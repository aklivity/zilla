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
import java.util.HashMap;
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
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;

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
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        JsonModelHandlerImpl handler = newHandler();
        // two per-stream pipelines from the same per-worker handler
        ModelPipeline a = handler.supplyDecoder(ModelTransform.NONE);
        ModelPipeline b = handler.supplyDecoder(ModelTransform.NONE);

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
        ModelPipeline pipeline = handler.supplyDecoder(observer(extracted));

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
        ModelPipeline pipeline = handler.supplyDecoder(observer(extracted));

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
        ModelPipeline pipeline = handler.supplyDecoder(observer(extracted));

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
        ModelPipeline pipeline = handler.supplyDecoder(observer(extracted));

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
        ModelPipeline pipeline = handler.supplyDecoder(ModelTransform.NONE);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        assertTrue(pipeline.padding(new UnsafeBufferEx(in), 0, in.length) >= 0);
    }

    @Test
    public void shouldReportIdentity()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelTransform.NONE);

        assertFalse(pipeline.identity());

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertTrue(pipeline.identity());
    }

    private JsonModelHandlerImpl newHandler()
    {
        return newHandler(OBJECT_SCHEMA);
    }

    private JsonModelHandlerImpl newHandler(
        String schema)
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
        return new JsonModelHandlerImpl(model, context);
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
