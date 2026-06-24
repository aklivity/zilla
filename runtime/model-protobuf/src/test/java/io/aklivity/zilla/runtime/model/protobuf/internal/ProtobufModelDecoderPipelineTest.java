/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufModelDecoderPipelineTest
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String SCHEMA = """
                                            syntax = "proto3";
                                            package io.aklivity.examples.clients.proto;
                                            message SimpleMessage {
                                                string content = 1;
                                                optional string date_time = 2;
                                            }
                                            """;

    private static final String COMPLEX_SCHEMA = """
                                                    syntax = "proto3";
                                                    package io.confluent.examples.clients.basicavro;
                                                    message SimpleMessage {
                                                        double field_double = 1;
                                                        float field_float = 2;
                                                        int64 field_int64 = 3;
                                                        uint64 field_uint64 = 4;
                                                        int32 field_int32 = 5;
                                                        fixed64 field_fixed64 = 6;
                                                        fixed32 field_fixed32 = 7;
                                                        string field_string = 8;
                                                        bytes field_bytes = 9;
                                                        uint32 field_uint32 = 10;
                                                        sfixed32 field_sfixed32 = 12;
                                                        sfixed64 field_sfixed64 = 13;
                                                        sint32 field_sint32 = 14;
                                                        sint64 field_sint64 = 15;
                                                    }
                                                    """;

    // message index 0, then content="OK" (field 1) and date_time="01012024" (field 2)
    private static final byte[] WIRE =
        {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
    // the bare wire payload, after stripping the single index byte
    private static final byte[] PAYLOAD =
        {0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
    private static final String JSON = "{\"content\":\"OK\",\"date_time\":\"01012024\"}";

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldTransformWholeValueToJson()
    {
        ProtobufModelHandlerImpl handler = newHandler("json");
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(WIRE.length, result.consumed());
        assertEquals(JSON, text(dst, result.produced()));

        // reset and reuse the same pipeline for the next value
        pipeline.reset();
        ModelPipelineResult reused = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, reused.status());
        assertEquals(JSON, text(dst, reused.produced()));
    }

    @Test
    public void shouldTransformWholeValueToWire()
    {
        ProtobufModelHandlerImpl handler = newHandler(null);
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(WIRE.length, result.consumed());
        byte[] out = new byte[result.produced()];
        dst.getBytes(0, out);
        assertArrayEquals(PAYLOAD, out);
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        ProtobufModelHandlerImpl handler = newHandler(null);
        // two per-stream pipelines from the same per-worker handler
        ModelPipeline a = handler.supplyDecoder(ModelVisitor.NONE);
        ModelPipeline b = handler.supplyDecoder(ModelVisitor.NONE);

        // stream A split at the field boundary: index byte + content field first, date_time on the final fragment
        byte[] a1 = {0x00, 0x0a, 0x02, 0x4f, 0x4b};
        byte[] a2tail = {0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ByteArrayOutputStream outA = new ByteArrayOutputStream();

        // stream A: first fragment, incomplete -> UNDERFLOW
        ModelPipelineResult ra1 = a.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBuffer(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());
        drain(dst, ra1.produced(), outA);

        // stream B: a whole value fed in the middle of A — would corrupt A if state were shared
        ModelPipelineResult rb = b.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());
        byte[] outB = new byte[rb.produced()];
        dst.getBytes(0, outB);
        assertArrayEquals(PAYLOAD, outB);

        // stream A: finish, prepending A's unconsumed remainder (the caller's decode-slot residue)
        byte[] a2 = concat(a1, ra1.consumed(), a2tail);
        ModelPipelineResult ra2 = a.transform(0L, 0L, FLAGS_FIN,
            new UnsafeBuffer(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
        drain(dst, ra2.produced(), outA);

        assertArrayEquals(PAYLOAD, outA.toByteArray());
    }

    @Test
    public void shouldExtractField()
    {
        ProtobufModelHandlerImpl handler = newHandler(null);

        Map<String, String> extracted = new HashMap<>();
        ModelVisitor visitor = (path, buffer, index, length) ->
            extracted.put(path, buffer.getStringWithoutLengthUtf8(index, length));
        ModelPipeline pipeline = handler.supplyDecoder(visitor);

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("OK", extracted.get("$.content"));
        assertEquals("01012024", extracted.get("$.date_time"));
    }

    @Test
    public void shouldExtractScalarFields()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(COMPLEX_SCHEMA)
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .build()
                .build()
            .build();
        ProtobufModelHandlerImpl handler = new ProtobufModelHandlerImpl(model, context);

        Map<String, String> extracted = new HashMap<>();
        ModelVisitor visitor = (path, buffer, index, length) ->
            extracted.put(path, buffer.getStringWithoutLengthUtf8(index, length));
        ModelPipeline pipeline = handler.supplyDecoder(visitor);

        // leading message index 0, then the complex message's scalar fields (matches the legacy extract case)
        byte[] wire = {0, 9, 119, -66, -97, 26, 47, -35, 94, 64, 21, 102, -26, -11, 66, 24, -107, -102, -17, 58,
            32, -79, -47, -7, -42, 3, 40, -71, 96, 49, 21, -51, 91, 7, 0, 0, 0, 0, 61, 57, 48, 0, 0, 66, 12, 100,
            117, 109, 109, 121, 32, 115, 116, 114, 105, 110, 103, 74, 5, 1, 2, 3, 4, 5, 80, -78, -110, 4, 101, 57,
            48, 0, 0, 105, 21, -51, 91, 7, 0, 0, 0, 0, 112, -28, -92, 8, 120, -30, -94, -13, -83, 7};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(wire), 0, wire.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("dummy string", extracted.get("$.field_string"));
        assertEquals("123.456", extracted.get("$.field_double"));
        assertEquals("12345", extracted.get("$.field_int32"));
        assertEquals("122.95", extracted.get("$.field_float"));
        assertEquals("123456789", extracted.get("$.field_int64"));
    }

    @Test
    public void shouldDrainJsonOnOverflow()
    {
        ProtobufModelHandlerImpl handler = newHandler("json");
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        // a 2000-byte content field forces the JSON output past a small destination window, exercising the
        // bounded-chunk OVERFLOW drain across re-transforms (INIT cleared on every re-call after the first)
        String content = "A".repeat(2000);
        byte[] contentBytes = content.getBytes(UTF_8);
        byte[] dateBytes = "01012024".getBytes(UTF_8);
        byte[] wire = new byte[contentBytes.length + 16];
        MutableDirectBuffer builder = new UnsafeBuffer(wire);
        int p = 0;
        builder.putByte(p++, (byte) 0x00);                  // message index 0
        builder.putByte(p++, (byte) 0x0a);                  // field 1 (content), wire type LEN
        builder.putByte(p++, (byte) 0xd0);                  // length 2000 varint, byte 0
        builder.putByte(p++, (byte) 0x0f);                  // length 2000 varint, byte 1
        builder.putBytes(p, contentBytes);
        p += contentBytes.length;
        builder.putByte(p++, (byte) 0x12);                  // field 2 (date_time), wire type LEN
        builder.putByte(p++, (byte) 0x08);                  // length 8
        builder.putBytes(p, dateBytes);
        p += dateBytes.length;

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[512]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int flags = FLAGS_COMPLETE;
        ModelPipelineResult result;
        int guard = 0;
        do
        {
            result = pipeline.transform(0L, 0L, flags, new UnsafeBuffer(wire), 0, p, dst, 0, dst.capacity());
            drain(dst, result.produced(), out);
            flags = FLAGS_FIN;
            guard++;
        }
        while (result.status() == ModelStatus.OVERFLOW && guard < 1000);

        assertEquals(ModelStatus.COMPLETE, result.status());
        String json = "{\"content\":\"" + content + "\",\"date_time\":\"01012024\"}";
        assertEquals(json, out.toString(UTF_8));
    }

    @Test
    public void shouldRejectInvalid()
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        ProtobufModelHandlerImpl handler = newHandler(null);
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        // index byte, then content length prefix promises 8 bytes but only "OK" follows under FLAGS_COMPLETE
        byte[] invalid = {0x00, 0x0a, 0x08, 0x4f, 0x4b};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(invalid), 0, invalid.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldReportIdentityWhenNoView()
    {
        ProtobufModelHandlerImpl handler = newHandler(null);
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        assertFalse(pipeline.identity());

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertTrue(pipeline.identity());
    }

    @Test
    public void shouldNotReportIdentityWhenJsonView()
    {
        ProtobufModelHandlerImpl handler = newHandler("json");
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertFalse(pipeline.identity());
    }

    private ProtobufModelHandlerImpl newHandler(
        String view)
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();
        var builder = ProtobufModelConfig.builder();
        if (view != null)
        {
            builder.view(view);
        }
        ProtobufModelConfig model = builder
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new ProtobufModelHandlerImpl(model, context);
    }

    private static byte[] concat(
        byte[] head,
        int headOffset,
        byte[] tail)
    {
        int headLength = head.length - headOffset;
        byte[] result = new byte[headLength + tail.length];
        System.arraycopy(head, headOffset, result, 0, headLength);
        System.arraycopy(tail, 0, result, headLength, tail.length);
        return result;
    }

    private static void drain(
        MutableDirectBuffer dst,
        int produced,
        ByteArrayOutputStream sink)
    {
        byte[] chunk = new byte[produced];
        dst.getBytes(0, chunk);
        sink.writeBytes(chunk);
    }

    private static String text(
        MutableDirectBuffer dst,
        int produced)
    {
        byte[] chunk = new byte[produced];
        dst.getBytes(0, chunk);
        return new String(chunk, UTF_8);
    }
}
