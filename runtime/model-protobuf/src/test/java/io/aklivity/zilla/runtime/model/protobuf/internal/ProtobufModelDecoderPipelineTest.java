/*
 * Copyright 2021-2026 Aklivity Inc
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
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.protobuf.ProtobufModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransformable;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufCache;
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufModelExtContext;
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufModelExtHandler;
import io.aklivity.zilla.runtime.model.protobuf.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.model.protobuf.internal.types.event.ProtobufModelEventExFW;
import io.aklivity.zilla.runtime.model.protobuf.internal.types.event.ProtobufModelEventType;

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
    public void shouldIsolateInterleavedStreams()
    {
        ProtobufModelHandlerImpl handler = newHandler(null);
        // two per-stream pipelines from the same per-worker handler
        ModelPipeline a = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);
        ModelPipeline b = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        // stream A split at the field boundary: index byte + content field first, date_time on the final fragment
        byte[] a1 = {0x00, 0x0a, 0x02, 0x4f, 0x4b};
        byte[] a2tail = {0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ByteArrayOutputStream outA = new ByteArrayOutputStream();

        // stream A: first fragment, incomplete -> UNDERFLOW
        ModelPipelineResult ra1 = a.transform(0L, 0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());
        drain(dst, ra1.produced(), outA);

        // stream B: a whole value fed in the middle of A — would corrupt A if state were shared
        ModelPipelineResult rb = b.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(WIRE), 0, WIRE.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());
        byte[] outB = new byte[rb.produced()];
        dst.getBytes(0, outB);
        assertArrayEquals(PAYLOAD, outB);

        // stream A: finish, prepending A's unconsumed remainder (the caller's decode-slot residue)
        byte[] a2 = concat(a1, ra1.consumed(), a2tail);
        ModelPipelineResult ra2 = a.transform(0L, 0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
        drain(dst, ra2.produced(), outA);

        assertArrayEquals(PAYLOAD, outA.toByteArray());
    }

    @Test
    public void shouldExtractField()
    {
        ProtobufModelHandlerImpl handler = newHandler(null);

        Map<String, String> extracted = new HashMap<>();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, observer(extracted), ModelCache.NONE);

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("OK", extracted.get("$.content"));
        assertEquals("01012024", extracted.get("$.date_time"));
    }

    @Test
    public void shouldExtractScalarFields()
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
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
        ProtobufModelHandlerImpl handler = new ProtobufModelHandlerImpl(model, context, List.of());

        Map<String, String> extracted = new HashMap<>();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, observer(extracted), ModelCache.NONE);

        // leading message index 0, then the complex message's scalar fields (matches the legacy extract case)
        byte[] wire = {0, 9, 119, -66, -97, 26, 47, -35, 94, 64, 21, 102, -26, -11, 66, 24, -107, -102, -17, 58,
            32, -79, -47, -7, -42, 3, 40, -71, 96, 49, 21, -51, 91, 7, 0, 0, 0, 0, 61, 57, 48, 0, 0, 66, 12, 100,
            117, 109, 109, 121, 32, 115, 116, 114, 105, 110, 103, 74, 5, 1, 2, 3, 4, 5, 80, -78, -110, 4, 101, 57,
            48, 0, 0, 105, 21, -51, 91, 7, 0, 0, 0, 0, 112, -28, -92, 8, 120, -30, -94, -13, -83, 7};
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(wire), 0, wire.length, dst, 0, dst.capacity());

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
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        // a 2000-byte content field forces the JSON output past a small destination window, exercising the
        // bounded-chunk OVERFLOW drain across re-transforms (INIT cleared on every re-call after the first)
        String content = "A".repeat(2000);
        byte[] contentBytes = content.getBytes(UTF_8);
        byte[] dateBytes = "01012024".getBytes(UTF_8);
        byte[] wire = new byte[contentBytes.length + 16];
        MutableDirectBufferEx builder = new UnsafeBufferEx(wire);
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

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[512]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int flags = FLAGS_COMPLETE;
        ModelPipelineResult result;
        int guard = 0;
        do
        {
            result = pipeline.transform(0L, 0L, 0L, flags, new UnsafeBufferEx(wire), 0, p, dst, 0, dst.capacity());
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
    public void shouldReportIdentityWhenNoView()
    {
        ProtobufModelHandlerImpl handler = newHandler(null);
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        assertFalse(pipeline.identity());

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertTrue(pipeline.identity());
    }

    @Test
    public void shouldNotReportIdentityWhenJsonView()
    {
        ProtobufModelHandlerImpl handler = newHandler("json");
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertFalse(pipeline.identity());
    }

    @Test
    public void shouldRoundTripJsonViewThroughWriteThenRead()
    {
        ProtobufModelHandlerImpl handler = newHandler("json");

        ModelPipeline writer = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.WRITE);
        MutableDirectBufferEx cached = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult written = writer.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(WIRE), 0, WIRE.length, cached, 0, cached.capacity());

        assertEquals(ModelStatus.COMPLETE, written.status());
        assertEquals(JSON, text(cached, written.produced()));

        // a reader fetching the value WRITE just cached: its source must parse the cached json,
        // not protobuf wire bytes, since that is the only form the cache holds
        ModelPipeline reader = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.READ);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        byte[] cachedJson = JSON.getBytes(UTF_8);
        ModelPipelineResult read = reader.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(cachedJson), 0, cachedJson.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, read.status());
        assertEquals(JSON, text(dst, read.produced()));
    }

    @Test
    public void shouldRoundTripWireBytesThroughWriteThenRead()
    {
        ProtobufModelHandlerImpl handler = newHandler(null);

        ModelPipeline writer = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.WRITE);
        MutableDirectBufferEx cached = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult written = writer.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(WIRE), 0, WIRE.length, cached, 0, cached.capacity());

        assertEquals(ModelStatus.COMPLETE, written.status());
        byte[] cachedBytes = new byte[written.produced()];
        cached.getBytes(0, cachedBytes);
        // WRITE's decode output never carries message-index framing, view or no view -- confirm READ's
        // static message resolution (proven above for the json-view case) applies here too
        assertArrayEquals(PAYLOAD, cachedBytes);

        ModelPipeline reader = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.READ);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult read = reader.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(cachedBytes), 0, cachedBytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, read.status());
        byte[] readBytes = new byte[read.produced()];
        dst.getBytes(0, readBytes);
        assertArrayEquals(cachedBytes, readBytes);
    }

    @Test
    public void shouldRoundTripEncodedFramingThroughWriteThenRead()
    {
        // simulates strategy: encoded -- the wire value carries real catalog framing (a magic-byte-style
        // prefix) embedding the schema id, and the model's own static catalog reference is deliberately
        // wrong (no schema resolves to that id), so decode can only succeed by resolving the schema id from
        // the framing itself, on both WRITE (from the original wire bytes) and READ (from what WRITE cached)
        byte[] prefixBytes = "XX".getBytes(UTF_8);
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .prefix("XX")
                .build()
            .build();
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .id(999)
                    .record("SimpleMessage")
                    .build()
                .build()
            .build();
        EngineContext ctx = mock(EngineContext.class);
        when(ctx.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        ProtobufModelHandlerImpl handler = new ProtobufModelHandlerImpl(model, ctx, List.of());

        byte[] wire = concat(prefixBytes, 0, WIRE);
        ModelPipeline writer = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.WRITE);
        MutableDirectBufferEx cached = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult written = writer.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(wire), 0, wire.length, cached, 0, cached.capacity());

        assertEquals(ModelStatus.COMPLETE, written.status());
        byte[] cachedBytes = new byte[written.produced()];
        cached.getBytes(0, cachedBytes);
        byte[] cachedPayload = new byte[cachedBytes.length - prefixBytes.length];
        System.arraycopy(cachedBytes, prefixBytes.length, cachedPayload, 0, cachedPayload.length);
        assertArrayEquals(PAYLOAD, cachedPayload);

        ModelPipeline reader = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.READ);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult read = reader.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(cachedBytes), 0, cachedBytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, read.status());
        byte[] readBytes = new byte[read.produced()];
        dst.getBytes(0, readBytes);
        assertArrayEquals(PAYLOAD, readBytes);
    }

    @Test
    public void shouldIncludeExtensionPaddingContribution()
    {
        ProtobufModelHandlerImpl baseline = newHandler("json", List.of());
        ProtobufModelHandlerImpl extended = newHandler("json", List.of(expandingExt(64)));

        int basePadding = baseline.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE)
            .padding(new UnsafeBufferEx(WIRE), 0, WIRE.length);
        int extPadding = extended.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE)
            .padding(new UnsafeBufferEx(WIRE), 0, WIRE.length);

        assertEquals(basePadding + 64, extPadding);
    }

    @Test
    public void shouldReportParsingFailureEvent()
    {
        ProtobufModelEventType[] kind = new ProtobufModelEventType[1];
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(capturingKind(kind));
        ProtobufModelHandlerImpl handler = newHandler(null);
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        // message index 0 followed by an unterminated variable-length integer (continuation bit set, no
        // terminating byte) -- malformed wire bytes the parser cannot decode at all, not a schema violation
        byte[] malformed = {0x00, (byte) 0xFF};
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(malformed), 0, malformed.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
        assertEquals(ProtobufModelEventType.PARSING_FAILED, kind[0]);
    }

    @Test
    public void shouldReportTransformFailureEvent()
    {
        ProtobufModelEventType[] kind = new ProtobufModelEventType[1];
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(capturingKind(kind));
        List<ProtobufModelExtContext> exts = List.of(failing());
        ProtobufModelHandlerImpl handler = newHandler(null, exts);
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
        assertEquals(ProtobufModelEventType.TRANSFORM_FAILED, kind[0]);
    }

    // decodes each emitted event's extension kind into the given single-element array, mirroring the
    // production ProtobufModelEventFormatter's own wrap-and-switch, so a test can assert which event fired
    // without depending on supplyEventId's (unstubbed, indistinguishable) mocked return value
    private static MessageConsumer capturingKind(
        ProtobufModelEventType[] kind)
    {
        EventFW eventRO = new EventFW();
        ProtobufModelEventExFW extensionRO = new ProtobufModelEventExFW();
        return (msgTypeId, buffer, index, length) ->
        {
            EventFW event = eventRO.wrap(buffer, index, index + length);
            ProtobufModelEventExFW extension = extensionRO
                .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
            kind[0] = extension.kind();
        };
    }

    // A stage's own exception (not a parsing nor a validation exception) standing in for an extension's
    // internal failure during its own transform logic.
    private static ProtobufModelExtContext failing()
    {
        return (schema, config) -> new ProtobufModelExtHandler()
        {
            @Override
            public <T extends ProtobufTransformable<T>> T decode(
                T transformable,
                ProtobufCache cache)
            {
                return transformable.transform(new Failing());
            }
        };
    }

    private static final class Failing implements ProtobufTransform
    {
        @Override
        public ProtobufPipeline.Status transform(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event,
            ProtobufSink sink)
        {
            throw new ProtobufException("extension failure");
        }
    }

    private ProtobufModelHandlerImpl newHandler(
        String view)
    {
        return newHandler(view, List.of());
    }

    private ProtobufModelHandlerImpl newHandler(
        String view,
        List<ProtobufModelExtContext> exts)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
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
                    .record("SimpleMessage")
                    .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new ProtobufModelHandlerImpl(model, context, exts);
    }

    private static ProtobufModelExtContext expandingExt(
        int padding)
    {
        return (schema, config) -> new ProtobufModelExtHandler()
        {
            @Override
            public int padding(
                ProtobufSchema schema)
            {
                return padding;
            }
        };
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
        MutableDirectBufferEx dst,
        int produced,
        ByteArrayOutputStream sink)
    {
        byte[] chunk = new byte[produced];
        dst.getBytes(0, chunk);
        sink.writeBytes(chunk);
    }

    private static String text(
        MutableDirectBufferEx dst,
        int produced)
    {
        byte[] chunk = new byte[produced];
        dst.getBytes(0, chunk);
        return new String(chunk, UTF_8);
    }

    private static ModelTransform observer(
        Map<String, String> extracted)
    {
        return new ModelTransform()
        {
            @Override
            public ModelStatus transform(
                ModelController control,
                ModelSource source,
                ModelEvent event,
                ModelSink sink)
            {
                if (event == ModelEvent.FIELD)
                {
                    DirectBufferEx value = source.getValue();
                    extracted.put(source.getPath(), value.getStringWithoutLengthUtf8(0, value.capacity()));
                }
                return sink.transform(control, source, event);
            }

            @Override
            public boolean identity()
            {
                return true;
            }
        };
    }
}
