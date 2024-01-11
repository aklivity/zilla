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

import java.io.ByteArrayOutputStream;
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
import org.testcontainers.shaded.org.apache.commons.io.HexDump;

import io.aklivity.zilla.runtime.command.dump.internal.types.String8FW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.engine.internal.layouts.StreamsLayout;
import io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions;
import io.aklivity.zilla.specs.binding.filesystem.internal.FileSystemFunctions;
import io.aklivity.zilla.specs.binding.grpc.internal.GrpcFunctions;
import io.aklivity.zilla.specs.binding.http.internal.HttpFunctions;
import io.aklivity.zilla.specs.binding.kafka.internal.KafkaFunctions;
import io.aklivity.zilla.specs.binding.mqtt.internal.MqttFunctions;
import io.aklivity.zilla.specs.binding.proxy.internal.ProxyFunctions;
import io.aklivity.zilla.specs.binding.sse.internal.SseFunctions;
import io.aklivity.zilla.specs.binding.ws.internal.WsFunctions;
import io.aklivity.zilla.specs.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.specs.engine.internal.types.stream.WindowFW;

@TestInstance(PER_CLASS)
public class ZillaDumpCommandTest
{
    private static final int WORKERS = 3;
    private static final int STREAMS_CAPACITY = 32 * 1024;
    private static final Path ENGINE_PATH =
        Path.of("src/test/resources/io/aklivity/zilla/runtime/command/dump/internal/airline/engine");
    private static final int FILESYSTEM_TYPE_ID = 1;
    private static final int GRPC_TYPE_ID = 2;
    private static final int HTTP_TYPE_ID = 3;
    private static final int KAFKA_TYPE_ID = 4;
    private static final int PROXY_TYPE_ID = 5;
    private static final int MQTT_TYPE_ID = 6;
    private static final int SSE_TYPE_ID = 7;
    private static final int WS_TYPE_ID = 8;
    private static final int AMQP_TYPE_ID = 36;

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
            .originId(0x000000090000000dL) // north_http_server
            .routedId(0x000000090000000eL) // north_http_kafka_mapping
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

        // data frame with h2 request payload: POST https://localhost:7142/
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

        // data frame with h2 response payload: 200 OK
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
            .timestamp(0x000000000000001aL)
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
            .timestamp(0x000000000000001bL)
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
            .timestamp(0x000000000000001cL)
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
            .timestamp(0x000000000000001dL)
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
            .timestamp(0x000000000000001eL)
            .traceId(0x0000000000000013L)
            .affinity(0x0000000000000000L)
            .extension(grpcBeginEx2, 0, grpcBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin12.buffer(), 0, begin12.sizeof());

