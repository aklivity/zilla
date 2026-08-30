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
package io.aklivity.zilla.runtime.model.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonTransformable;
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
import io.aklivity.zilla.runtime.model.json.ext.JsonCache;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtContext;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtHandler;

public class JsonModelDecoderPipelineTest
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_NONE = 0x00;
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String OBJECT_SCHEMA = """
        {
            "type": "object",
            "properties":
            {
                "id": { "type": "string" },
                "status": { "type": "string" }
            },
            "required": [ "id", "status" ]
        }""";

    // "note" has no content-constraining keyword (pattern/minLength/maxLength), so the validator forwards
    // a value spanning an input window in fragments instead of reassembling it first (see JsonExtractor)
    private static final String UNCONSTRAINED_VALUE_SCHEMA = """
        {
            "type": "object",
            "properties":
            {
                "id": { "type": "string" },
                "note": { "type": "string" }
            },
            "required": [ "id", "note" ]
        }""";

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        JsonModelHandlerImpl handler = newHandler();
        // two per-stream pipelines from the same per-worker handler
        ModelPipeline a = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);
        ModelPipeline b = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        byte[] a1 = "{\"id\":\"A\",".getBytes(UTF_8);
        byte[] a2tail = "\"status\":\"OK\"}".getBytes(UTF_8);
        byte[] bWhole = "{\"id\":\"B\",\"status\":\"NO\"}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ByteArrayOutputStream outA = new ByteArrayOutputStream();

        // stream A: first fragment, incomplete -> UNDERFLOW
        ModelPipelineResult ra1 = a.transform(0L, 0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());
        drain(dst, ra1.produced(), outA);

        // stream B: a whole value fed in the middle of A — would corrupt A if state were shared
        ModelPipelineResult rb = b.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bWhole), 0, bWhole.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());
        assertEquals("{\"id\":\"B\",\"status\":\"NO\"}", text(dst, rb.produced()));

        // stream A: finish, prepending A's unconsumed remainder (the caller's decode-slot residue)
        byte[] a2 = concat(a1, ra1.consumed(), a2tail);
        ModelPipelineResult ra2 = a.transform(0L, 0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
        drain(dst, ra2.produced(), outA);

        assertEquals("{\"id\":\"A\",\"status\":\"OK\"}", outA.toString(UTF_8));
    }

    @Test
    public void shouldExtractField()
    {
        JsonModelHandlerImpl handler = newHandler();
        Map<String, String> extracted = new HashMap<>();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, observer(extracted), ModelCache.NONE);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("123", extracted.get("$.id"));
        assertEquals("OK", extracted.get("$.status"));
    }

    @Test
    public void shouldExtractMultiByteAndSurrogatePairFields()
    {
        JsonModelHandlerImpl handler = newHandler();
        Map<String, String> extracted = new HashMap<>();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, observer(extracted), ModelCache.NONE);

        // "id" holds a 3-byte BMP char (中) and a 2-byte char (é); "status" holds a surrogate-pair emoji (😀)
        byte[] in = "{\"id\":\"中é\",\"status\":\"😀\"}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("中é", extracted.get("$.id"));
        assertEquals("😀", extracted.get("$.status"));
    }

    @Test
    public void shouldExtractUnpairedSurrogateAsReplacementChar()
    {
        JsonModelHandlerImpl handler = newHandler();
        Map<String, String> extracted = new HashMap<>();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, observer(extracted), ModelCache.NONE);

        // \uD800 is an unpaired high surrogate; String.getBytes(UTF_8) replaces it with '?' (0x3F)
        byte[] in = "{\"id\":\"a\\uD800b\",\"status\":\"OK\"}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("a?b", extracted.get("$.id"));
    }

    @Test
    public void shouldExtractFieldSpanningInputWindow()
    {
        JsonModelHandlerImpl handler = newHandler(UNCONSTRAINED_VALUE_SCHEMA);
        Map<String, String> extracted = new HashMap<>();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, observer(extracted), ModelCache.NONE);

        // "note" has no content keyword, so a value spanning an input window is forwarded to the
        // extractor in fragments (Validator's forward-and-suppress path) instead of being reassembled
        // first, unlike "id" or an object key
        String note = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        byte[] head = "{\"id\":\"1\",\"note\":".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[512]);

        // window 1: everything up to (not including) the value
        ModelPipelineResult r1 = pipeline.transform(0L, 0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(head), 0, head.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, r1.status());

        // window 2: a small window of ONLY value bytes (opening quote + a prefix), so the value's own
        // scanned bytes fill this window before it closes, forcing the fragmenting path
        byte[] remainder1 = concat(head, r1.consumed(), new byte[0]);
        byte[] valueChunk1 = ("\"" + note.substring(0, 20)).getBytes(UTF_8);
        byte[] window2 = concat(remainder1, 0, valueChunk1);
        ModelPipelineResult r2 = pipeline.transform(0L, 0L, 0L, FLAGS_NONE,
            new UnsafeBufferEx(window2), 0, window2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, r2.status());

        // window 3: the rest of the value, its closing quote, and the closing brace
        byte[] remainder2 = concat(window2, r2.consumed(), new byte[0]);
        byte[] tail = (note.substring(20) + "\"}").getBytes(UTF_8);
        byte[] window3 = concat(remainder2, 0, tail);
        ModelPipelineResult r3 = pipeline.transform(0L, 0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(window3), 0, window3.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, r3.status());
        assertEquals(note, extracted.get("$.note"));
    }

    @Test
    public void shouldReportDecodePadding()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        assertTrue(pipeline.padding(new UnsafeBufferEx(in), 0, in.length) >= 0);
    }

    @Test
    public void shouldReportIdentity()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        assertFalse(pipeline.identity());

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertTrue(pipeline.identity());
    }

    @Test
    public void shouldApplyInstalledExtensionAheadOfSchemaValidation()
    {
        // OBJECT_SCHEMA requires both "id" and "status"; an extension dropping "status" ahead of the
        // model's own validator stage turns an otherwise-valid document into a schema-rejected one
        List<JsonModelExtContext> exts = List.of(dropping("status"));
        JsonModelHandlerImpl handler = newHandler(OBJECT_SCHEMA, exts);
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldComposeMultipleInstalledExtensions()
    {
        // no required fields, so validation still succeeds once both extensions have dropped their field
        String schema = """
            {
                "type": "object",
                "properties":
                {
                    "id": { "type": "string" },
                    "status": { "type": "string" }
                }
            }""";
        List<JsonModelExtContext> exts = List.of(dropping("id"), dropping("status"));
        JsonModelHandlerImpl handler = newHandler(schema, exts);
        Map<String, String> extracted = new HashMap<>();
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, observer(extracted), ModelCache.NONE);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("{}", text(dst, result.produced()));
        assertTrue(extracted.isEmpty());
    }

    @Test
    public void shouldIncludeExtensionPaddingContribution()
    {
        JsonModelHandlerImpl baseline = newHandler(OBJECT_SCHEMA, List.of());
        JsonModelHandlerImpl extended = newHandler(OBJECT_SCHEMA, List.of(expandingExt(64)));

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        int basePadding = baseline.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE)
            .padding(new UnsafeBufferEx(in), 0, in.length);
        int extPadding = extended.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE)
            .padding(new UnsafeBufferEx(in), 0, in.length);

        assertEquals(basePadding + 64, extPadding);
    }

    private static JsonModelExtContext dropping(
        String key)
    {
        return (schema, config) -> new JsonModelExtHandler()
        {
            @Override
            public <T extends JsonTransformable<T>> T decode(
                T stream,
                JsonCache cache)
            {
                return stream.transform(new Skip(key));
            }
        };
    }

    private static JsonModelExtContext expandingExt(
        int padding)
    {
        return (schema, config) -> new JsonModelExtHandler()
        {
            @Override
            public int padding(
                JsonSchema schema)
            {
                return padding;
            }
        };
    }

    private JsonModelHandlerImpl newHandler()
    {
        return newHandler(OBJECT_SCHEMA);
    }

    private JsonModelHandlerImpl newHandler(
        String schema)
    {
        return newHandler(schema, List.of());
    }

    private JsonModelHandlerImpl newHandler(
        String schema,
        List<JsonModelExtContext> exts)
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
        JsonModelConfig model = JsonModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .subject(null)
                    .version("latest")
                    .id(0)
                    .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new JsonModelHandlerImpl(model, context, exts);
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

    // A mediating transform standing in for an installed extension's own stage: forwards every event
    // verbatim except a named top-level field, dropped with a single source.skipValue() on the matched
    // KEY_NAME (see common-json's JsonSkipTest for the technique this mirrors).
    private static final class Skip implements JsonTransform
    {
        private final String dropKey;

        private JsonController upstream;
        private boolean downstreamVerbatim;
        private int depth;

        private final JsonController mediator = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }

            @Override
            public void verbatim()
            {
                downstreamVerbatim = true;
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
                upstream.consumed(sourceBytes);
            }
        };

        private Skip(
            String dropKey)
        {
            this.dropKey = dropKey;
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            upstream = control;
            Status status;
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                status = sink.transform(mediator, source, forward(event));
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                Status downstream = sink.transform(mediator, source, forward(event));
                status = downstream == Status.REJECTED ? Status.REJECTED
                    : depth == 0 ? Status.COMPLETED
                    : downstream;
                break;
            case KEY_NAME:
                if (depth == 1 && contentEquals(dropKey, source.getStringView()))
                {
                    source.skipValue();
                    status = Status.ADVANCED;
                }
                else
                {
                    status = sink.transform(mediator, source, forward(event));
                }
                break;
            default:
                status = sink.transform(mediator, source, forward(event));
                break;
            }
            return status;
        }

        private JsonEvent forward(
            JsonEvent event)
        {
            boolean body = event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT && !event.segmented();
            return downstreamVerbatim && body ? JsonEvent.VERBATIM : event;
        }

        @Override
        public void reset()
        {
            downstreamVerbatim = false;
            depth = 0;
        }

        private static boolean contentEquals(
            String name,
            CharSequence view)
        {
            boolean matches = name.length() == view.length();
            for (int i = 0; matches && i < name.length(); i++)
            {
                matches = name.charAt(i) == view.charAt(i);
            }
            return matches;
        }
    }
}
