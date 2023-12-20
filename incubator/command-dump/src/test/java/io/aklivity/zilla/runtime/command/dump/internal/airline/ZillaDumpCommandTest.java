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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.aklivity.zilla.runtime.command.dump.internal.types.String8FW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.engine.internal.layouts.StreamsLayout;
import io.aklivity.zilla.specs.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.specs.engine.internal.types.stream.WindowFW;

public class ZillaDumpCommandTest
{
    private static final Path ENGINE_PATH =
        Path.of("src/test/resources/io/aklivity/zilla/runtime/command/dump/internal/airline/engine");

    @TempDir
    private File tempDir;

    private ZillaDumpCommand command;

    @BeforeAll
    @SuppressWarnings("checkstyle:methodlength")
    public static void generateStreamsBuffer()
    {
        StreamsLayout streamsLayout = new StreamsLayout.Builder()
            .path(ENGINE_PATH.resolve("data0"))
            .streamsCapacity(8 * 1024)
            .readonly(false)
            .build();
        RingBuffer streams = streamsLayout.streamsBuffer();
        MutableDirectBuffer frameBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        SignalFW signal1 = new SignalFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(0)
            .streamId(0)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000001L)
            .traceId(0x0000000000000000L)
            .cancelId(0x0000000000007701L)
            .signalId(0x00007702)
            .contextId(0x00007703)
            .build();
        streams.write(SignalFW.TYPE_ID, signal1.buffer(), 0, signal1.sizeof());

