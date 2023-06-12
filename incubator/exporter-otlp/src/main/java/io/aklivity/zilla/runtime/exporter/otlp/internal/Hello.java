/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
/*package io.aklivity.zilla.runtime.exporter.otlp.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;

import java.net.InetAddress;
import java.net.URL;
import java.nio.channels.SelectableChannel;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;
import io.aklivity.zilla.runtime.metrics.grpc.internal.GrpcMetricGroup;
import io.aklivity.zilla.runtime.metrics.http.internal.HttpMetricGroup;
import io.aklivity.zilla.runtime.metrics.stream.internal.StreamMetricGroup;

public class Hello
{
    private final EngineConfiguration config;
    private final Map<String, MetricGroup> metricGroupsByName;

    public Hello()
    {
        Properties props = new Properties();
        props.put(ENGINE_DIRECTORY.name(), "/Users/attila/az/zilla-run/.zilla/engine");
        config = new EngineConfiguration(props);
        metricGroupsByName = new Object2ObjectHashMap<>();
        metricGroupsByName.put("stream", new StreamMetricGroup(config));
        metricGroupsByName.put("http", new HttpMetricGroup(config));
        metricGroupsByName.put("grpc", new GrpcMetricGroup(config));
    }

    public void hello()
    {
        OltpExporterHandler handler = new OltpExporterHandler(config, createFakeEngineContext(), null, this::fakeBindingKind);
        handler.start();
        wait(Duration.ofSeconds(15));
        handler.stop();
        wait(Duration.ofSeconds(30));
    }

    private static void wait(
        Duration duration)
    {
        try
        {
            Thread.sleep(duration.toMillis());
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    private KindConfig fakeBindingKind(
        String bindingName)
    {
        switch (bindingName)
        {
        case "http_server0":
            return KindConfig.SERVER;
        case "http_client0":
            return KindConfig.CLIENT;
        }
        return KindConfig.PROXY;
    }

    private EngineContext createFakeEngineContext()
    {
        return new EngineContext()
        {
            @Override
            public int index()
            {
                return 0;
            }

            @Override
            public Signaler signaler()
            {
                return null;
            }

            @Override
            public int supplyTypeId(String name)
            {
                return 0;
            }

            @Override
            public long supplyInitialId(long bindingId)
            {
                return 0;
            }

            @Override
            public long supplyReplyId(long initialId)
            {
                return 0;
            }

            @Override
            public long supplyPromiseId(long initialId)
            {
                return 0;
            }

            @Override
            public long supplyAuthorizedId()
            {
                return 0;
            }

            @Override
            public long supplyBudgetId()
            {
                return 0;
            }

            @Override
            public long supplyTraceId()
            {
                return 0;
            }

            @Override
            public MessageConsumer supplySender(long streamId)
            {
                return null;
            }

            @Override
            public MessageConsumer supplyReceiver(long streamId)
            {
                return null;
            }

            @Override
            public void detachSender(long replyId)
            {

            }

            @Override
            public void detachStreams(long bindingId)
            {

            }

            @Override
            public BudgetCreditor creditor()
            {
                return null;
            }

            @Override
            public BudgetDebitor supplyDebitor(long budgetId)
            {
                return null;
            }

            @Override
            public MutableDirectBuffer writeBuffer()
            {
                return null;
            }

            @Override
            public BufferPool bufferPool()
            {
                return null;
            }

            @Override
            public LongSupplier supplyCounter(long bindingId, long metricId)
            {
                return null;
            }

            @Override
            public MessageConsumer droppedFrameHandler()
            {
                return null;
            }

            @Override
            public int supplyClientIndex(long streamId)
            {
                return 0;
            }

            @Override
            public InetAddress[] resolveHost(String host)
            {
                return new InetAddress[0];
            }

            @Override
            public PollerKey supplyPollerKey(SelectableChannel channel)
            {
                return null;
            }

            @Override
            public long supplyBindingId(NamespaceConfig namespace, BindingConfig binding)
            {
                return 0;
            }

            @Override
            public String supplyNamespace(long bindingId)
            {
                return null;
            }

            @Override
            public String supplyLocalName(long bindingId)
            {
                return null;
            }

            @Override
            public BindingHandler streamFactory()
            {
                return null;
            }

            @Override
            public GuardHandler supplyGuard(long guardId)
            {
                return null;
            }

            @Override
            public VaultHandler supplyVault(long vaultId)
            {
                return null;
            }

            @Override
            public URL resolvePath(String path)
            {
                return null;
            }

            @Override
            public Metric resolveMetric(String metricName)
            {
                String metricGroupName = metricName.split("\\.")[0];
                return metricGroupsByName.get(metricGroupName).supply(metricName);
            }

            @Override
            public void onExporterAttached(long exporterId)
            {

            }

            @Override
            public void onExporterDetached(long exporterId)
            {

            }
        };
    }

    public static void main(String[] args)
    {
        Hello hello = new Hello();
        hello.hello();
    }
}
*/
