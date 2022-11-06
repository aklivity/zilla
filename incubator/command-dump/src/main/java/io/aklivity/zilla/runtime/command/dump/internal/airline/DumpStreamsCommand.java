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

import static java.lang.Integer.parseInt;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;

import io.aklivity.zilla.runtime.command.dump.internal.airline.labels.LabelManager;
import io.aklivity.zilla.runtime.command.dump.internal.airline.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.dump.internal.airline.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class DumpStreamsCommand extends StreamsCommand implements Runnable
{
    private DumpHandlers dumpHandlers;

    private FileChannel channel;
    private RandomAccessFile writer;

    public DumpStreamsCommand(
        EngineConfiguration config,
        boolean verbose,
        int count,
        long affinity,
        RingBufferSpy.SpyPosition position,
        String pcapLocation)
    {
        super(config, verbose, count, affinity, position);
        try
        {
            String timeStamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(System.currentTimeMillis());
            String fileName = "zilla_dump" + timeStamp + ".pcap";
            Path path = Paths.get(pcapLocation, fileName);
            this.writer = new RandomAccessFile(path.toString(), "rw");
            this.channel = writer.getChannel();
            this.dumpHandlers = new DumpHandlers(channel, 64 * 1024, new LabelManager(config.directory()), t -> true);
        }
        catch (FileNotFoundException e)
        {
            System.out.println("Failed to open dump file: " + e.getMessage());
        }
    }

    @Override
    protected LoggableStream newLoggable(Path path)
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

    @Override
    protected void closeResources()
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
