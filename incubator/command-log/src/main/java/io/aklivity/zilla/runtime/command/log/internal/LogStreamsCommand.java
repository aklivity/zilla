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
package io.aklivity.zilla.runtime.command.log.internal;

import static java.lang.Integer.parseInt;

import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.command.dump.internal.airline.Handlers;
import io.aklivity.zilla.runtime.command.dump.internal.airline.LoggableStream;
import io.aklivity.zilla.runtime.command.dump.internal.airline.Logger;
import io.aklivity.zilla.runtime.command.dump.internal.airline.StreamsCommand;
import io.aklivity.zilla.runtime.command.dump.internal.airline.labels.LabelManager;
import io.aklivity.zilla.runtime.command.dump.internal.airline.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.dump.internal.airline.spy.RingBufferSpy.SpyPosition;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public final class LogStreamsCommand extends StreamsCommand implements Runnable
{
    private static final Pattern STREAMS_PATTERN = Pattern.compile("data(\\d+)");
    private final Predicate<String> hasExtensionType;
    private final LabelManager labels;
    private final Logger out;


    LogStreamsCommand(
        EngineConfiguration config,
        Logger out,
        Predicate<String> hasFrameType,
        Predicate<String> hasExtensionType,
        boolean verbose,
        boolean continuous,
        long affinity,
        SpyPosition position)
    {
        super(config, hasFrameType, verbose, continuous, affinity, position);
        this.labels = new LabelManager(config.directory());
        this.out = out;
        this.hasExtensionType = hasExtensionType;
    }
    @Override
    protected LoggableStream newLoggable(
        Path path)
    {
        final String filename = path.getFileName().toString();
        final Matcher matcher = STREAMS_PATTERN.matcher(filename);
        matcher.matches();
        final int index = parseInt(matcher.group(1));

        StreamsLayout layout = new StreamsLayout.Builder()
                .path(path)
                .readonly(true)
                .spyAt(position)
                .build();

        Handlers logHandlers = new LogHandlers(index, labels, out, hasExtensionType);

        return new LoggableStream(index, layout, hasFrameType, this::nextTimestamp, logHandlers);
    }

    @Override
    protected void closeResources()
    {
    }
}
