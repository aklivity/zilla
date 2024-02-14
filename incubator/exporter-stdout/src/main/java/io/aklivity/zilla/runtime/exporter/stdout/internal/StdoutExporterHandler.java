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
import java.util.Arrays;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.util.function.EventReader;
import io.aklivity.zilla.runtime.exporter.stdout.internal.config.StdoutExporterConfig;
import io.aklivity.zilla.runtime.exporter.stdout.internal.stream.StdoutEventsStream;

public class StdoutExporterHandler implements ExporterHandler
{
    private final EngineContext context;
    private final PrintStream out;

    private StdoutEventsStream[] stdoutEventStreams;

    public StdoutExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        StdoutExporterConfig exporter,
        PrintStream out)
    {
        this.context = context;
        this.out = out;
    }

    @Override
    public void start()
    {
        this.stdoutEventStreams = Arrays.stream(context.supplyEventReaders().get())
                .map(this::newStdoutEventsStream)
                .toArray(StdoutEventsStream[]::new);
    }

    @Override
    public int export()
    {
        int workCount = 0;
        if (stdoutEventStreams != null)
        {
            for (int i = 0; i < stdoutEventStreams.length; i++)
            {
                workCount += stdoutEventStreams[i].process();
            }
        }
        return workCount;
    }

    @Override
    public void stop()
    {
        this.stdoutEventStreams = null;
    }

    private StdoutEventsStream newStdoutEventsStream(
        EventReader eventReader)
    {
        return new StdoutEventsStream(eventReader, context::supplyNamespace, context::supplyLocalName,
            context::lookupLabelId, out);
    }
}
