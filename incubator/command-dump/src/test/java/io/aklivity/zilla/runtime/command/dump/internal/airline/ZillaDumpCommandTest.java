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
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import io.aklivity.zilla.specs.binding.grpc.internal.GrpcFunctions;
import io.aklivity.zilla.specs.binding.http.internal.HttpFunctions;
import io.aklivity.zilla.specs.binding.proxy.internal.ProxyFunctions;
import io.aklivity.zilla.specs.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.specs.engine.internal.types.stream.WindowFW;

@TestInstance(PER_CLASS)
public class ZillaDumpCommandTest
{
    private static final int WORKERS = 3;
    private static final int STREAMS_CAPACITY = 8 * 1024;
    private static final Path ENGINE_PATH =
        Path.of("src/test/resources/io/aklivity/zilla/runtime/command/dump/internal/airline/engine");
    private static final int GRPC_TYPE_ID = 2;
    private static final int HTTP_TYPE_ID = 3;
    private static final int PROXY_TYPE_ID = 5;
    private static final byte[] EMPTY_BYTES = {};

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final SignalFW.Builder signalRW = new SignalFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    @TempDir
    private File tempDir;

    private ZillaDumpCommand command;

    @BeforeAll
    @SuppressWarnings("checkstyle:methodlength")
    public void generateStreamsBuffer() throws Exception
    {
        RingBuffer[] streams = new RingBuffer[WORKERS];
        for (int i = 0; i < WORKERS; i++)
        {
            StreamsLayout streamsLayout = new StreamsLayout.Builder()
                .path(ENGINE_PATH.resolve(String.format("data%d", i)))
                .streamsCapacity(STREAMS_CAPACITY)
                .readonly(false)
                .build();
            streams[i] = streamsLayout.streamsBuffer();
        }
        MutableDirectBuffer frameBuffer = new UnsafeBuffer(new byte[STREAMS_CAPACITY]);

        // worker 0
        SignalFW signal1 = signalRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(0)
            .streamId(0)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000001L)
            .traceId(0x0000000000000001L)
            .cancelId(0x0000000000007701L)
            .signalId(0x00007702)
            .contextId(0x00007703)
            .build();
        streams[0].write(SignalFW.TYPE_ID, signal1.buffer(), 0, signal1.sizeof());

        DirectBuffer helloBuf = new String8FW("Hello World!").value();
        SignalFW signal2 = signalRW.wrap(frameBuffer, 0, frameBuffer.capacity())
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
        streams[0].write(SignalFW.TYPE_ID, signal2.buffer(), 0, signal2.sizeof());

        BeginFW begin1 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
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
        streams[0].write(BeginFW.TYPE_ID, begin1.buffer(), 0, begin1.sizeof());

        WindowFW window1 = windowRW.wrap(frameBuffer, 0, frameBuffer.capacity())
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
        streams[0].write(WindowFW.TYPE_ID, window1.buffer(), 0, window1.sizeof());

        BeginFW begin2 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
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
        streams[0].write(BeginFW.TYPE_ID, begin2.buffer(), 0, begin2.sizeof());

        WindowFW window2 = windowRW.wrap(frameBuffer, 0, frameBuffer.capacity())
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
        streams[0].write(WindowFW.TYPE_ID, window2.buffer(), 0, window2.sizeof());

        BeginFW filteredBegin = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
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
        streams[0].write(BeginFW.TYPE_ID, filteredBegin.buffer(), 0, filteredBegin.sizeof());

        String http1request =
            "POST / HTTP/1.1\n" +
            "Host: localhost:8080\n" +
            "User-Agent: curl/7.85.0\n" +
            "Accept: */*\n" +
            "Content-Type: text/plain\n" +
            "Content-Length: 12\n" +
            "\n" +
            "Hello, world";
        DirectBuffer http1requestBuf = new String8FW(http1request).value();
        DataFW data1 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
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
            .payload(http1requestBuf, 0, http1requestBuf.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data1.buffer(), 0, data1.sizeof());

        String http1response =
            "HTTP/1.1 200 OK\n" +
            "Content-Type: text/plain\n" +
            "Content-Length: 13\n" +
            "\n" +
            "Hello, World!";
        DirectBuffer http1responseBuf = new String8FW(http1response).value();
        DataFW data2 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
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
            .payload(http1responseBuf, 0, http1responseBuf.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data2.buffer(), 0, data2.sizeof());

