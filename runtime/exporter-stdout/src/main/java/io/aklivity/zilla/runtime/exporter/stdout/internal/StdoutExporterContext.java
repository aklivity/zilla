/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.stdout.internal;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.event.EventFormatter;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.exporter.stdout.internal.config.StdoutExporterConfig;

public class StdoutExporterContext implements ExporterContext
{
    private final StdoutConfiguration config;
    private final EngineContext context;
    private final ConcurrentSkipListSet<StdoutExporterHandler> handlers;

    public StdoutExporterContext(
        StdoutConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.handlers = new ConcurrentSkipListSet<>(Comparator.comparingInt(System::identityHashCode));
    }

    @Override
    public ExporterHandler attach(
        ExporterConfig exporter,
        List<AttributeConfig> attributes,
        Collector collector,
        LongFunction<KindConfig> resolveKind)
    {
        StdoutExporterConfig stdoutExporter = new StdoutExporterConfig(exporter);
        return new StdoutExporterHandler(config, context, stdoutExporter, handlers);
    }

    @Override
    public void detach(
        long exporterId)
    {
    }

    public String supplyQName(
        long namespacedId)
    {
        return context.supplyQName(namespacedId);
    }

    public String supplyEventName(
            int eventId)
    {
        return context.supplyEventName(eventId);
    }

    public EventFormatter supplyEventFormatter()
    {
        return context.supplyEventFormatter();
    }

    public MessageReader supplyEventReader()
    {
        return context.supplyEventReader();
    }
}
