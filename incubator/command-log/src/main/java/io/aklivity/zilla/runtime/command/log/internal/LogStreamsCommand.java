/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.LangUtil;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import io.aklivity.zilla.runtime.command.log.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.command.log.internal.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.log.internal.spy.RingBufferSpy.SpyPosition;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public final class LogStreamsCommand implements Runnable
{
    private static final Pattern STREAMS_PATTERN = Pattern.compile("data(\\d+)");

    private static final long MAX_PARK_NS = MILLISECONDS.toNanos(100L);
    private static final long MIN_PARK_NS = MILLISECONDS.toNanos(1L);
    private static final int MAX_YIELDS = 30;
    private static final int MAX_SPINS = 20;

    private final Path directory;
    private final Predicate<String> hasFrameType;
    private final Predicate<String> hasExtensionType;
    private final LabelManager labels;
    private final boolean verbose;
    private final boolean continuous;
    private final boolean withPayload;
    private final long affinity;
    private final SpyPosition position;
    private final Logger out;

    private long nextTimestamp = Long.MAX_VALUE;

    LogStreamsCommand(
        EngineConfiguration config,
        Logger out,
        Predicate<String> hasFrameType,
        Predicate<String> hasExtensionType,
        boolean verbose,
        boolean continuous,
        boolean withPayload,
        long affinity,
        SpyPosition position)
    {
        this.directory = config.directory();
        this.labels = new LabelManager(directory);
        this.verbose = verbose;
        this.continuous = continuous;
        this.withPayload = withPayload;
        this.affinity = affinity;
        this.position = position;
        this.out = out;
        this.hasFrameType = hasFrameType;
        this.hasExtensionType = hasExtensionType;
    }

    private boolean isStreamsFile(
        Path path)
    {
        final int depth = path.getNameCount() - directory.getNameCount();
        if (depth != 1 || !Files.isRegularFile(path))
        {
            return false;
        }

        final Matcher matcher = STREAMS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString());
        return matcher.matches() && ((1L << parseInt(matcher.group(1))) & affinity) != 0L;
    }

    private LoggableStream newLoggable(
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

        return new LoggableStream(index, labels, layout, out, hasFrameType, hasExtensionType, withPayload,
            this::nextTimestamp);
    }

    private void onDiscovered(
        Path path)
    {
        if (verbose)
        {
            out.printf("Discovered: %s\n", path);
        }
    }

    @Override
    public void run()
    {
        try (Stream<Path> files = Files.walk(directory, 3))
        {
            LoggableStream[] loggables = files.filter(this::isStreamsFile)
                 .peek(this::onDiscovered)
                 .map(this::newLoggable)
                 .toArray(LoggableStream[]::new);

            final IdleStrategy idleStrategy = new BackoffIdleStrategy(MAX_SPINS, MAX_YIELDS, MIN_PARK_NS, MAX_PARK_NS);

            final int exitWorkCount = continuous ? -1 : 0;

            int workCount;
            do
            {
                workCount = 0;

                for (int i = 0; i < loggables.length; i++)
                {
                    workCount += loggables[i].process();
                }

                idleStrategy.idle(workCount);

            } while (workCount != exitWorkCount);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    private boolean nextTimestamp(
        final long timestamp)
    {
        if (timestamp != nextTimestamp)
        {
            nextTimestamp = Math.min(timestamp, nextTimestamp);
            return false;
        }
        else
        {
            nextTimestamp = Long.MAX_VALUE;
            return true;
        }
    }
}
