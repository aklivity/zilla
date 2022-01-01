/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.context;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.config.Namespace;

public final class NamespaceTask implements Runnable
{
    private final CompletableFuture<Void> future;
    private final Namespace namespace;
    private final Consumer<Namespace> configurable;

    NamespaceTask(
        Namespace namespace,
        Consumer<Namespace> configurable)
    {
        this.future = new CompletableFuture<Void>();
        this.namespace = namespace;
        this.configurable = configurable;
    }

    public CompletableFuture<Void> future()
    {
        return future;
    }

    @Override
    public void run()
    {
        try
        {
            configurable.accept(namespace);
            future.complete(null);
        }
        catch (Throwable ex)
        {
            future.completeExceptionally(ex);
        }
    }
}
