/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static org.agrona.LangUtil.rethrowUnchecked;
import static org.agrona.concurrent.AgentRunner.startOnThread;

import java.nio.channels.SelectableChannel;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.agrona.ErrorHandler;
import org.agrona.collections.ObjectHashSet;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.AgentTerminationException;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineController;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.binding.BindingController;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.internal.poller.Poller;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;

public class EngineBoss implements EngineController, Agent
{
    private static final String AGENT_NAME = "engine/boss";

    private final AgentRunner runner;

    private final Poller poller;
    private final Deque<Runnable> taskQueue;
    private final Map<String, BindingController> controllersByType;

    private volatile Thread thread;
    private Set<NamespaceConfig> namespaces;

    public EngineBoss(
        EngineConfiguration config,
        ErrorHandler errorHandler,
        Collection<Binding> bindings)
    {
        this.poller = new Poller();
        this.taskQueue = new ConcurrentLinkedDeque<>();
        this.namespaces = new ObjectHashSet<>();

        this.controllersByType = bindings.stream()
        .flatMap(b ->
        {
            BindingController controller = b.supply(this);
            return controller != null ? Stream.of(Map.entry(b.name(), controller)) : Stream.empty();
        })
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        IdleStrategy idleStrategy = new BackoffIdleStrategy(
            config.maxSpins(),
            config.maxYields(),
            config.minParkNanos(),
            config.maxParkNanos());

        this.runner = new AgentRunner(idleStrategy, errorHandler, null, this);
    }

    @Override
    public int doWork() throws Exception
    {
        int workDone = 0;

        try
        {
            workDone += poller.doWork();

            if (!taskQueue.isEmpty())
            {
                for (Runnable task = taskQueue.poll(); task != null; task = taskQueue.poll())
                {
                    task.run();
                    workDone++;
                }
            }
        }
        catch (Throwable ex)
        {
            throw new AgentTerminationException(ex);
        }

        return workDone;
    }

    public void doStart()
    {
        thread = startOnThread(runner, Thread::new);
    }

    public void doClose()
    {
        try
        {
            namespaces.forEach(this::detachNamespace);
            controllersByType.clear();

            Consumer<Thread> timeout = t -> rethrowUnchecked(new IllegalStateException("close timeout"));

            runner.close((int) SECONDS.toMillis(5L), timeout);

            poller.close();
        }
        finally
        {
            thread = null;
        }
    }

    public void attachNow(
        NamespaceConfig namespace)
    {
        attach(namespace).join();
    }

    public CompletableFuture<Void> attach(
        NamespaceConfig namespace)
    {
        NamespaceTask attachedTask = new NamespaceTask(namespace, this::attachNamespace);
        taskQueue.offer(attachedTask);

        return attachedTask.future();
    }

    public void detachNow(
        NamespaceConfig namespace)
    {
        detach(namespace).join();
    }

    public CompletableFuture<Void> detach(
        NamespaceConfig namespace)
    {
        NamespaceTask detachedTask = new NamespaceTask(namespace, this::detachNamespace);
        taskQueue.offer(detachedTask);

        return detachedTask.future();
    }

    @Override
    public String roleName()
    {
        return AGENT_NAME;
    }

    public PollerKey supplyPollerKey(
        SelectableChannel channel)
    {
        return poller.register(channel);
    }

    private void attachNamespace(
        NamespaceConfig namespace)
    {
        namespaces.add(namespace);

        for (BindingConfig binding : namespace.bindings)
        {
            BindingController controller = controllersByType.get(binding.type);
            if (controller != null && binding.kind == KindConfig.SERVER)
            {
                controller.attach(binding);
            }
        }
    }

    private void detachNamespace(
        NamespaceConfig namespace)
    {
        namespaces.remove(namespace);

        for (BindingConfig binding : namespace.bindings)
        {
            BindingController controller = controllersByType.get(binding.type);
            if (controller != null)
            {
                controller.detach(binding);
            }
        }
    }
}
