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

import java.util.function.IntConsumer;

import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;

public class ExporterRegistry
{
    private final int id;
    private final ExporterHandler handler;
    private final IntConsumer attached;
    private final IntConsumer detached;

    public ExporterRegistry(
        int id,
        ExporterHandler handler,
        IntConsumer attached,
        IntConsumer detached)
    {
        this.id = id;
        this.handler = handler;
        this.attached = attached;
        this.detached = detached;
    }

    public void attach()
    {
        attached.accept(id);
    }

    public void detach()
    {
        detached.accept(id);
    }

    public ExporterHandler handler()
    {
        return handler;
    }
}
