/*
 * Copyright 2021-2022 Aklivity Inc.
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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;

public class UnregisterTask implements Callable<Void>
{
    private final Collection<DispatchAgent> dispatchers;
    private final NamespaceConfig rootNamespace;
    private final EngineExtContext context;
    private final List<EngineExtSpi> extensions;

    public UnregisterTask(
        Collection<DispatchAgent> dispatchers,
        NamespaceConfig rootNamespace,
        EngineExtContext context,
        List<EngineExtSpi> extensions)
    {
        this.dispatchers = dispatchers;
        this.rootNamespace = rootNamespace;
        this.context = context;
        this.extensions = extensions;
    }

    @Override
    public Void call() throws Exception
    {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (DispatchAgent dispatcher : dispatchers)
        {
            future = CompletableFuture.allOf(future, dispatcher.detach(rootNamespace));
        }
        future.join();
        extensions.forEach(e -> e.onUnregistered(context));
        return null;
    }
}
