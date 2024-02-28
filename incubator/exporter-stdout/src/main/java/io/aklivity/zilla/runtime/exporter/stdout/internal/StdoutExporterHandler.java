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
package io.aklivity.zilla.runtime.exporter.stdout.internal;

import java.io.PrintStream;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.exporter.stdout.internal.config.StdoutExporterConfig;
import io.aklivity.zilla.runtime.exporter.stdout.internal.stream.StdoutEventsStream;

public class StdoutExporterHandler implements ExporterHandler
{
    private final StdoutExporterContext context;
    private final PrintStream out;

    private StdoutEventsStream events;

    public StdoutExporterHandler(
        StdoutConfiguration config,
        EngineContext context,
        StdoutExporterConfig exporter)
    {
        this.context = new StdoutExporterContext(config, context);
        this.out = config.output();
    }

    @Override
    public void start()
    {
        events = new StdoutEventsStream(context, out);
    }

    @Override
    public int export()
    {
        return events.process();
    }

    @Override
    public void stop()
    {
        this.events = null;
    }
}
