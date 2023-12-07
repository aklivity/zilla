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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import io.aklivity.zilla.runtime.command.dump.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.layouts.StreamsLayout;
import io.aklivity.zilla.specs.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.specs.engine.internal.types.stream.WindowFW;

public class ZillaDumpCommandTest
{
    private static String baseDir = "src/test/resources/io/aklivity/zilla/runtime/command/dump/internal";

    @TempDir
    private File tempDir;

    private ZillaDumpCommand command;

    @BeforeAll
    public static void generateStreamsBuffer()
    {
        StreamsLayout streamsLayout = new StreamsLayout.Builder()
            .path(Paths.get(baseDir, "engine").resolve("data0"))
            .streamsCapacity(8 * 1024)
            .readonly(false)
            .build();

        RingBuffer streams = streamsLayout.streamsBuffer();

        MutableDirectBuffer frameBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        BeginFW begin = new BeginFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(0)
            .streamId(0)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .affinity(0)
            .build();

        streams.write(BeginFW.TYPE_ID, begin.buffer(), 0, begin.sizeof());

        BeginFW begin2 = new BeginFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(1)
            .streamId(1)
            .sequence(1)
            .acknowledge(0)
            .maximum(0)
            .affinity(0)
            .build();

        streams.write(BeginFW.TYPE_ID, begin2.buffer(), 0, begin2.sizeof());

        BeginFW filteredBegin = new BeginFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(4294967298L)
            .streamId(4)
            .sequence(4)
            .acknowledge(0)
            .maximum(0)
            .affinity(0)
            .build();

        streams.write(BeginFW.TYPE_ID, filteredBegin.buffer(), 0, filteredBegin.sizeof());

        WindowFW window1 = new WindowFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(0)
            .streamId(0)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .budgetId(0)
            .padding(0)
            .build();

        streams.write(WindowFW.TYPE_ID, window1.buffer(), 0, window1.sizeof());

        WindowFW window2 = new WindowFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(1)
            .streamId(1)
            .sequence(1)
            .acknowledge(0)
            .maximum(0)
            .budgetId(0)
            .padding(0)
            .build();

        streams.write(WindowFW.TYPE_ID, window2.buffer(), 0, window2.sizeof());

        String payload = "POST / HTTP/1.1\n" +
            "Host: localhost:8080\n" +
            "User-Agent: curl/7.85.0\n" +
            "Accept: */*\n" +
            "Content-Type: text/plain\n" +
            "Content-Length: 12\n" +
            "\n" +
            "Hello, world";

        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        DataFW data1 = new DataFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(0)
            .streamId(0)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .budgetId(0)
            .reserved(0)
            .payload(new OctetsFW().wrap(new UnsafeBuffer(payloadBytes), 0, payloadBytes.length))
            .build();

        streams.write(DataFW.TYPE_ID, data1.buffer(), 0, data1.sizeof());

        DataFW data2 = new DataFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(1)
            .streamId(1)
            .sequence(1)
            .acknowledge(0)
            .maximum(0)
            .budgetId(0)
            .reserved(0)
            .payload(new OctetsFW().wrap(new UnsafeBuffer(payloadBytes), 0, payloadBytes.length))
            .build();

        streams.write(DataFW.TYPE_ID, data2.buffer(), 0, data2.sizeof());


        EndFW end1 = new EndFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(0)
            .streamId(0)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .build();

        streams.write(EndFW.TYPE_ID, end1.buffer(), 0, end1.sizeof());

        EndFW end2 = new EndFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(1)
            .streamId(1)
            .sequence(1)
            .acknowledge(0)
            .maximum(0)
            .build();

        streams.write(EndFW.TYPE_ID, end2.buffer(), 0, end2.sizeof());

    }

    @BeforeEach
    public void init()
    {
        command = new ZillaDumpCommand();
        command.directory = Paths.get(baseDir, "engine");
        command.verbose = true;
        command.continuous = false;
        command.output = Paths.get(tempDir.getPath(), "test.pcap");
    }

    //@Test
    public void shouldDumpWithoutFilter() throws IOException
    {
        command.run();

        File[] files = tempDir.listFiles();
        assertEquals(1, files.length);

        File expectedDump = new File(baseDir + "/expected_dump_without_filter.pcap");
        byte[] expected = Files.readAllBytes(expectedDump.toPath());
        byte[] actual = Files.readAllBytes(files[0].toPath());
        assertArrayEquals(expected, actual);
    }

    //@Test
    public void shouldDumpWithKafkaFilter() throws IOException
    {
        command.bindings = singletonList("test.kafka0");
        command.run();

        File[] files = tempDir.listFiles();
        assertEquals(1, files.length);

        File expectedDump = new File(baseDir + "/expected_dump_with_kafka_filter.pcap");
        byte[] expected = Files.readAllBytes(expectedDump.toPath());
        byte[] actual = Files.readAllBytes(files[0].toPath());
        assertArrayEquals(expected, actual);
    }
}