        DirectBuffer helloBuf = new String8FW("Hello World!").value();
        SignalFW signal2 = new SignalFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(0)
            .streamId(0)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000002L)
            .traceId(0x0000000000000000L)
            .cancelId(0x0000000000007801L)
            .signalId(0x00007802)
            .contextId(0x00007803)
            .payload(helloBuf, 0, helloBuf.capacity())
            .build();
        streams.write(SignalFW.TYPE_ID, signal2.buffer(), 0, signal2.sizeof());

        BeginFW begin1 = new BeginFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000005L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000003L)
            .traceId(0x0000000000000003L)
            .affinity(0x0000000000000005L)
            .build();
        streams.write(BeginFW.TYPE_ID, begin1.buffer(), 0, begin1.sizeof());

        WindowFW window1 = new WindowFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000005L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(65536)
            .timestamp(0x0000000000000004L)
            .traceId(0x0000000000000003L)
            .budgetId(0)
            .padding(0)
            .build();
        streams.write(WindowFW.TYPE_ID, window1.buffer(), 0, window1.sizeof());

        BeginFW begin2 = new BeginFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(1)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000005L)
            .traceId(0x0000000000000003L)
            .affinity(0)
            .build();
        streams.write(BeginFW.TYPE_ID, begin2.buffer(), 0, begin2.sizeof());

        WindowFW window2 = new WindowFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(65536)
            .timestamp(0x0000000000000006L)
            .traceId(0x0000000000000003L)
            .budgetId(0)
            .padding(0)
            .build();
        streams.write(WindowFW.TYPE_ID, window2.buffer(), 0, window2.sizeof());

        BeginFW filteredBegin = new BeginFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000cL) // north_tls_server
            .streamId(0x0000000000000077L) // INI
            .sequence(71)
            .acknowledge(72)
            .maximum(73)
            .timestamp(0x0000000000000007L)
            .traceId(0x0000000000004202L)
            .authorization(0x0000000000004203L)
            .affinity(0x0000000000004204L)
            .build();
        streams.write(BeginFW.TYPE_ID, filteredBegin.buffer(), 0, filteredBegin.sizeof());

        String request1 =
            "POST / HTTP/1.1\n" +
            "Host: localhost:8080\n" +
            "User-Agent: curl/7.85.0\n" +
            "Accept: */*\n" +
            "Content-Type: text/plain\n" +
            "Content-Length: 12\n" +
            "\n" +
            "Hello, world";
        DirectBuffer request1buf = new String8FW(request1).value();
        DataFW data1 = new DataFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000005L) // INI
            .sequence(123)
            .acknowledge(456)
            .maximum(777)
            .timestamp(0x0000000000000008L)
            .traceId(0x0000000000000003L)
            .budgetId(0x0000000000004205L)
            .reserved(0x00004206)
            .payload(request1buf, 0, request1buf.capacity())
            .build();
        streams.write(DataFW.TYPE_ID, data1.buffer(), 0, data1.sizeof());

        String response1 =
            "HTTP/1.1 200 OK\n" +
            "Content-Type: text/plain\n" +
            "Content-Length: 13\n" +
            "\n" +
            "Hello, World!";
        DirectBuffer response1buf = new String8FW(response1).value();
        DataFW data2 = new DataFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(123)
            .acknowledge(456)
            .maximum(777)
            .timestamp(0x0000000000000009L)
            .traceId(0x0000000000000003L)
            .budgetId(0x0000000000004205L)
            .reserved(0x00004206)
            .payload(response1buf, 0, response1buf.capacity())
            .build();
        streams.write(DataFW.TYPE_ID, data2.buffer(), 0, data2.sizeof());

        ChallengeFW challenge1 = new ChallengeFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(201)
            .acknowledge(202)
            .maximum(22222)
            .timestamp(0x000000000000000aL)
            .traceId(0x0000000000000003L)
            .authorization(0x0000000000007742L)
            .build();
        streams.write(ChallengeFW.TYPE_ID, challenge1.buffer(), 0, challenge1.sizeof());

        FlushFW flush1 = new FlushFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(301)
            .acknowledge(302)
            .maximum(3344)
            .timestamp(0x000000000000000bL)
            .traceId(0x0000000000000003L)
            .budgetId(0x0000000000003300L)
            .reserved(0x00003303)
            .build();
        streams.write(FlushFW.TYPE_ID, flush1.buffer(), 0, flush1.sizeof());

        AbortFW abort1 = new AbortFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000005L) // INI
            .sequence(401)
            .acknowledge(402)
            .maximum(4477)
            .timestamp(0x000000000000000cL)
            .traceId(0x0000000000000003L)
            .build();
        streams.write(AbortFW.TYPE_ID, abort1.buffer(), 0, abort1.sizeof());

        ResetFW reset1 = new ResetFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000006L) // REP
            .sequence(501)
            .acknowledge(502)
            .maximum(5577)
            .timestamp(0x000000000000000dL)
            .traceId(0x0000000000000003L)
            .build();
        streams.write(ResetFW.TYPE_ID, reset1.buffer(), 0, reset1.sizeof());

        EndFW end1 = new EndFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000005L) // INI
            .sequence(701)
            .acknowledge(702)
            .maximum(7777)
            .timestamp(0x000000000000000eL)
            .traceId(0x0000000000000003L)
            .build();
        streams.write(EndFW.TYPE_ID, end1.buffer(), 0, end1.sizeof());

        EndFW end2 = new EndFW.Builder().wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(703)
            .acknowledge(704)
            .maximum(4444)
            .timestamp(0x000000000000000fL)
            .traceId(0x0000000000000003L)
            .build();
        streams.write(EndFW.TYPE_ID, end2.buffer(), 0, end2.sizeof());
    }

    @BeforeEach
    public void init()
    {
        command = new ZillaDumpCommand();
        command.verbose = true;
        command.continuous = false;
        command.properties = List.of(String.format("zilla.engine.directory=%s", ENGINE_PATH));
        command.output = Paths.get(tempDir.getPath(), "actual.pcap");
    }

    @Test
    public void shouldWritePcap() throws IOException
    {
        // GIVEN
        byte[] expected = getResourceAsBytes("expected_dump.pcap");

        // WHEN
        command.run();

        // THEN
        File[] files = tempDir.listFiles();
        assert files != null;
        byte[] actual = Files.readAllBytes(files[0].toPath());
        assertThat(files.length, equalTo(1));
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void shouldWriteFilteredPcap() throws IOException
    {
        // GIVEN
        byte[] expected = getResourceAsBytes("expected_filtered_dump.pcap");

        // WHEN
        command.bindings = singletonList("example.north_tls_server");
        command.run();

        // THEN
        File[] files = tempDir.listFiles();
        assert files != null;
        byte[] actual = Files.readAllBytes(files[0].toPath());
        assertThat(files.length, equalTo(1));
        assertThat(actual, equalTo(expected));
    }

    private static byte[] getResourceAsBytes(
        String resourceName) throws IOException
    {
        byte[] bytes;
        try (InputStream is = ZillaDumpCommandTest.class.getResourceAsStream(resourceName))
        {
            assert is != null;
            bytes = is.readAllBytes();
        }
        return bytes;
    }
}
