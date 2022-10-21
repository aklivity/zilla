package io.aklivity.zilla.runtime.command.dump;

import io.aklivity.zilla.runtime.command.common.LoggableStream;
import io.aklivity.zilla.runtime.command.common.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.common.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
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

public class DumpStreamsCommand implements Runnable
{
    private static final Pattern STREAMS_PATTERN = Pattern.compile("data(\\d+)");

    private static final long MAX_PARK_NS = MILLISECONDS.toNanos(100L);
    private static final long MIN_PARK_NS = MILLISECONDS.toNanos(1L);
    private static final int MAX_YIELDS = 30;
    private static final int MAX_SPINS = 20;

    private final Path directory;
    private final Predicate<String> hasFrameType;
    private final boolean verbose;
    private final boolean continuous;
    private final long affinity;
    private final RingBufferSpy.SpyPosition position;

    private long nextTimestamp = Long.MAX_VALUE;

    private DumpHandlers dumpHandlers;


    //TODO refactor common parts with LogStreamsCommand
    DumpStreamsCommand(
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

        this.dumpHandlers = new DumpHandlers();

        return new LoggableStream(index, layout, hasFrameType, this::nextTimestamp, dumpHandlers);
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
            dumpHandlers.writePacketsToPcap();
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
