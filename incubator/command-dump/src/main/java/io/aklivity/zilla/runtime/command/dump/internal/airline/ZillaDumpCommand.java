/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static java.lang.Integer.parseInt;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.LangUtil;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.dump.internal.airline.labels.LabelManager;
import io.aklivity.zilla.runtime.command.dump.internal.airline.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.dump.internal.airline.spy.RingBufferSpy;

@Command(name = "dump", description = "Dump stream content")
public final class ZillaDumpCommand extends ZillaCommand
{
    @Option(name = {"-v", "--verbose"},
        description = "Show verbose output")
    public boolean verbose;

    @Option(name = {"--version"})
    public boolean version = false;

    @Option(name = {"--continuous"})
    public boolean continuous = true;

    @Option(name = {"-t", "--bindingTypes"},
        description = "Dump specific bindings types only, e.g http")
    public List<String> bindingTypes = new ArrayList<>();

    @Option(name = {"-d", "--directory"},
        description = "Configuration directory")
    public URI directory;

    @Option(name = {"-o", "--output"},
        description = "PCAP file location to dump stream")
    public URI pcapLocation;

    @Option(name = {"-a", "--affinity"},
        description = "Affinity mask")
    public long affinity = 0xffff_ffff_ffff_ffffL;

    private final Logger out = System.out::printf;

    private long nextTimestamp = Long.MAX_VALUE;
    private RingBufferSpy.SpyPosition position;
    private static final long MAX_PARK_NS = MILLISECONDS.toNanos(100L);
    private static final long MIN_PARK_NS = MILLISECONDS.toNanos(1L);
    private static final int MAX_YIELDS = 30;
    private static final int MAX_SPINS = 20;
    private static final Pattern STREAMS_PATTERN = Pattern.compile("data(\\d+)");

    private Path directoryPath;
    private DumpHandlers dumpHandlers;

    private FileChannel channel;
    private RandomAccessFile writer;

    @Override
    public void run()
    {
        this.directoryPath = Path.of(directory);
        LabelManager labelManager = new LabelManager(directoryPath);
        final Predicate<String> hasExtensionType =
            bindingTypes == null || bindingTypes.isEmpty() ? t -> true : t -> bindingTypes.contains(t);
        try
        {

            this.writer = new RandomAccessFile(pcapLocation.getPath(), "rw");
            this.channel = writer.getChannel();
            this.dumpHandlers = new DumpHandlers(channel, 64 * 1024, labelManager, hasExtensionType);
        }
        catch (IOException e)
        {
            System.out.println("Failed to open dump file: " + e.getMessage());
        }


        if (version)
        {
            out.printf("version: %s\n", ZillaDumpCommand.class.getPackage().getSpecificationVersion());
        }
        Properties properties = new Properties();
        properties.setProperty(ENGINE_DIRECTORY.name(), directory.getPath());

        this.position = RingBufferSpy.SpyPosition.ZERO;

        runDumpCommand();
    }

    public void runDumpCommand()
    {
        try (Stream<Path> files = Files.walk(directoryPath, 3))
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

    private boolean isStreamsFile(
        Path path)
    {
        final int depth = path.getNameCount() - directoryPath.getNameCount();
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

    private LoggableStream newLoggable(Path path)
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
        return new LoggableStream(index, layout, this::nextTimestamp, dumpHandlers);
    }

    private void closeResources()
    {
        try
        {
            channel.close();
            writer.close();
        }
        catch (IOException e)
        {
            System.out.println("Could not close file. Reason: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
