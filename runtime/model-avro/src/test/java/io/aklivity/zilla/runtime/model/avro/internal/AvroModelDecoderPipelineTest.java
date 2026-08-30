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
package io.aklivity.zilla.runtime.model.avro.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
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
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExtContext;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExtHandler;

public class AvroModelDecoderPipelineTest
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String SCHEMA = """
        {
            "fields":
            [
                { "name": "id", "type": "string" },
                { "name": "status", "type": "string" }
            ],
            "name": "Event",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    private static final String SCALARS_SCHEMA = """
        {
            "fields":
            [
                { "name": "i", "type": "int" },
                { "name": "l", "type": "long" },
                { "name": "f", "type": "float" },
                { "name": "d", "type": "double" },
                { "name": "b", "type": "boolean" },
                { "name": "e", "type": { "type": "enum", "name": "Kind", "symbols": [ "LOW", "HIGH" ] } }
            ],
            "name": "Scalars",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    // id="id0" (len 3) then status="positive" (len 8)
    private static final byte[] AVRO = {0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
    private static final String JSON = "{\"id\":\"id0\",\"status\":\"positive\"}";

    private EngineContext context;
    private AvroModelConfiguration config;

    @Before
    public void init()
    {
        config = new AvroModelConfiguration(new Configuration());
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        AvroModelHandlerImpl handler = newHandler();
        // two per-stream pipelines from the same per-worker handler
        ModelPipeline a = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);
        ModelPipeline b = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        // stream A split at the field boundary: the id field first, the status field on the final fragment
        byte[] a1 = {0x06, 0x69, 0x64, 0x30};
        byte[] a2tail = {0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ByteArrayOutputStream outA = new ByteArrayOutputStream();

        // stream A: first fragment, incomplete -> UNDERFLOW
        ModelPipelineResult ra1 = a.transform(0L, 0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());
        drain(dst, ra1.produced(), outA);

        // stream B: a whole value fed in the middle of A — would corrupt A if state were shared
        ModelPipelineResult rb = b.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(AVRO), 0, AVRO.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());
        assertEquals(JSON, text(dst, rb.produced()));

        // stream A: finish, prepending A's unconsumed remainder (the caller's decode-slot residue)
        byte[] a2 = concat(a1, ra1.consumed(), a2tail);
        ModelPipelineResult ra2 = a.transform(0L, 0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
        drain(dst, ra2.produced(), outA);

        assertEquals(JSON, outA.toString(UTF_8));
    }

    @Test
    public void shouldExtractField()
    {
        AvroModelHandlerImpl handler = newHandler();

        Map<String, String> extracted = new HashMap<>();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, observer(extracted), ModelCache.NONE);

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("id0", extracted.get("$.id"));
        assertEquals("positive", extracted.get("$.status"));
    }

    @Test
    public void shouldExtractScalarFields()
    {
        AvroModelHandlerImpl handler = newHandler(SCALARS_SCHEMA, "json");

        Map<String, String> extracted = new HashMap<>();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, observer(extracted), ModelCache.NONE);

        // i=5, l=7, f=1.5, d=2.5, b=true, e=index 1
        byte[] scalars =
        {
            0x0a,
            0x0e,
            0x00, 0x00, (byte) 0xc0, 0x3f,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, 0x40,
            0x01,
            0x02
        };
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(scalars), 0, scalars.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("5", extracted.get("$.i"));
        assertEquals("7", extracted.get("$.l"));
        assertEquals("1.5", extracted.get("$.f"));
        assertEquals("2.5", extracted.get("$.d"));
        assertEquals("true", extracted.get("$.b"));
        assertEquals("1", extracted.get("$.e"));
    }

    @Test
    public void shouldReportDecodePadding()
    {
        AvroModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        assertTrue(pipeline.padding(new UnsafeBufferEx(AVRO), 0, AVRO.length) >= 0);
    }

    @Test
    public void shouldIncludeExtensionPaddingContribution()
    {
        AvroModelHandlerImpl baseline = newHandler(SCHEMA, "json", List.of());
        AvroModelHandlerImpl extended = newHandler(SCHEMA, "json", List.of(expandingExt(64)));

        int basePadding = baseline.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE)
            .padding(new UnsafeBufferEx(AVRO), 0, AVRO.length);
        int extPadding = extended.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE)
            .padding(new UnsafeBufferEx(AVRO), 0, AVRO.length);

        assertEquals(basePadding + 64, extPadding);
    }

    @Test
    public void shouldReportIdentityWhenNoView()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, null);
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        assertFalse(pipeline.identity());

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertTrue(pipeline.identity());
    }

    @Test
    public void shouldNotReportIdentityWhenJsonView()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertFalse(pipeline.identity());
    }

    @Test
    public void shouldRoundTripJsonViewThroughWriteThenRead()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");

        ModelPipeline writer = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.WRITE);
        MutableDirectBufferEx cached = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult written = writer.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(AVRO), 0, AVRO.length, cached, 0, cached.capacity());

        assertEquals(ModelStatus.COMPLETE, written.status());
        assertEquals(JSON, text(cached, written.produced()));

        // a reader fetching the value WRITE just cached: its source must parse the cached json,
        // not avro wire bytes, since that is the only form the cache holds
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
        AvroModelHandlerImpl handler = newHandler(SCHEMA, null);

        ModelPipeline writer = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.WRITE);
        MutableDirectBufferEx cached = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult written = writer.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(AVRO), 0, AVRO.length, cached, 0, cached.capacity());

        assertEquals(ModelStatus.COMPLETE, written.status());

        // no view configured, so WRITE's re-encoded output is still avro wire bytes -- READ's
        // frontend must be able to parse it exactly like NONE's does
        byte[] cachedBytes = new byte[written.produced()];
        cached.getBytes(0, cachedBytes);

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
        AvroModelConfig model = AvroModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                    .schema()
                        .id(999)
                        .build()
                .build()
            .build();
        EngineContext ctx = mock(EngineContext.class);
        when(ctx.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroModelHandlerImpl handler = new AvroModelHandlerImpl(config, model, ctx, List.of());

        byte[] wire = concat(prefixBytes, 0, AVRO);
        ModelPipeline writer = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.WRITE);
        MutableDirectBufferEx cached = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult written = writer.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(wire), 0, wire.length, cached, 0, cached.capacity());

        assertEquals(ModelStatus.COMPLETE, written.status());
        byte[] cachedBytes = new byte[written.produced()];
        cached.getBytes(0, cachedBytes);
        assertEquals(JSON, new String(cachedBytes, prefixBytes.length, cachedBytes.length - prefixBytes.length, UTF_8));

        ModelPipeline reader = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.READ);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult read = reader.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(cachedBytes), 0, cachedBytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, read.status());
        assertEquals(JSON, text(dst, read.produced()));
    }

    private AvroModelHandlerImpl newHandler()
    {
        return newHandler(SCHEMA, "json");
    }

    private AvroModelHandlerImpl newHandler(
        String schema,
        String view)
    {
        return newHandler(schema, view, List.of());
    }

    private AvroModelHandlerImpl newHandler(
        String schema,
        String view,
        List<AvroModelExtContext> exts)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(schema)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .view(view)
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
        return new AvroModelHandlerImpl(config, model, context, exts);
    }

    private static AvroModelExtContext expandingExt(
        int padding)
    {
        return (schema, config) -> new AvroModelExtHandler()
        {
            @Override
            public int padding(
                AvroSchema schema)
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
