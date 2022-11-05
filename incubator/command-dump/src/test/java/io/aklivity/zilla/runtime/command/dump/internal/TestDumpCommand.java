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
package io.aklivity.zilla.runtime.command.dump.internal;

import static org.pcap4j.core.PcapHandle.TimestampPrecision.NANO;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.Packet;

import com.google.common.io.Files;

import io.aklivity.zilla.runtime.command.dump.internal.airline.DumpStreamsCommand;
import io.aklivity.zilla.runtime.command.dump.internal.airline.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class TestDumpCommand
{

    @TempDir
    File tempDir;

    //@Test
    public void testDump() throws PcapNativeException, NotOpenException, IOException, TimeoutException
    {
        Properties properties = new Properties();
        properties.put("zilla.engine.directory", "src/test/resources/zilla_test_stream");
        final EngineConfiguration config = new EngineConfiguration(new Configuration(), properties);
        Predicate<String> hasFrameTypes = t -> true;
        boolean verbose = true;
        boolean continuous = false;
        long affinity = -1;
        RingBufferSpy.SpyPosition position = RingBufferSpy.SpyPosition.ZERO;
        Runnable command = new DumpStreamsCommand(config, hasFrameTypes, verbose, continuous, affinity, position,
            tempDir.getAbsolutePath());
        command.run();

        File[] files = tempDir.listFiles();
        Assertions.assertEquals(1, files.length);
        File file = new File("src/test/resources/expected_dump.pcap");
        // We need to copy the file, because Pcap library cannot read from resources dir. //TODO: why?
        Files.copy(file, new File(tempDir.getAbsolutePath() + "expected_dump.pcap"));

        PcapHandle expectedPhb = Pcaps.openOffline(tempDir.getAbsolutePath() + "expected_dump.pcap", NANO);
        PcapHandle actualPhb = Pcaps.openOffline(files[0].getPath(), NANO);
        Packet expected = expectedPhb.getNextPacketEx().getPacket();
        while (true)
        {
            try
            {
                Packet actual = actualPhb.getNextPacketEx().getPacket();
                Assertions.assertEquals(expected, actual);
                expected = expectedPhb.getNextPacketEx().getPacket();
            }
            catch (EOFException e)
            {
                break;
            }
        }
        expectedPhb.close();
        actualPhb.close();
    }
}
