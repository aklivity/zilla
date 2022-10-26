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
package io.aklivity.zilla.runtime.command.dump;

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

import io.aklivity.zilla.runtime.command.dump.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public abstract class StreamsCommand implements Runnable
{
    private long nextTimestamp = Long.MAX_VALUE;
    private final Path directory;
    private final boolean verbose;
    private final boolean continuous;
    private final long affinity;
    protected final RingBufferSpy.SpyPosition position;
    protected final Predicate<String> hasFrameType;

    private static final long MAX_PARK_NS = MILLISECONDS.toNanos(100L);
    private static final long MIN_PARK_NS = MILLISECONDS.toNanos(1L);
    private static final int MAX_YIELDS = 30;
    private static final int MAX_SPINS = 20;
    protected static final Pattern STREAMS_PATTERN = Pattern.compile("data(\\d+)");


    public StreamsCommand(
        EngineConfiguration config,
        Predicate<String> hasFrameType,
        boolean verbose,
        boolean continuous,
        long affinity,
        RingBufferSpy.SpyPosition position)
    {
        this.directory = config.directory();
        this.verbose = verbose;
        this.continuous = continuous;
        this.affinity = affinity;
        this.position = position;
        this.hasFrameType = hasFrameType;
    }

    protected abstract LoggableStream newLoggable(Path path);

    protected abstract void closeResources();

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

    private void onDiscovered(
        Path path)
    {
        if (verbose)
        {
            System.out.printf("Discovered: %s\n", path);
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
            closeResources();
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    protected boolean nextTimestamp(
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
