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

import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonModelDecoderPipelineTest
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
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

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
<<<<<<< HEAD:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonDecodeModelPipelineTest.java
    public void shouldTransformWholeValue()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(in.length, result.consumed());
        assertEquals("{\"id\":\"123\",\"status\":\"OK\"}", text(dst, result.produced()));
    }

    @Test
=======
>>>>>>> origin/develop:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonModelDecoderPipelineTest.java
    public void shouldIsolateInterleavedStreams()
    {
        JsonModelHandlerImpl handler = newHandler();
        // two per-stream pipelines from the same per-worker handler
        ModelPipeline a = handler.supplyDecoder(ModelVisitor.NONE);
        ModelPipeline b = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] a1 = "{\"id\":\"A\",".getBytes(UTF_8);
        byte[] a2tail = "\"status\":\"OK\"}".getBytes(UTF_8);
        byte[] bWhole = "{\"id\":\"B\",\"status\":\"NO\"}".getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ByteArrayOutputStream outA = new ByteArrayOutputStream();

        // stream A: first fragment, incomplete -> UNDERFLOW
<<<<<<< HEAD:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonDecodeModelPipelineTest.java
        ModelPipelineResult ra1 = a.transform(0L, 0L, ModelPipeline.FLAGS_INIT,
            new UnsafeBufferEx(a1), 0, a1.length, dst, 0, dst.capacity());
=======
        ModelPipelineResult ra1 = a.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(a1), 0, a1.length, dst, 0, dst.capacity());
>>>>>>> origin/develop:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonModelDecoderPipelineTest.java
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());
        drain(dst, ra1.produced(), outA);

        // stream B: a whole value fed in the middle of A — would corrupt A if state were shared
<<<<<<< HEAD:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonDecodeModelPipelineTest.java
        ModelPipelineResult rb = b.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bWhole), 0, bWhole.length, dst, 0, dst.capacity());
=======
        ModelPipelineResult rb = b.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bWhole), 0, bWhole.length, dst, 0, dst.capacity());
>>>>>>> origin/develop:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonModelDecoderPipelineTest.java
        assertEquals(ModelStatus.COMPLETE, rb.status());
        assertEquals("{\"id\":\"B\",\"status\":\"NO\"}", text(dst, rb.produced()));

        // stream A: finish, prepending A's unconsumed remainder (the caller's decode-slot residue)
        byte[] a2 = concat(a1, ra1.consumed(), a2tail);
<<<<<<< HEAD:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonDecodeModelPipelineTest.java
        ModelPipelineResult ra2 = a.transform(0L, 0L, ModelPipeline.FLAGS_FIN,
            new UnsafeBufferEx(a2), 0, a2.length, dst, 0, dst.capacity());
=======
        ModelPipelineResult ra2 = a.transform(0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(a2), 0, a2.length, dst, 0, dst.capacity());
>>>>>>> origin/develop:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonModelDecoderPipelineTest.java
        assertEquals(ModelStatus.COMPLETE, ra2.status());
        drain(dst, ra2.produced(), outA);

        assertEquals("{\"id\":\"A\",\"status\":\"OK\"}", outA.toString(UTF_8));
    }

    @Test
    public void shouldExtractField()
    {
        JsonModelHandlerImpl handler = newHandler();
        Map<String, String> extracted = new HashMap<>();
        ModelVisitor visitor = (path, buffer, index, length) ->
        {
            byte[] bytes = new byte[length];
            buffer.getBytes(index, bytes);
            extracted.put(path, new String(bytes, UTF_8));
        };
        ModelPipeline pipeline = handler.supplyDecoder(visitor);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
<<<<<<< HEAD:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonDecodeModelPipelineTest.java
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
=======
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
>>>>>>> origin/develop:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonModelDecoderPipelineTest.java

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("123", extracted.get("$.id"));
        assertEquals("OK", extracted.get("$.status"));
    }

    @Test
    public void shouldReportDecodePadding()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        assertTrue(pipeline.padding(new UnsafeBufferEx(in), 0, in.length) >= 0);
    }

    @Test
    public void shouldReportIdentity()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        assertFalse(pipeline.identity());

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
<<<<<<< HEAD:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonDecodeModelPipelineTest.java
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
=======
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
>>>>>>> origin/develop:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonModelDecoderPipelineTest.java

        assertTrue(pipeline.identity());
    }

    private JsonModelHandlerImpl newHandler()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(OBJECT_SCHEMA)
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