        // data frame with extension, without payload, payload length is -1
        DirectBuffer grpcDataEx1 = new UnsafeBuffer(new byte[]{
            GRPC_TYPE_ID, 0, 0, 0,  // int32 typeId
            42, 0, 0, 0             // int32 deferred
        });
        DataFW data6 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000013L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000001fL)
            .traceId(0x0000000000000013L)
            .budgetId(0x0000000000000013L)
            .reserved(0x00000042)
            .extension(grpcDataEx1, 0, grpcDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data6.buffer(), 0, data6.sizeof());

        // data frame with extension, without payload, payload length is 0
        DirectBuffer grpcDataPayload1 = new UnsafeBuffer();
        DirectBuffer grpcDataEx2 = new UnsafeBuffer(new byte[]{
            GRPC_TYPE_ID, 0, 0, 0,  // int32 typeId
            77, 0, 0, 0             // int32 deferred
        });
        DataFW data7 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000012L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000020L)
            .traceId(0x0000000000000013L)
            .budgetId(0x0000000000000013L)
            .reserved(0x00000042)
            .payload(grpcDataPayload1, 0, grpcDataPayload1.capacity())
            .extension(grpcDataEx2, 0, grpcDataEx2.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data7.buffer(), 0, data7.sizeof());

        // data frame with extension, with payload
        DirectBuffer grpcDataPayload2 = new UnsafeBuffer("Hello World!".getBytes(StandardCharsets.UTF_8));
        DirectBuffer grpcDataEx3 = new UnsafeBuffer(new byte[]{
            GRPC_TYPE_ID, 0, 0, 0,  // int32 typeId
            88, 0, 0, 0             // int32 deferred
        });
        DataFW data8 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001aL) // north_grpc_server
            .routedId(0x000000090000001bL) // north_grpc_kafka_mapping
            .streamId(0x0000000000000013L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000021L)
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
            .timestamp(0x0000000000000022L)
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
            .timestamp(0x0000000000000023L)
            .traceId(0x0000000000000013L)
            .extension(grpcResetEx1, 0, grpcResetEx1.capacity())
            .build();
        streams[0].write(ResetFW.TYPE_ID, reset3.buffer(), 0, reset3.sizeof());

        // sse extension
        DirectBuffer sseBeginEx1 = new UnsafeBuffer(SseFunctions.beginEx()
            .typeId(SSE_TYPE_ID)
            .scheme("http")
            .authority("localhost:7153")
            .path("/hello")
            .lastId(null)  // length will be -1
            .build());
        BeginFW begin13 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001cL) // north_sse_server
            .routedId(0x000000090000001dL) // south_sse_client
            .streamId(0x0000000000000015L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000024L)
            .traceId(0x0000000000000015L)
            .affinity(0x0000000000000000L)
            .extension(sseBeginEx1, 0, sseBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin13.buffer(), 0, begin13.sizeof());

        DirectBuffer sseBeginEx2 = new UnsafeBuffer(SseFunctions.beginEx()
            .typeId(SSE_TYPE_ID)
            .scheme("http")
            .authority("localhost:7153")
            .path("/hello")
            .lastId("lastId")
            .build());
        BeginFW begin14 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001cL) // north_sse_server
            .routedId(0x000000090000001dL) // south_sse_client
            .streamId(0x0000000000000014L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000025L)
            .traceId(0x0000000000000015L)
            .affinity(0x0000000000000000L)
            .extension(sseBeginEx2, 0, sseBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin14.buffer(), 0, begin14.sizeof());

        DirectBuffer sseDataEx1 = new UnsafeBuffer(SseFunctions.dataEx()
            .typeId(SSE_TYPE_ID)
            .timestamp(0x0000000000000026L)
            .id("id")
            .type("type")
            .build());
        DataFW data9 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001cL) // north_sse_server
            .routedId(0x000000090000001dL) // south_sse_client
            .streamId(0x0000000000000015L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000026L)
            .traceId(0x0000000000000015L)
            .budgetId(0x0000000000000015L)
            .reserved(0x00000042)
            .extension(sseDataEx1, 0, sseDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data9.buffer(), 0, data9.sizeof());

        DirectBuffer ssePayload1 = new UnsafeBuffer("Hello SSE!".getBytes(StandardCharsets.UTF_8));
        DirectBuffer sseDataEx2 = new UnsafeBuffer(SseFunctions.dataEx()
            .typeId(SSE_TYPE_ID)
            .timestamp(0x0000000000000027L)
            .id(null) // length will be -1
            .type("fortytwo")
            .build());
        DataFW data10 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001cL) // north_sse_server
            .routedId(0x000000090000001dL) // south_sse_client
            .streamId(0x0000000000000014L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000027L)
            .traceId(0x0000000000000015L)
            .budgetId(0x0000000000000015L)
            .reserved(0x00000042)
            .payload(ssePayload1, 0, ssePayload1.capacity())
            .extension(sseDataEx2, 0, sseDataEx2.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data10.buffer(), 0, data10.sizeof());

        DirectBuffer sseEndEx1 = new UnsafeBuffer(SseFunctions.endEx()
            .typeId(SSE_TYPE_ID)
            .id("sse-end-id")
            .build());
        EndFW end6 = endRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001cL) // north_sse_server
            .routedId(0x000000090000001dL) // south_sse_client
            .streamId(0x0000000000000014L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000028L)
            .traceId(0x0000000000000015L)
            .extension(sseEndEx1, 0, sseEndEx1.capacity())
            .build();
        streams[0].write(EndFW.TYPE_ID, end6.buffer(), 0, end6.sizeof());

        // ws extension
        DirectBuffer wsBeginEx1 = new UnsafeBuffer(WsFunctions.beginEx()
            .typeId(WS_TYPE_ID)
            .protocol("echo")
            .scheme("http")
            .authority("localhost:7114")
            .path("/hello")
            .build());
        BeginFW begin15 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001eL) // north_ws_server
            .routedId(0x000000090000001fL) // north_echo_server
            .streamId(0x0000000000000017L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000029L)
            .traceId(0x0000000000000017L)
            .affinity(0x0000000000000000L)
            .extension(wsBeginEx1, 0, wsBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin15.buffer(), 0, begin15.sizeof());

        DirectBuffer wsBeginEx2 = new UnsafeBuffer(WsFunctions.beginEx()
            .typeId(WS_TYPE_ID)
            .protocol("echo")
            .scheme("http")
            .authority("localhost:7114")
            .path("/hello")
            .build());
        BeginFW begin16 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001eL) // north_ws_server
            .routedId(0x000000090000001fL) // north_echo_server
            .streamId(0x0000000000000016L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000002aL)
            .traceId(0x0000000000000017L)
            .affinity(0x0000000000000000L)
            .extension(wsBeginEx2, 0, wsBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin16.buffer(), 0, begin16.sizeof());

        DirectBuffer wsDataEx1 = new UnsafeBuffer(new byte[]{
            WS_TYPE_ID, 0, 0, 0,    // int32 typeId
            0x42,                   // uint8 flags
        });
        DataFW data11 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001eL) // north_ws_server
            .routedId(0x000000090000001fL) // north_echo_server
            .streamId(0x0000000000000017L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000002bL)
            .traceId(0x0000000000000017L)
            .budgetId(0x0000000000000017L)
            .reserved(0x00000042)
            .extension(wsDataEx1, 0, wsDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data11.buffer(), 0, data11.sizeof());

        DirectBuffer wsPayload1 = new UnsafeBuffer("Hello Websocket!".getBytes(StandardCharsets.UTF_8));
        DirectBuffer wsDataEx2 = new UnsafeBuffer(new byte[]{
            WS_TYPE_ID, 0, 0, 0,                // int32 typeId
            0x33,                               // uint8 flags
            0x42, 0x77, 0x44, 0x33, 0x21, 0x07  // octets info
        });
        DataFW data12 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001eL) // north_ws_server
            .routedId(0x000000090000001fL) // north_echo_server
            .streamId(0x0000000000000016L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000002cL)
            .traceId(0x0000000000000017L)
            .budgetId(0x0000000000000017L)
            .reserved(0x00000042)
            .payload(wsPayload1, 0, wsPayload1.capacity())
            .extension(wsDataEx2, 0, wsDataEx2.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data12.buffer(), 0, data12.sizeof());

        DirectBuffer wsEndEx1 = new UnsafeBuffer(new byte[]{
            WS_TYPE_ID, 0, 0, 0,        // int32 typeId
            42, 0,                      // int16 code
            5, 'h', 'e', 'l', 'l', 'o'  // string8 reason
        });
        EndFW end7 = endRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000001eL) // north_ws_server
            .routedId(0x000000090000001fL) // north_echo_server
            .streamId(0x0000000000000017L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000002dL)
            .traceId(0x0000000000000017L)
            .extension(wsEndEx1, 0, wsEndEx1.capacity())
            .build();
        streams[0].write(EndFW.TYPE_ID, end7.buffer(), 0, end7.sizeof());

        // filesystem extension
        DirectBuffer fileSystemBeginEx1 = new UnsafeBuffer(FileSystemFunctions.beginEx()
            .typeId(FILESYSTEM_TYPE_ID)
            .capabilities("READ_PAYLOAD", "READ_EXTENSION", "READ_CHANGES")
            .path("/hello")
            .type("type")
            .payloadSize(42_000_000_000L)
            .tag("tag")
            .timeout(77)
            .build());
        BeginFW begin17 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000020L) // east_http_filesystem_mapping
            .routedId(0x0000000900000021L) // east_filesystem_server
            .streamId(0x0000000000000019L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000002eL)
            .traceId(0x0000000000000019L)
            .affinity(0x0000000000000000L)
            .extension(fileSystemBeginEx1, 0, fileSystemBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin17.buffer(), 0, begin17.sizeof());

        DirectBuffer fileSystemBeginEx2 = new UnsafeBuffer(FileSystemFunctions.beginEx()
            .typeId(FILESYSTEM_TYPE_ID)
            .capabilities("READ_EXTENSION")
            .path("/hello")
            .type("type")
            .payloadSize(0)
            .tag("tag")
            .timeout(0)
            .build());
        BeginFW begin18 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000020L) // east_http_filesystem_mapping
            .routedId(0x0000000900000021L) // east_filesystem_server
            .streamId(0x0000000000000018L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000002fL)
            .traceId(0x0000000000000019L)
            .affinity(0x0000000000000000L)
            .extension(fileSystemBeginEx2, 0, fileSystemBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin18.buffer(), 0, begin18.sizeof());

        // data frame with tls payload: TLSv1.3 Server Hello
        DirectBuffer tlsPayload1 = new UnsafeBuffer(BitUtil.fromHex(
            "160303007a020000760303328f126a2dc67b1d107023f088ca43560c8b1535c9d7e1be8b217b60b8cefa32209d830c3919be" +
            "a4f53b3ace6b5f6837c9914c982f1421d3e162606c3eb5907c16130200002e002b0002030400330024001d00201c00c791d3" +
            "e7b6b5dc3f191be9e29a7e220e8ea695696b281e7f92e27a05f27e"));
        DataFW data13 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x000000090000000cL) // north_tls_server
            .streamId(0x000000000000001bL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000030L)
            .traceId(0x000000000000001aL)
            .budgetId(0x000000000000001aL)
            .reserved(0x00000042)
            .payload(tlsPayload1, 0, tlsPayload1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data13.buffer(), 0, data13.sizeof());

        // data frame with mqtt payload: mqtt Connect Command
        DirectBuffer mqttPayload1 = new UnsafeBuffer(BitUtil.fromHex("101000044d5154540502003c032100140000"));
        DataFW data14 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x0000000900000022L) // north_mqtt_server
            .streamId(0x000000000000001bL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000031L)
            .traceId(0x000000000000001bL)
            .budgetId(0x000000000000001bL)
            .reserved(0x00000077)
            .payload(mqttPayload1, 0, mqttPayload1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data14.buffer(), 0, data14.sizeof());

        // data frame with kafka payload: Kafka (Fetch v5 Request)
        DirectBuffer kafkaPayload1 = new UnsafeBuffer(BitUtil.fromHex(
            "00000051000100050000000100057a696c6c61ffffffff0000000000000001032000000000000001000f6974656d732d7265" +
            "73706f6e73657300000001000000000000000000000000ffffffffffffffff03200000"));
        DataFW data15 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000011L) // south_kafka_client
            .routedId(0x0000000900000012L) // south_tcp_client
            .streamId(0x000000000000001bL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000032L)
            .traceId(0x000000000000001bL)
            .budgetId(0x000000000000001bL)
            .reserved(0x00000088)
            .payload(kafkaPayload1, 0, kafkaPayload1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data15.buffer(), 0, data15.sizeof());

        // data frame with kafka payload: Kafka (Fetch v5 Response)
        DirectBuffer kafkaPayload2 = new UnsafeBuffer(BitUtil.fromHex(
            "00000047000000010000000000000001000f6974656d732d726573706f6e7365730000000100000000000000000000000000" +
            "0000000000000000000000000000000000ffffffff00000000"));
        DataFW data16 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000011L) // south_kafka_client
            .routedId(0x0000000900000012L) // south_tcp_client
            .streamId(0x000000000000001aL) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000033L)
            .traceId(0x000000000000001bL)
            .budgetId(0x000000000000001bL)
            .reserved(0x00000088)
            .payload(kafkaPayload2, 0, kafkaPayload2.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data16.buffer(), 0, data16.sizeof());

        // data frame with amqp payload: Protocol-Header 1-0-0
        DirectBuffer amqpPayload1 = new UnsafeBuffer(BitUtil.fromHex("414d515000010000"));
        DataFW data17 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000bL) // north_tcp_server
            .routedId(0x0000000900000025L) // north_amqp_server
            .streamId(0x000000000000001bL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000034L)
            .traceId(0x000000000000001bL)
            .budgetId(0x000000000000001bL)
            .reserved(0x00000077)
            .payload(amqpPayload1, 0, amqpPayload1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data17.buffer(), 0, data17.sizeof());

        // mqtt extension
        // - publish
        DirectBuffer mqttPublishBeginEx1 = new UnsafeBuffer(MqttFunctions.beginEx()
            .typeId(MQTT_TYPE_ID)
            .publish()
                .clientId("client-id")
                .topic("topic")
                .qos(1)
            .build()
            .build());
        BeginFW begin19 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000021L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000035L)
            .traceId(0x0000000000000021L)
            .affinity(0x0000000000000000L)
            .extension(mqttPublishBeginEx1, 0, mqttPublishBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin19.buffer(), 0, begin19.sizeof());

        DirectBuffer mqttPublishBeginEx2 = new UnsafeBuffer(MqttFunctions.beginEx()
            .typeId(MQTT_TYPE_ID)
            .publish()
                .clientId("client-id")
                .topic("topic")
                .flags("RETAIN")
                .qos(2)
                .build()
            .build());
        BeginFW begin20 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000020L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000036L)
            .traceId(0x0000000000000021L)
            .affinity(0x0000000000000000L)
            .extension(mqttPublishBeginEx2, 0, mqttPublishBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin20.buffer(), 0, begin20.sizeof());

        DirectBuffer mqttPublishDataPayload = new String8FW("Hello, mqtt-pub!").value();
        DirectBuffer mqttPublishDataEx1 = new UnsafeBuffer(MqttFunctions.dataEx()
            .typeId(MQTT_TYPE_ID)
            .publish()
                .qos("AT_LEAST_ONCE")
                .expiryInterval(42)
                .contentType("Content Type")
                .format("TEXT")
                .responseTopic("Response Topic")
                .correlation("Correlation")
                .userProperty("key1", "value1")
                .userProperty("key42", "value42")
                .userProperty("key77", "value77")
                .build()
            .build());
        DataFW data18 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000021L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000037L)
            .traceId(0x0000000000000021L)
            .budgetId(0x0000000000000021L)
            .reserved(0x00000000)
            .payload(mqttPublishDataPayload, 0, mqttPublishDataPayload.capacity())
            .extension(mqttPublishDataEx1, 0, mqttPublishDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data18.buffer(), 0, data18.sizeof());

        DirectBuffer mqttPublishDataEx2 = new UnsafeBuffer(MqttFunctions.dataEx()
            .typeId(MQTT_TYPE_ID)
            .publish()
                .qos("EXACTLY_ONCE")
                .flags("RETAIN")
                .expiryInterval(77)
                .contentType("Content Type")
                .format("BINARY")
                .responseTopic("Response Topic")
                .correlation("Correlation")
                .userProperty("key1", "value1")
                .build()
            .build());
        DataFW data19 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000020L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000038L)
            .traceId(0x0000000000000021L)
            .budgetId(0x0000000000000021L)
            .reserved(0x00000000)
            .payload(mqttPublishDataPayload, 0, mqttPublishDataPayload.capacity())
            .extension(mqttPublishDataEx2, 0, mqttPublishDataEx2.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data19.buffer(), 0, data19.sizeof());

        // - subscribe
        DirectBuffer mqttSubscribeBeginEx1 = new UnsafeBuffer(MqttFunctions.beginEx()
            .typeId(MQTT_TYPE_ID)
            .subscribe()
                .clientId("client-id")
                .qos("AT_LEAST_ONCE")
                .filter("pattern-1")
                .filter("pattern-2", 0x42, "AT_MOST_ONCE", "SEND_RETAINED", "RETAIN_AS_PUBLISHED")
                .filter("pattern-3", 0x77, "AT_LEAST_ONCE", "NO_LOCAL", "RETAIN")
                .filter("pattern-4")
                .build()
            .build());
        BeginFW begin21 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000023L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000039L)
            .traceId(0x0000000000000023L)
            .affinity(0x0000000000000000L)
            .extension(mqttSubscribeBeginEx1, 0, mqttSubscribeBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin21.buffer(), 0, begin21.sizeof());

        DirectBuffer mqttSubscribeBeginEx2 = new UnsafeBuffer(MqttFunctions.beginEx()
            .typeId(MQTT_TYPE_ID)
            .subscribe()
                .clientId("client-id")
                .qos("EXACTLY_ONCE")
                .filter("pattern-1")
                .filter("pattern-2", 0x21, "EXACTLY_ONCE")
                .filter("pattern-3", 0x71, "AT_LEAST_ONCE", "SEND_RETAINED", "RETAIN_AS_PUBLISHED",
                    "NO_LOCAL", "RETAIN")
                .filter("pattern-4", 0x81)
                .build()
            .build());
        BeginFW begin22 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000022L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000003aL)
            .traceId(0x0000000000000023L)
            .affinity(0x0000000000000000L)
            .extension(mqttSubscribeBeginEx2, 0, mqttSubscribeBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin22.buffer(), 0, begin22.sizeof());

        DirectBuffer mqttSubscribeDataPayload = new String8FW("Hello, mqtt-sub!").value();
        DirectBuffer mqttSubscribeDataEx1 = new UnsafeBuffer(MqttFunctions.dataEx()
            .typeId(MQTT_TYPE_ID)
            .subscribe()
                .topic("topic")
                .packetId(0x21)
                .qos("AT_LEAST_ONCE")
                // flags omitted, should be 0
                .subscriptionId(13)
                .subscriptionId(42_000)
                .subscriptionId(42_000_024)
                .subscriptionId(Integer.MAX_VALUE) // ff:ff:ff:ff:07 decoded as 2147483647
                .subscriptionId(0)
                .expiryInterval(42)
                .contentType("Content Type")
                // format omitted, should be NONE
                .responseTopic("Response Topic")
                .correlation("Correlation")
                .userProperty("key1", "value1")
                .userProperty("key42", "value42")
                .build()
            .build());
        DataFW data20 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000023L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000003bL)
            .traceId(0x0000000000000023L)
            .budgetId(0x0000000000000023L)
            .reserved(0x00000000)
            .payload(mqttSubscribeDataPayload, 0, mqttSubscribeDataPayload.capacity())
            .extension(mqttSubscribeDataEx1, 0, mqttSubscribeDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data20.buffer(), 0, data20.sizeof());

        DirectBuffer mqttSubscribeDataEx2 = new UnsafeBuffer(MqttFunctions.dataEx()
            .typeId(MQTT_TYPE_ID)
            .subscribe()
                .topic("topic")
                .packetId(0x42)
                .qos("EXACTLY_ONCE")
                .flags("RETAIN")
                .subscriptionId(777_777_777)
                .expiryInterval(21)
                .contentType("Content Type")
                .format("BINARY")
                .responseTopic("Response Topic")
                .correlation("Correlation")
                .userProperty("key1", "value1")
                .userProperty("key42", "value42")
                .build()
            .build());
        DataFW data21 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000022L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000003cL)
            .traceId(0x0000000000000023L)
            .budgetId(0x0000000000000023L)
            .reserved(0x00000000)
            .payload(mqttSubscribeDataPayload, 0, mqttSubscribeDataPayload.capacity())
            .extension(mqttSubscribeDataEx2, 0, mqttSubscribeDataEx2.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data21.buffer(), 0, data21.sizeof());

        DirectBuffer mqttSubscribeFlushEx1 = new UnsafeBuffer(MqttFunctions.flushEx()
            .typeId(MQTT_TYPE_ID)
            .subscribe()
                .qos("EXACTLY_ONCE")
                .packetId(0x4221)
                .state("INCOMPLETE")
                .filter("filter-1", 0x42)
                .build()
            .build());
        FlushFW flush3 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000023L) // INI
            .sequence(401)
            .acknowledge(402)
            .maximum(7777)
            .timestamp(0x000000000000003dL)
            .traceId(0x0000000000000023L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(mqttSubscribeFlushEx1, 0, mqttSubscribeFlushEx1.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush3.buffer(), 0, flush3.sizeof());

        DirectBuffer mqttSubscribeFlushEx2 = new UnsafeBuffer(MqttFunctions.flushEx()
            .typeId(MQTT_TYPE_ID)
            .subscribe()
                .qos("AT_MOST_ONCE")
                .packetId(0x2117)
                .state("COMPLETE")
                .filter("pattern-77", 0x77, "AT_LEAST_ONCE", "SEND_RETAINED", "RETAIN_AS_PUBLISHED",
                    "NO_LOCAL", "RETAIN")
                .build()
            .build());
        FlushFW flush4 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000023L) // INI
            .sequence(401)
            .acknowledge(402)
            .maximum(7777)
            .timestamp(0x000000000000003eL)
            .traceId(0x0000000000000023L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(mqttSubscribeFlushEx2, 0, mqttSubscribeFlushEx2.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush4.buffer(), 0, flush4.sizeof());

        DirectBuffer mqttResetEx1 = new UnsafeBuffer(MqttFunctions.resetEx()
            .typeId(MQTT_TYPE_ID)
            .serverRef("Server Reference")
            .reasonCode(42)
            .reason("Reason")
            .build());
        ResetFW reset4 = resetRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000023L) // INI
            .sequence(501)
            .acknowledge(502)
            .maximum(8888)
            .timestamp(0x000000000000003fL)
            .traceId(0x0000000000000023L)
            .extension(mqttResetEx1, 0, mqttResetEx1.capacity())
            .build();
        streams[0].write(ResetFW.TYPE_ID, reset4.buffer(), 0, reset4.sizeof());

        // - session
        DirectBuffer mqttSessionBeginEx1 = new UnsafeBuffer(MqttFunctions.beginEx()
            .typeId(MQTT_TYPE_ID)
            .session()
                .flags("CLEAN_START")
                .expiry(42)
                .qosMax(2)
                .packetSizeMax(42_000)
                .capabilities("RETAIN")
                .clientId("client-id")
                .build()
            .build());
        BeginFW begin23 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000025L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000040L)
            .traceId(0x0000000000000025L)
            .affinity(0x0000000000000000L)
            .extension(mqttSessionBeginEx1, 0, mqttSessionBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin23.buffer(), 0, begin23.sizeof());

        DirectBuffer mqttSessionBeginEx2 = new UnsafeBuffer(MqttFunctions.beginEx()
            .typeId(MQTT_TYPE_ID)
            .session()
                .flags("CLEAN_START", "WILL")
                .expiry(42)
                .qosMax(2)
                .packetSizeMax(42_000)
                .capabilities("RETAIN", "WILDCARD", "SUBSCRIPTION_IDS", "SHARED_SUBSCRIPTIONS")
                .clientId("client-id")
                .build()
            .build());
        BeginFW begin24 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000024L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000041L)
            .traceId(0x0000000000000025L)
            .affinity(0x0000000000000000L)
            .extension(mqttSessionBeginEx2, 0, mqttSessionBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin24.buffer(), 0, begin24.sizeof());

        DirectBuffer mqttSessionDataPayload = new String8FW("Hello, mqtt session!").value();
        DirectBuffer mqttSessionDataEx1 = new UnsafeBuffer(MqttFunctions.dataEx()
            .typeId(MQTT_TYPE_ID)
            .session()
                .deferred(77)
                .kind("STATE")
                .build()
            .build());
        DataFW data22 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000025L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000042L)
            .traceId(0x0000000000000025L)
            .budgetId(0x0000000000000025L)
            .reserved(0x00000000)
            .payload(mqttSessionDataPayload, 0, mqttSessionDataPayload.capacity())
            .extension(mqttSessionDataEx1, 0, mqttSessionDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data22.buffer(), 0, data22.sizeof());

        DirectBuffer mqttSessionDataEx2 = new UnsafeBuffer(MqttFunctions.dataEx()
            .typeId(MQTT_TYPE_ID)
                .session()
                .deferred(88)
                .kind("WILL")
                .build()
            .build());
        DataFW data23 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000022L) // north_mqtt_server
            .routedId(0x0000000900000023L) // north_mqtt_kafka_mapping
            .streamId(0x0000000000000024L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000043L)
            .traceId(0x0000000000000025L)
            .budgetId(0x0000000000000025L)
            .reserved(0x00000000)
            .payload(mqttSessionDataPayload, 0, mqttSessionDataPayload.capacity())
            .extension(mqttSessionDataEx2, 0, mqttSessionDataEx2.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data23.buffer(), 0, data23.sizeof());

        // kafka extension
        // - CONSUMER
        DirectBuffer kafkaConsumerBeginEx1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .consumer()
                .groupId("group-id")
                .consumerId("consumer-id")
                .timeout(42)
                .topic("topic")
                .partition(21)
                .partition(33)
                .partition(77)
                .partition(88)
                .build()
            .build());
        BeginFW begin25 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000027L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000044L)
            .traceId(0x0000000000000027L)
            .affinity(0x0000000000000000L)
            .extension(kafkaConsumerBeginEx1, 0, kafkaConsumerBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin25.buffer(), 0, begin25.sizeof());

        DirectBuffer kafkaConsumerBeginEx2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .consumer()
                .groupId("group-id")
                .consumerId("consumer-id")
                .timeout(99)
                .topic("topic")
                .build()
            .build());
        BeginFW begin26 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000026L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000045L)
            .traceId(0x0000000000000027L)
            .affinity(0x0000000000000000L)
            .extension(kafkaConsumerBeginEx2, 0, kafkaConsumerBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin26.buffer(), 0, begin26.sizeof());

        DirectBuffer kafkaConsumerDataPayload = new String8FW("kafka consumer data payload").value();
        DirectBuffer kafkaConsumerDataEx1 = new UnsafeBuffer(KafkaFunctions.dataEx()
            .typeId(KAFKA_TYPE_ID)
            .consumer()
                .partition(33)
                .partition(44)
                .partition(55)
                .assignments()
                    .id("consumer-id-1")
                    .partition(101)
                    .partition(102)
                    .build()
                .assignments()
                    .id("consumer-id-2")
                    .partition(201)
                    .partition(202)
                    .build()
                .build()
            .build());
        DataFW data24 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000027L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000046L)
            .traceId(0x0000000000000027L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(kafkaConsumerDataPayload, 0, kafkaConsumerDataPayload.capacity())
            .extension(kafkaConsumerDataEx1, 0, kafkaConsumerDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data24.buffer(), 0, data24.sizeof());

        DirectBuffer kafkaConsumerFlushEx1 = new UnsafeBuffer(KafkaFunctions.flushEx()
            .typeId(KAFKA_TYPE_ID)
            .consumer()
                .progress(17, 21, "metadata")
                .leaderEpoch(42)
                .correlationId(77)
                .build()
            .build());
        FlushFW flush5 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000027L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000047L)
            .traceId(0x0000000000000027L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(kafkaConsumerFlushEx1, 0, kafkaConsumerFlushEx1.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush5.buffer(), 0, flush5.sizeof());

        DirectBuffer kafkaResetEx1 = new UnsafeBuffer(KafkaFunctions.resetEx()
            .typeId(KAFKA_TYPE_ID)
            .error(666)
            .consumerId("consumer-id")
            .build());
        ResetFW reset5 = resetRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000027L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000048L)
            .traceId(0x0000000000000027L)
            .extension(kafkaResetEx1, 0, kafkaResetEx1.capacity())
            .build();
        streams[0].write(ResetFW.TYPE_ID, reset5.buffer(), 0, reset5.sizeof());

        // - GROUP
        DirectBuffer kafkaGroupBeginEx1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .group()
                .groupId("group-id")
                .protocol("protocol")
                .instanceId("instance-id")
                .host("host")
                .port(42)
                .timeout(77)
                .metadata(BitUtil.fromHex("1122334455"))
                .build()
            .build());
        BeginFW begin27 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000029L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000049L)
            .traceId(0x0000000000000029L)
            .affinity(0x0000000000000000L)
            .extension(kafkaGroupBeginEx1, 0, kafkaGroupBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin27.buffer(), 0, begin27.sizeof());

        DirectBuffer kafkaGroupBeginEx2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .group()
                .groupId("group-id")
                .protocol("protocol")
                .instanceId("instance-id")
                .host("host")
                .port(42)
                .timeout(77)
                .build()
            .build());
        BeginFW begin28 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000028L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000004aL)
            .traceId(0x0000000000000029L)
            .affinity(0x0000000000000000L)
            .extension(kafkaGroupBeginEx2, 0, kafkaGroupBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin28.buffer(), 0, begin28.sizeof());

        DirectBuffer kafkaGroupFlushEx1 = new UnsafeBuffer(KafkaFunctions.flushEx()
            .typeId(KAFKA_TYPE_ID)
            .group()
                .generationId(77)
                .leaderId("leader-id")
                .memberId("member-id")
                .build()
            .build());
        FlushFW flush6 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000029L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000004bL)
            .traceId(0x0000000000000029L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(kafkaGroupFlushEx1, 0, kafkaGroupFlushEx1.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush6.buffer(), 0, flush6.sizeof());

        DirectBuffer kafkaGroupFlushEx2 = new UnsafeBuffer(KafkaFunctions.flushEx()
            .typeId(KAFKA_TYPE_ID)
            .group()
                .generationId(99)
                .leaderId("leader-id")
                .memberId("member-id")
                .members("member-1")
                .members("member-2-with-metadata", BitUtil.fromHex("778899aabb"))
                .members("member-3")
                .build()
            .build());
        FlushFW flush7 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000028L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000004cL)
            .traceId(0x0000000000000029L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(kafkaGroupFlushEx2, 0, kafkaGroupFlushEx2.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush7.buffer(), 0, flush7.sizeof());

        // - BOOTSTRAP
        DirectBuffer kafkaBootstrapBeginEx1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .bootstrap()
                .topic("topic")
                .groupId("group-id")
                .consumerId("consumer-id")
                // timeout omitted, should be 0
                .build()
            .build());
        BeginFW begin29 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000031L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000004dL)
            .traceId(0x0000000000000031L)
            .affinity(0x0000000000000000L)
            .extension(kafkaBootstrapBeginEx1, 0, kafkaBootstrapBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin29.buffer(), 0, begin29.sizeof());

        DirectBuffer kafkaBootstrapBeginEx2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .bootstrap()
                .topic("topic")
                .groupId("group-id")
                .consumerId("consumer-id")
                .timeout(999_999)
                .build()
            .build());
        BeginFW begin30 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000030L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000004eL)
            .traceId(0x0000000000000031L)
            .affinity(0x0000000000000000L)
            .extension(kafkaBootstrapBeginEx2, 0, kafkaBootstrapBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin30.buffer(), 0, begin30.sizeof());

        // - MERGED
        DirectBuffer kafkaMergedBeginEx1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .merged()
                .capabilities("PRODUCE_ONLY")
                .topic("topic")
                .groupId("group-id")
                .consumerId("consumer-id")
                // timeout omitted, should be 0
                .partition(42, 4242)
                .filter()
                    .key("key1")
                    .build()
                .filter()
                    .key("key1")
                    .key("key2")
                    .header("name1", "value1")
                    .header("name2", "value2")
                    .build()
                .filter()
                    .keyNot("key-n1")
                    .keyNot("key-n2")
                    .headerNot("name-n1", "value-n1")
                    .headerNot("name-n2", "value-n2")
                    .build()
                .filter()
                    .key("key")
                    .headers("headers-1")
                        .sequence("value-1", "value-2", "value-3")
                        .build()
                    .headers("headers-2")
                        .sequence("value-01")
                        .sequence("value-02")
                        .build()
                    .build()
                .filter()
                    .headers("headers-skip")
                        .sequence("value-s1")
                        .skip(1)
                        .build()
                    .headers("headers-skip-many")
                        .sequence("value-sm01")
                        .sequence("value-sm02")
                        .skipMany()
                        .build()
                    .build()
                .evaluation("LAZY")
                .isolation("READ_UNCOMMITTED")
                .deltaType("NONE")
                .ackMode("NONE")
                .build()
            .build());
        BeginFW begin31 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000033L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000004fL)
            .traceId(0x0000000000000033L)
            .affinity(0x0000000000000000L)
            .extension(kafkaMergedBeginEx1, 0, kafkaMergedBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin31.buffer(), 0, begin31.sizeof());

        DirectBuffer kafkaMergedBeginEx2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .merged()
                .capabilities("FETCH_ONLY")
                .topic("topic")
                .groupId("group-id")
                .consumerId("consumer-id")
                .timeout(42)
                .partition(1, 42_000, 43_000, 44_000, "metadata")
                .partition(2, 77_000)
                .partition(3, 88_000)
                // no filters
                .evaluation("EAGER")
                .isolation("READ_COMMITTED")
                .deltaType("JSON_PATCH")
                .ackMode("LEADER_ONLY")
                .build()
            .build());
        BeginFW begin32 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000032L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000050L)
            .traceId(0x0000000000000033L)
            .affinity(0x0000000000000000L)
            .extension(kafkaMergedBeginEx2, 0, kafkaMergedBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin32.buffer(), 0, begin32.sizeof());

        DirectBuffer kafkaMergedBeginEx3 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .merged()
                .capabilities("PRODUCE_AND_FETCH")
                .topic("topic")
                .groupId("group-id")
                .consumerId("consumer-id")
                .timeout(3600)
                .partition(42, 123_456)
                // no filters
                .evaluation("EAGER")
                .isolation("READ_COMMITTED")
                .deltaType("JSON_PATCH")
                .ackMode("IN_SYNC_REPLICAS")
                .build()
            .build());
        BeginFW begin33 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000032L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000051L)
            .traceId(0x0000000000000033L)
            .affinity(0x0000000000000000L)
            .extension(kafkaMergedBeginEx3, 0, kafkaMergedBeginEx3.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin33.buffer(), 0, begin33.sizeof());

        DirectBuffer kafkaMergedFetchDataPayload = new String8FW("kafka merged fetch data payload").value();
        DirectBuffer kafkaMergedFetchDataEx1 = new UnsafeBuffer(KafkaFunctions.dataEx()
            .typeId(KAFKA_TYPE_ID)
            .merged()
                .fetch()
                .deferred(99)
                .timestamp(0x52)
                .filters(77)
                .partition(1, 42_000)
                .progress(17, 42)
                .progress(19, 77, 2121)
                .key("key")
                .delta("JSON_PATCH", 7777)
                .header("name1", "value1")
                .header("name2", "value2")
                .build()
            .build());
        DataFW data25 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000033L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000052L)
            .traceId(0x0000000000000033L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(kafkaMergedFetchDataPayload, 0, kafkaMergedFetchDataPayload.capacity())
            .extension(kafkaMergedFetchDataEx1, 0, kafkaMergedFetchDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data25.buffer(), 0, data25.sizeof());

        DirectBuffer kafkaMergedProduceDataPayload = new String8FW("kafka merged produce data payload").value();
        DirectBuffer kafkaMergedProduceDataEx1 = new UnsafeBuffer(KafkaFunctions.dataEx()
            .typeId(KAFKA_TYPE_ID)
            .merged()
                .produce()
                .deferred(100)
                .timestamp(0x53)
                .partition(1, 77_000)
                .key("key")
                .hashKey("hash-key")
                .header("name1", "value1")
                .header("name2", "value2")
                .build()
            .build());
        DataFW data26 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000033L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000053L)
            .traceId(0x0000000000000033L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(kafkaMergedProduceDataPayload, 0, kafkaMergedProduceDataPayload.capacity())
            .extension(kafkaMergedProduceDataEx1, 0, kafkaMergedProduceDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data26.buffer(), 0, data26.sizeof());

        DirectBuffer kafkaMergedConsumerFlushEx = new UnsafeBuffer(KafkaFunctions.flushEx()
            .typeId(KAFKA_TYPE_ID)
            .merged()
                .consumer()
                .progress(17, 4242, "metadata")
                .correlationId(77)
                .build()
            .build());
        FlushFW flush8 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000033L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000054L)
            .traceId(0x0000000000000033L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(kafkaMergedConsumerFlushEx, 0, kafkaMergedConsumerFlushEx.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush8.buffer(), 0, flush8.sizeof());

        DirectBuffer kafkaMergedFetchFlushEx = new UnsafeBuffer(KafkaFunctions.flushEx()
            .typeId(KAFKA_TYPE_ID)
            .merged()
                .fetch()
                .partition(1, 42_000)
                .progress(17, 42)
                .progress(19, 77, 2121)
                .progress(21, 88, 1122, 3344)
                .capabilities("PRODUCE_AND_FETCH")
                .filter()
                    .key("filter-key1")
                    .build()
                .key("key")
                .build()
            .build());
        FlushFW flush9 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000033L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000055L)
            .traceId(0x0000000000000033L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(kafkaMergedFetchFlushEx, 0, kafkaMergedFetchFlushEx.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush9.buffer(), 0, flush9.sizeof());

        // - META
        DirectBuffer kafkaMetaBegin1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .meta()
                .topic("topic")
                .build()
            .build());
        BeginFW begin34 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000035L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000056L)
            .traceId(0x0000000000000035L)
            .affinity(0x0000000000000000L)
            .extension(kafkaMetaBegin1, 0, kafkaMetaBegin1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin34.buffer(), 0, begin34.sizeof());

        DirectBuffer kafkaMetaBegin2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .meta()
                .topic("topic")
                .build()
            .build());
        BeginFW begin35 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000034L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000057L)
            .traceId(0x0000000000000035L)
            .affinity(0x0000000000000000L)
            .extension(kafkaMetaBegin2, 0, kafkaMetaBegin2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin35.buffer(), 0, begin35.sizeof());

        DirectBuffer kafkaMetaDataPayload = new String8FW("kafka meta data payload").value();
        DirectBuffer kafkaMetaDataEx1 = new UnsafeBuffer(KafkaFunctions.dataEx()
            .typeId(KAFKA_TYPE_ID)
            .meta()
                .partition(1, 42)
                .partition(10, 420)
                .partition(100, 4200)
                .build()
            .build());
        DataFW data27 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000035L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000058L)
            .traceId(0x0000000000000035L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(kafkaMetaDataPayload, 0, kafkaMetaDataPayload.capacity())
            .extension(kafkaMetaDataEx1, 0, kafkaMetaDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data27.buffer(), 0, data27.sizeof());

        // - OFFSET_COMMIT
        DirectBuffer kafkaOffsetCommitBegin1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .offsetCommit()
                .groupId("group")
                .memberId("member")
                .instanceId("instance")
                .build()
            .build());
        BeginFW begin36 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000037L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000059L)
            .traceId(0x0000000000000037L)
            .affinity(0x0000000000000000L)
            .extension(kafkaOffsetCommitBegin1, 0, kafkaOffsetCommitBegin1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin36.buffer(), 0, begin36.sizeof());

        DirectBuffer kafkaOffsetCommitBegin2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .offsetCommit()
                .groupId("group")
                .memberId("member")
                .instanceId("instance")
                .build()
            .build());
        BeginFW begin37 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000036L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000005aL)
            .traceId(0x0000000000000037L)
            .affinity(0x0000000000000000L)
            .extension(kafkaOffsetCommitBegin2, 0, kafkaOffsetCommitBegin2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin37.buffer(), 0, begin37.sizeof());

        DirectBuffer kafkaOffsetCommitDataPayload = new String8FW("kafka offset commit data payload").value();
        DirectBuffer kafkaOffsetCommitDataEx1 = new UnsafeBuffer(KafkaFunctions.dataEx()
            .typeId(KAFKA_TYPE_ID)
            .offsetCommit()
                .topic("test")
                .progress(21, 1234, "metadata")
                .generationId(42)
                .leaderEpoch(77)
                .build()
            .build());
        DataFW data28 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000037L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000005bL)
            .traceId(0x0000000000000037L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(kafkaOffsetCommitDataPayload, 0, kafkaOffsetCommitDataPayload.capacity())
            .extension(kafkaOffsetCommitDataEx1, 0, kafkaOffsetCommitDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data28.buffer(), 0, data28.sizeof());

        // - OFFSET_FETCH
        DirectBuffer kafkaOffsetFetchBegin1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .offsetFetch()
                .groupId("group")
                .host("host")
                .port(42)
                .topic("topic")
                .partition(21)
                .partition(42)
                .partition(77)
                .partition(88)
                .build()
            .build());
        BeginFW begin38 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000039L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000005cL)
            .traceId(0x0000000000000039L)
            .affinity(0x0000000000000000L)
            .extension(kafkaOffsetFetchBegin1, 0, kafkaOffsetFetchBegin1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin38.buffer(), 0, begin38.sizeof());

        DirectBuffer kafkaOffsetFetchBegin2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .offsetFetch()
                .groupId("group")
                .host("host")
                .port(42)
                .topic("topic")
                .partition(42)
                .build()
            .build());
        BeginFW begin39 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000038L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000005dL)
            .traceId(0x0000000000000039L)
            .affinity(0x0000000000000000L)
            .extension(kafkaOffsetFetchBegin2, 0, kafkaOffsetFetchBegin2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin39.buffer(), 0, begin39.sizeof());

        DirectBuffer kafkaOffsetFetchDataPayload = new String8FW("kafka offset fetch data payload").value();
        DirectBuffer kafkaOffsetFetchDataEx1 = new UnsafeBuffer(KafkaFunctions.dataEx()
            .typeId(KAFKA_TYPE_ID)
                .offsetFetch()
                .partition(17, 21, 42, "metadata1")
                .partition(18, 22, 43, "metadata2")
                .partition(19, 23, 44, "metadata3")
                .build()
            .build());
        DataFW data29 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x0000000000000039L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000005eL)
            .traceId(0x0000000000000039L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(kafkaOffsetFetchDataPayload, 0, kafkaOffsetFetchDataPayload.capacity())
            .extension(kafkaOffsetFetchDataEx1, 0, kafkaOffsetFetchDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data29.buffer(), 0, data29.sizeof());

        // - DESCRIBE
        DirectBuffer kafkaDescribeBegin1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .describe()
            .topic("topic")
                .config("config1")
                .config("config2")
                .config("config3")
                .build()
            .build());
        BeginFW begin40 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003bL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000005fL)
            .traceId(0x000000000000003bL)
            .affinity(0x0000000000000000L)
            .extension(kafkaDescribeBegin1, 0, kafkaDescribeBegin1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin40.buffer(), 0, begin40.sizeof());

        DirectBuffer kafkaDescribeBegin2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .describe()
                .topic("topic")
            // configs omitted
            .build()
            .build());
        BeginFW begin41 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003aL) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000060L)
            .traceId(0x000000000000003bL)
            .affinity(0x0000000000000000L)
            .extension(kafkaDescribeBegin2, 0, kafkaDescribeBegin2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin41.buffer(), 0, begin41.sizeof());

        DirectBuffer kafkaDescribeDataPayload = new String8FW("kafka describe payload").value();
        DirectBuffer kafkaDescribeDataEx1 = new UnsafeBuffer(KafkaFunctions.dataEx()
            .typeId(KAFKA_TYPE_ID)
            .describe()
                .config("name1", "value1")
                .config("name2", "value2")
                .config("name3", "value3")
                .build()
            .build());
        DataFW data30 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003bL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000061L)
            .traceId(0x000000000000003bL)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(kafkaDescribeDataPayload, 0, kafkaDescribeDataPayload.capacity())
            .extension(kafkaDescribeDataEx1, 0, kafkaDescribeDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data30.buffer(), 0, data30.sizeof());

        // - FETCH
        DirectBuffer kafkaFetchBegin1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .fetch()
                .topic("topic")
                .partition(42, 4242)
                .filter()
                    .key("key1")
                    .build()
                .filter()
                    .key("key1")
                    .key("key2")
                    .header("name1", "value1")
                    .header("name2", "value2")
                    .build()
                .evaluation("LAZY")
                .isolation("READ_UNCOMMITTED")
                .deltaType("NONE")
                .build()
            .build());
        BeginFW begin42 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003dL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000062L)
            .traceId(0x000000000000003dL)
            .affinity(0x0000000000000000L)
            .extension(kafkaFetchBegin1, 0, kafkaFetchBegin1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin42.buffer(), 0, begin42.sizeof());

        DirectBuffer kafkaFetchBegin2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .fetch()
                .topic("topic")
                .partition(21, 2121)
                .filter()
                    .key("key1")
                    .build()
                .evaluation("EAGER")
                .isolation("READ_COMMITTED")
                .deltaType("JSON_PATCH")
                .build()
            .build());
        BeginFW begin43 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003cL) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000063L)
            .traceId(0x000000000000003dL)
            .affinity(0x0000000000000000L)
            .extension(kafkaFetchBegin2, 0, kafkaFetchBegin2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin43.buffer(), 0, begin43.sizeof());

        DirectBuffer kafkaFetchDataPayload = new String8FW("kafka fetch payload").value();
        DirectBuffer kafkaFetchDataEx1 = new UnsafeBuffer(KafkaFunctions.dataEx()
            .typeId(KAFKA_TYPE_ID)
            .fetch()
                .deferred(7777)
                .timestamp(0x64)
                .producerId(0x12345678)
                .filters(77)
                .partition(1, 42_000)
                .key("key")
                .delta("JSON_PATCH", 7777)
                .header("name1", "value1")
                .header("name2", "value2")
                .build()
            .build());
        DataFW data31 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003dL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000064L)
            .traceId(0x000000000000003dL)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(kafkaFetchDataPayload, 0, kafkaFetchDataPayload.capacity())
            .extension(kafkaFetchDataEx1, 0, kafkaFetchDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data31.buffer(), 0, data31.sizeof());

        DirectBuffer kafkaFetchFlushEx = new UnsafeBuffer(KafkaFunctions.flushEx()
            .typeId(KAFKA_TYPE_ID)
            .fetch()
                .partition(21, 2121)
                .transaction("ABORT", 0x6666)
                .transaction("COMMIT", 0x4277)
                .filter()
                    .key("key1")
                    .build()
            .build()
            .build());
        FlushFW flush10 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003dL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000065L)
            .traceId(0x000000000000003dL)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(kafkaFetchFlushEx, 0, kafkaFetchFlushEx.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush10.buffer(), 0, flush10.sizeof());

        // - PRODUCE
        DirectBuffer kafkaProduceBegin1 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .produce()
                .transaction("transaction")
                .topic("topic")
                .partition(2, 42_000, 77_000)
                .build()
            .build());
        BeginFW begin44 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003fL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000066L)
            .traceId(0x000000000000003fL)
            .affinity(0x0000000000000000L)
            .extension(kafkaProduceBegin1, 0, kafkaProduceBegin1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin44.buffer(), 0, begin44.sizeof());

        DirectBuffer kafkaProduceBegin2 = new UnsafeBuffer(KafkaFunctions.beginEx()
            .typeId(KAFKA_TYPE_ID)
            .produce()
                .transaction("transaction")
                .topic("topic")
                .partition(1, 21_000)
                .build()
            .build());
        BeginFW begin45 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003eL) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000067L)
            .traceId(0x000000000000003fL)
            .affinity(0x0000000000000000L)
            .extension(kafkaProduceBegin2, 0, kafkaProduceBegin2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin45.buffer(), 0, begin45.sizeof());

        DirectBuffer kafkaProduceDataPayload = new String8FW("kafka produce payload").value();
        DirectBuffer kafkaProduceDataEx1 = new UnsafeBuffer(KafkaFunctions.dataEx()
            .typeId(KAFKA_TYPE_ID)
            .produce()
                .deferred(999)
                .timestamp(0x68)
                .sequence(777)
                .ackMode("LEADER_ONLY")
                .key("key")
                .header("name1", "value1")
                .header("name2", "value2")
                .build()
            .build());
        DataFW data32 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003fL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000068L)
            .traceId(0x000000000000003fL)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(kafkaProduceDataPayload, 0, kafkaProduceDataPayload.capacity())
            .extension(kafkaProduceDataEx1, 0, kafkaProduceDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data32.buffer(), 0, data32.sizeof());

        DirectBuffer kafkaProduceFlushEx = new UnsafeBuffer(KafkaFunctions.flushEx()
            .typeId(KAFKA_TYPE_ID)
            .produce()
                .partition(2, 42_000, 77_000)
                .key("key")
                .build()
            .build());
        FlushFW flush11 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x000000090000000fL) // north_kafka_cache_client
            .routedId(0x0000000900000010L) // south_kafka_cache_server
            .streamId(0x000000000000003fL) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000069L)
            .traceId(0x000000000000003fL)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(kafkaProduceFlushEx, 0, kafkaProduceFlushEx.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush11.buffer(), 0, flush11.sizeof());

        // amqp extension
        DirectBuffer amqpBeginEx1 = new UnsafeBuffer(AmqpFunctions.beginEx()
            .typeId(AMQP_TYPE_ID)
            .address("address")
            .capabilities("SEND_AND_RECEIVE")
            .senderSettleMode("SETTLED")
            .receiverSettleMode("FIRST")
            .build());
        BeginFW begin46 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000025L) // north_amqp_server
            .routedId(0x0000000900000026L) // north_fan_server
            .streamId(0x0000000000000041L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000006aL)
            .traceId(0x0000000000000041L)
            .affinity(0x0000000000000000L)
            .extension(amqpBeginEx1, 0, amqpBeginEx1.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin46.buffer(), 0, begin46.sizeof());

        DirectBuffer amqpBeginEx2 = new UnsafeBuffer(AmqpFunctions.beginEx()
            .typeId(AMQP_TYPE_ID)
            .address("address")
            .capabilities("SEND_ONLY")
            .senderSettleMode("MIXED")
            .receiverSettleMode("SECOND")
            .build());
        BeginFW begin47 = beginRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000025L) // north_amqp_server
            .routedId(0x0000000900000026L) // north_fan_server
            .streamId(0x0000000000000040L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000006bL)
            .traceId(0x0000000000000041L)
            .affinity(0x0000000000000000L)
            .extension(amqpBeginEx2, 0, amqpBeginEx2.capacity())
            .build();
        streams[0].write(BeginFW.TYPE_ID, begin47.buffer(), 0, begin47.sizeof());

        DirectBuffer amqpPayload = new String8FW("amqp payload").value();
        DirectBuffer amqpDataEx1 = new UnsafeBuffer(AmqpFunctions.dataEx()
            .typeId(AMQP_TYPE_ID)
            .deliveryTag("delivery-tag")
            .messageFormat(7777)
            .flags("BATCHABLE")
            // annotations:
            .annotation("annotation1", "value1".getBytes(StandardCharsets.UTF_8))
            .annotation(0x8888L, "value2".getBytes(StandardCharsets.UTF_8))
            // properties:
            .messageId("message-id")
            .to("to")
            .correlationId("correlation-id")
            // application properties:
            .property("app-property1", "value1".getBytes(StandardCharsets.UTF_8))
            .property("app-property2", "value2".getBytes(StandardCharsets.UTF_8))
            .bodyKind("VALUE")
            .deferred(9999)
            .build());
        DataFW data33 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000025L) // north_amqp_server
            .routedId(0x0000000900000026L) // north_fan_server
            .streamId(0x0000000000000041L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000006cL)
            .traceId(0x0000000000000041L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(amqpPayload, 0, amqpPayload.capacity())
            .extension(amqpDataEx1, 0, amqpDataEx1.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data33.buffer(), 0, data33.sizeof());

        DirectBuffer amqpDataEx2 = new UnsafeBuffer(AmqpFunctions.dataEx()
            .typeId(AMQP_TYPE_ID)
            .deliveryTag("delivery-tag")
            .messageFormat(1111)
            .flags("BATCHABLE", "ABORTED", "RESUME", "SETTLED")
            // annotations:
            .annotation("annotation1", "value1".getBytes(StandardCharsets.UTF_8))
            .annotation(0x2222L, "value2".getBytes(StandardCharsets.UTF_8))
            // properties:
            .messageId(0x77L)
            .userId("user-id")
            .to("to")
            .subject("subject")
            .replyTo("reply-to")
            .correlationId(0x88L)
            .contentType("content-type")
            .contentEncoding("content-encoding")
            .absoluteExpiryTime(123_456)
            .creationTime(654_321)
            .groupId("group-id")
            .groupSequence(456_789)
            .replyToGroupId("reply-to-group-id")
            // application properties:
            .property("app-property1", "value1".getBytes(StandardCharsets.UTF_8))
            .property("app-property2", "value2".getBytes(StandardCharsets.UTF_8))
            .bodyKind("VALUE_STRING32")
            .deferred(3333)
            .build());
        DataFW data34 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000025L) // north_amqp_server
            .routedId(0x0000000900000026L) // north_fan_server
            .streamId(0x0000000000000040L) // REP
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000006dL)
            .traceId(0x0000000000000042L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(amqpPayload, 0, amqpPayload.capacity())
            .extension(amqpDataEx2, 0, amqpDataEx2.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data34.buffer(), 0, data34.sizeof());

        DirectBuffer amqpDataEx3 = new UnsafeBuffer(AmqpFunctions.dataEx()
            .typeId(AMQP_TYPE_ID)
            .deliveryTag("delivery-tag")
            .messageFormat(2222)
            .flags("BATCHABLE", "ABORTED", "RESUME", "SETTLED")
            // annotations:
            .annotation("annotation1", "value1".getBytes(StandardCharsets.UTF_8))
            .annotation(0x3333L, "value2".getBytes(StandardCharsets.UTF_8))
            // properties:
            .messageId("message-id".getBytes(StandardCharsets.UTF_8))
            .replyTo("reply-to")
            .correlationId("correlation-id".getBytes(StandardCharsets.UTF_8))
            .contentType("content-type")
            .contentEncoding("content-encoding")
            .groupId("group-id")
            .replyToGroupId("reply-to-group-id")
            // application properties:
            .property("app-property1", "value1".getBytes(StandardCharsets.UTF_8))
            .property("app-property2", "value2".getBytes(StandardCharsets.UTF_8))
            .bodyKind("VALUE_STRING32")
            .deferred(4444)
            .build());
        DataFW data35 = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000025L) // north_amqp_server
            .routedId(0x0000000900000026L) // north_fan_server
            .streamId(0x0000000000000041L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000006eL)
            .traceId(0x0000000000000042L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .payload(amqpPayload, 0, amqpPayload.capacity())
            .extension(amqpDataEx3, 0, amqpDataEx3.capacity())
            .build();
        streams[0].write(DataFW.TYPE_ID, data35.buffer(), 0, data35.sizeof());

        DirectBuffer amqpFlushEx = new UnsafeBuffer(new byte[]{
            AMQP_TYPE_ID, 0, 0, 0,  // int32 typeId
            3                       // uint8 AmqpCapabilities
        });
        FlushFW flush12 = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000025L) // north_amqp_server
            .routedId(0x0000000900000026L) // north_fan_server
            .streamId(0x0000000000000041L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x000000000000006fL)
            .traceId(0x0000000000000041L)
            .budgetId(0x0000000000000000L)
            .reserved(0x00000000)
            .extension(amqpFlushEx, 0, amqpFlushEx.capacity())
            .build();
        streams[0].write(FlushFW.TYPE_ID, flush12.buffer(), 0, flush12.sizeof());

        DirectBuffer amqpAbortEx = new UnsafeBuffer(AmqpFunctions.abortEx()
            .typeId(AMQP_TYPE_ID)
            .condition("condition")
            .build());
        AbortFW abort3 = abortRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0x0000000900000025L) // north_amqp_server
            .routedId(0x0000000900000026L) // north_fan_server
            .streamId(0x0000000000000041L) // INI
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .timestamp(0x0000000000000070L)
            .traceId(0x0000000000000041L)
            .extension(amqpAbortEx, 0, amqpAbortEx.capacity())
            .build();
        streams[0].write(AbortFW.TYPE_ID, abort3.buffer(), 0, abort3.sizeof());
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
        assertThat(files.length, equalTo(1));
        byte[] actual = Files.readAllBytes(files[0].toPath());
        assertThat(hexDump(actual), equalTo(hexDump(expected)));
    }

    @Test
    public void shouldWriteFilteredPcap() throws IOException
    {
        // GIVEN
        byte[] expected = getResourceAsBytes("expected_filtered_dump.pcap");

        // WHEN
        command.bindings = singletonList("example.north_http_kafka_mapping");
        command.run();

        // THEN
        File[] files = tempDir.listFiles();
        assert files != null;
        assertThat(files.length, equalTo(1));
        byte[] actual = Files.readAllBytes(files[0].toPath());
        assertThat(hexDump(actual), equalTo(hexDump(expected)));
    }

    private static String hexDump(
        byte[] bytes)
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            HexDump.dump(bytes, 0, baos, 0);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        return baos.toString();
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