        ChallengeFW challenge1 = challengeRW.wrap(frameBuffer, 0, frameBuffer.capacity())
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
        streams[0].write(ChallengeFW.TYPE_ID, challenge1.buffer(), 0, challenge1.sizeof());

        // POST https://localhost:7142/
        byte[] h2request = BitUtil.fromHex(
            "00002c0104000000018387418aa0e41d139d09b8e85a67847a8825b650c3cb85717f53032a2f2a5f87497ca58ae819aa0f0d023132");
        DirectBuffer h2requestBuf = new UnsafeBuffer(h2request);
        DataFW data3 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000005L) // INI
            .sequence(123)
            .acknowledge(456)
            .maximum(777)
            .timestamp(0x000000000000000bL)
            .traceId(0x0000000000000003L)
            .budgetId(0x0000000000004405L)
            .reserved(0x00004206)
            .payload(h2requestBuf, 0, h2requestBuf.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data3.buffer(), 0, data3.sizeof());

        // 200 OK
        byte[] h2response = BitUtil.fromHex(
            "000026010400000001880f2b0a6375726c2f382e312e320f04032a2f2a0f100a746578742f706c61696e0f0d023132");
        DirectBuffer h2responseBuf = new UnsafeBuffer(h2response);
        DataFW data4 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(123)
            .acknowledge(456)
            .maximum(777)
            .timestamp(0x000000000000000cL)
            .traceId(0x0000000000000003L)
            .budgetId(0x0000000000004405L)
            .reserved(0x00004206)
            .payload(h2responseBuf, 0, h2responseBuf.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data4.buffer(), 0, data4.sizeof());

        DirectBuffer hello2Buf = new String8FW("Hello World!").value();
        DataFW data5 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(123)
            .acknowledge(456)
            .maximum(777)
            .timestamp(0x000000000000000dL)
            .traceId(0x0000000000000003L)
            .budgetId(0x0000000000004405L)
            .reserved(0x00004206)
            .payload(hello2Buf, 0, hello2Buf.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data5.buffer(), 0, data5.sizeof());

        FlushFW flush1 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(301)
            .acknowledge(302)
            .maximum(3344)
            .timestamp(0x000000000000000eL)
            .traceId(0x0000000000000003L)
            .budgetId(0x0000000000003300L)
            .reserved(0x00003303)
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush1.buffer(), 0, flush1.sizeof());

        AbortFW abort1 = abortRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000005L) // INI
            .sequence(401)
            .acknowledge(402)
            .maximum(4477)
            .timestamp(0x000000000000000fL)
            .traceId(0x0000000000000003L)
            .build();
        streams[0].write(AbortFW.TYPE_ID, abort1.buffer(), 0, abort1.sizeof());

        ResetFW reset1 = resetRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000006L) // REP
            .sequence(501)
            .acknowledge(502)
            .maximum(5577)
            .timestamp(0x0000000000000010L)
            .traceId(0x0000000000000003L)
            .build();
        streams[0].write(ResetFW.TYPE_ID, reset1.buffer(), 0, reset1.sizeof());

        EndFW end1 = endRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000005L) // INI
            .sequence(701)
            .acknowledge(702)
            .maximum(7777)
            .timestamp(0x0000000000000011L)
            .traceId(0x0000000000000003L)
            .build();
        streams[0].write(EndFW.TYPE_ID, end1.buffer(), 0, end1.sizeof());

        EndFW end2 = endRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000004L) // REP
            .sequence(703)
            .acknowledge(704)
            .maximum(4444)
            .timestamp(0x0000000000000012L)
            .traceId(0x0000000000000003L)
            .build();
        streams[0].write(EndFW.TYPE_ID, end2.buffer(), 0, end2.sizeof());

        // proxy extension
        DirectBuffer proxyBeginEx1 = new UnsafeBuffer(ProxyFunctions.beginEx()
            .typeId(PROXY_TYPE_ID)
            .addressInet()
                .protocol("stream")
                .source("192.168.0.77")
                .destination("192.168.0.42")
                .sourcePort(12345)
                .destinationPort(442)
                .build()
            .build());
        BeginFW begin3 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000011L) // south_kafka_client
            .routedId(0x0000000900000012L) // south_tcp_client
            .streamId(0x0000000000000009L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x00000000000000013L)
            .traceId(0x0000000000000009L)
            .affinity(0x0000000000000000L)
            .extension(proxyBeginEx1, 0, proxyBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin3.buffer(), 0, begin3.sizeof());

        DirectBuffer proxyBeginEx2 = new UnsafeBuffer(ProxyFunctions.beginEx()
            .typeId(PROXY_TYPE_ID)
            .addressInet4()
                .protocol("stream")
                .source("192.168.0.1")
                .destination("192.168.0.254")
                .sourcePort(32768)
                .destinationPort(443)
                .build()
            .info()
                .alpn("alpn")
                .authority("authority")
                .identity(BitUtil.fromHex("12345678"))
                .namespace("namespace")
                .secure()
                    .version("TLSv1.3")
                    .name("name")
                    .cipher("cipher")
                    .signature("signature")
                    .key("key")
                    .build()
                .build()
            .build());
        BeginFW begin4 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000011L) // south_kafka_client
            .routedId(0x0000000900000012L) // south_tcp_client
            .streamId(0x0000000000000009L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x00000000000000014L)
            .traceId(0x0000000000000009L)
            .affinity(0x0000000000000000L)
            .extension(proxyBeginEx2, 0, proxyBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin4.buffer(), 0, begin4.sizeof());

        DirectBuffer proxyBeginEx3 = new UnsafeBuffer(ProxyFunctions.beginEx()
            .typeId(PROXY_TYPE_ID)
            .addressInet6()
                .protocol("stream")
                .source("fd12:3456:789a:1::1")
                .destination("fd12:3456:789a:1::fe")
                .sourcePort(32768)
                .destinationPort(443)
                .build()
            .build());
        BeginFW begin5 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000011L) // south_kafka_client
            .routedId(0x0000000900000012L) // south_tcp_client
            .streamId(0x0000000000000009L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x00000000000000015L)
            .traceId(0x0000000000000009L)
            .affinity(0x0000000000000000L)
            .extension(proxyBeginEx3, 0, proxyBeginEx3.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin5.buffer(), 0, begin5.sizeof());

        DirectBuffer proxyBeginEx4 = new UnsafeBuffer(ProxyFunctions.beginEx()
            .typeId(PROXY_TYPE_ID)
            .addressUnix()
                .protocol("datagram")
                .source("unix-source")
                .destination("unix-destination")
                .build()
            .build());
        BeginFW begin6 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000011L) // south_kafka_client
            .routedId(0x0000000900000012L) // south_tcp_client
            .streamId(0x0000000000000009L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x00000000000000016L)
            .traceId(0x0000000000000009L)
            .affinity(0x0000000000000000L)
            .extension(proxyBeginEx4, 0, proxyBeginEx4.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin6.buffer(), 0, begin6.sizeof());

        DirectBuffer proxyBeginEx5 = new UnsafeBuffer(ProxyFunctions.beginEx()
            .typeId(PROXY_TYPE_ID)
            .addressNone()
                .build()
            .build());
        BeginFW begin7 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000011L) // south_kafka_client
            .routedId(0x0000000900000012L) // south_tcp_client
            .streamId(0x0000000000000009L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x00000000000000017L)
            .traceId(0x0000000000000009L)
            .affinity(0x0000000000000000L)
            .extension(proxyBeginEx5, 0, proxyBeginEx5.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin7.buffer(), 0, begin7.sizeof());

        // http extension
        DirectBuffer httpBeginEx1 = new UnsafeBuffer(HttpFunctions.beginEx()
            .typeId(HTTP_TYPE_ID)
            .header(":scheme", "http")
            .header(":method", "GET")
            .header(":path", "/hello")
            .build());
        BeginFW begin8 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000011L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000018L)
            .traceId(0x0000000000000011L)
            .affinity(0x0000000000000000L)
            .extension(httpBeginEx1, 0, httpBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin8.buffer(), 0, begin8.sizeof());

        DirectBuffer httpChallengeEx1 = new UnsafeBuffer(HttpFunctions.challengeEx()
            .typeId(HTTP_TYPE_ID)
            .header(":scheme", "http")
            .header(":method", "GET")
            .header(":path", "/hello")
            .build());
        ChallengeFW challenge2 = challengeRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000011L) // INI
            .sequence(201)
            .acknowledge(202)
            .maximum(22222)
            .timestamp(0x0000000000000019L)
            .traceId(0x0000000000000011L)
            .authorization(0x0000000000007742L)
            .extension(httpChallengeEx1, 0, httpChallengeEx1.capacity())
            .build();
        streams[0].write(ChallengeFW.TYPE_ID, challenge2.buffer(), 0, challenge2.sizeof());

        DirectBuffer httpFlushEx1 = new UnsafeBuffer(HttpFunctions.flushEx()
            .typeId(HTTP_TYPE_ID)
            .promiseId(0x0000000000000042L)
            .promise(":scheme", "http")
            .promise(":method", "GET")
            .promise(":path", "/hello")
            .build());
        FlushFW flush2 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000010L) // REP
            .sequence(301)
            .acknowledge(302)
            .maximum(3344)
            .timestamp(0x0000000000000020L)
            .traceId(0x0000000000000011L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(httpFlushEx1, 0, httpFlushEx1.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush2.buffer(), 0, flush2.sizeof());

        DirectBuffer httpResetEx1 = new UnsafeBuffer(HttpFunctions.resetEx()
            .typeId(HTTP_TYPE_ID)
            .header(":scheme", "http")
            .header(":method", "GET")
            .header(":path", "/hello")
            .build());
        ResetFW reset2 = resetRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000010L) // REP
            .sequence(501)
            .acknowledge(502)
            .maximum(5577)
            .timestamp(0x0000000000000021L)
            .traceId(0x0000000000000011L)
            .extension(httpResetEx1, 0, httpResetEx1.capacity())
            .build();
        streams[0].write(ResetFW.TYPE_ID, reset2.buffer(), 0, reset2.sizeof());

        DirectBuffer httpEndEx1 = new UnsafeBuffer(HttpFunctions.endEx()
            .typeId(HTTP_TYPE_ID)
            .trailer(":scheme", "http")
            .trailer(":method", "GET")
            .trailer(":path", "/hello")
            .build());
        EndFW end3 = endRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0000000000000011L) // INI
            .sequence(742)
            .acknowledge(427)
            .maximum(60000)
            .timestamp(0x0000000000000022L)
            .traceId(0x0000000000000011L)
            .extension(httpEndEx1, 0, httpEndEx1.capacity())
            .build();
        streams[0].write(EndFW.TYPE_ID, end3.buffer(), 0, end3.sizeof());

        // worker 1
        SignalFW signal3 = signalRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(0)
            .streamId(0)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000001L)
            .traceId(0x0100000000000001L)
            .cancelId(0x0000000000008801L)
            .signalId(0x00008802)
            .contextId(0x00008803)
            .build();
        streams[1].write(SignalFW.TYPE_ID, signal3.buffer(), 0, signal3.sizeof());

        BeginFW begin9 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0101000000000005L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000002L)
            .traceId(0x0100000000000003L)
            .affinity(0x0101000000000005L)
            .build();
        streams[1].write(BeginFW.TYPE_ID, begin9.buffer(), 0, begin9.sizeof());

        EndFW end4 = endRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0101000000000004L) // REP
            .sequence(703)
            .acknowledge(704)
            .maximum(4444)
            .timestamp(0x0000000000000003L)
            .traceId(0x0100000000000003L)
            .build();
        streams[1].write(EndFW.TYPE_ID, end4.buffer(), 0, end4.sizeof());

        // worker 2
        SignalFW signal4 = signalRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0)
            .routedId(0)
            .streamId(0)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000001L)
            .traceId(0x0200000000000001L)
            .cancelId(0x0000000000008801L)
            .signalId(0x00009902)
            .contextId(0x00009903)
            .build();
        streams[2].write(SignalFW.TYPE_ID, signal4.buffer(), 0, signal4.sizeof());

        BeginFW begin10 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0202000000000005L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000002L)
            .traceId(0x0200000000000003L)
            .affinity(0x0202000000000005L)
            .build();
        streams[2].write(BeginFW.TYPE_ID, begin10.buffer(), 0, begin10.sizeof());

        EndFW end5 = endRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000dL) // north_http_server
            .streamId(0x0202000000000004L) // REP
            .sequence(703)
            .acknowledge(704)
            .maximum(4444)
            .timestamp(0x0000000000000003L)
            .traceId(0x0200000000000003L)
            .build();
        streams[2].write(EndFW.TYPE_ID, end5.buffer(), 0, end5.sizeof());

        // worker 0
        // grpc extension
        DirectBuffer grpcBeginEx1 = new UnsafeBuffer(GrpcFunctions.beginEx()
            .typeId(GRPC_TYPE_ID)
            .scheme("http")
            .authority("localhost:7153")
            .service("example.EchoService")
            .method("EchoUnary")
            .metadata("grpc-accept-encoding", "gzip")
            .metadata("metadata-2", "hello")
            .metadata("BASE64", "metadata-3", "4242")
            .build());
        BeginFW begin11 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000013L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000023L)
            .traceId(0x0000000000000013L)
            .affinity(0x0000000000000000L)
            .extension(grpcBeginEx1, 0, grpcBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin11.buffer(), 0, begin11.sizeof());

        DirectBuffer grpcBeginEx2 = new UnsafeBuffer(GrpcFunctions.beginEx()
            .typeId(GRPC_TYPE_ID)
            .scheme("http")
            .authority("localhost:7153")
            .service("example.EchoService")
            .method("EchoUnary")
            .metadata("long field", "Z".repeat(200))
            .metadata("metadata-2", "hello")
            .build());
        BeginFW begin12 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000012L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000024L)
            .traceId(0x0000000000000013L)
            .affinity(0x0000000000000000L)
            .extension(grpcBeginEx2, 0, grpcBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin12.buffer(), 0, begin12.sizeof());

        // data frame with extension, without payload, payload length is -1
        DirectBuffer grpcDataEx1 = new UnsafeBuffer(new byte[]{GRPC_TYPE_ID, 0, 0, 0, 42, 0, 0, 0});
        DataFW data6 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000013L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000025L)
            .traceId(0x0000000000000013L)
            .budgetId(0x0000000000000013L)
            .reserved(0x00000042)
            .extension(grpcDataEx1, 0, grpcDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data6.buffer(), 0, data6.sizeof());

        // data frame with extension, without payload, payload length is 0
        DirectBuffer grpcDataPayload1 = new UnsafeBuffer(EMPTY_BYTES);
        DirectBuffer grpcDataEx2 = new UnsafeBuffer(new byte[]{GRPC_TYPE_ID, 0, 0, 0, 77, 0, 0, 0});
        DataFW data7 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000012L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000026L)
            .traceId(0x0000000000000013L)
            .budgetId(0x0000000000000013L)
            .reserved(0x00000042)
            .payload(grpcDataPayload1, 0, grpcDataPayload1.capacity())
            .extension(grpcDataEx2, 0, grpcDataEx2.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data7.buffer(), 0, data7.sizeof());

        // data frame with extension, with payload
        DirectBuffer grpcDataPayload2 = new UnsafeBuffer("Hello World!".getBytes(StandardCharsets.UTF_8));
        DirectBuffer grpcDataEx3 = new UnsafeBuffer(new byte[]{GRPC_TYPE_ID, 0, 0, 0, 88, 0, 0, 0});
        DataFW data8 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000013L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000027L)
            .traceId(0x0000000000000013L)
            .budgetId(0x0000000000000013L)
            .reserved(0x00000042)
            .payload(grpcDataPayload2, 0, grpcDataPayload2.capacity())
            .extension(grpcDataEx3, 0, grpcDataEx3.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data8.buffer(), 0, data8.sizeof());

        DirectBuffer grpcAbortEx1 = new UnsafeBuffer(GrpcFunctions.abortEx()
            .typeId(GRPC_TYPE_ID)
            .status("aborted")
            .build());
        AbortFW abort2 = abortRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000013L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000028L)
            .traceId(0x0000000000000013L)
            .extension(grpcAbortEx1, 0, grpcAbortEx1.capacity())
            .build();
        streams[0].write(AbortFW.TYPE_ID, abort2.buffer(), 0, abort2.sizeof());

        DirectBuffer grpcResetEx1 = new UnsafeBuffer(GrpcFunctions.abortEx()
            .typeId(GRPC_TYPE_ID)
            .status("reset")
            .build());
        ResetFW reset3 = resetRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000012L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000029L)
            .traceId(0x0000000000000013L)
            .extension(grpcResetEx1, 0, grpcResetEx1.capacity())
            .build();
        streams[0].write(ResetFW.TYPE_ID, reset3.buffer(), 0, reset3.sizeof());
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
