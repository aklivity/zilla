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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import static java.lang.Integer.parseInt;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapDumper;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.namednumber.DataLinkType;

import io.aklivity.zilla.runtime.command.dump.internal.airline.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.dump.internal.airline.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class DumpStreamsCommand extends StreamsCommand implements Runnable
{
    private DumpHandlers dumpHandlers;


    private PcapHandle phb;
    private PcapDumper dumper;

    public DumpStreamsCommand(
        EngineConfiguration config,
        Predicate<String> hasFrameType,
        boolean verbose,
        boolean continuous,
        long affinity,
        RingBufferSpy.SpyPosition position,
        String pcapLocation)
    {
        super(config, hasFrameType, verbose, continuous, affinity, position);
        try
        {
            phb = Pcaps.openDead(DataLinkType.EN10MB, 65536);
            String timeStamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(System.currentTimeMillis());
            String fileName = "zilla_dump" + timeStamp + ".pcap";
            dumper = phb.dumpOpen(Paths.get(pcapLocation, fileName).toString());
        }
        catch (PcapNativeException | NotOpenException e)
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

        this.dumpHandlers = new DumpHandlers(dumper);

        return new LoggableStream(index, layout, hasFrameType, this::nextTimestamp, dumpHandlers);
    }

    @Override
    protected void closeResources()
    {
        dumper.close();
        phb.close();
    }
}
