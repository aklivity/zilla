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
package io.aklivity.zilla.runtime.model.avro.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
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

import io.aklivity.zilla.runtime.engine.Configuration;
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
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroModelDecoderPipelineTest
{
    private static final String SCHEMA = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
        "{\"name\":\"status\",\"type\":\"string\"}]," +
        "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

    private static final String SCALARS_SCHEMA = "{\"fields\":[{\"name\":\"i\",\"type\":\"int\"}," +
        "{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"}," +
        "{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"b\",\"type\":\"boolean\"}," +
        "{\"name\":\"e\",\"type\":{\"type\":\"enum\",\"name\":\"Kind\",\"symbols\":[\"LOW\",\"HIGH\"]}}]," +
        "\"name\":\"Scalars\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

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
    public void shouldTransformWholeValue()
    {
        AvroModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(AVRO.length, result.consumed());
        assertEquals(JSON, text(dst, result.produced()));
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        AvroModelHandlerImpl handler = newHandler();
        // two per-stream pipelines from the same per-worker handler
        ModelPipeline a = handler.supplyDecoder(ModelVisitor.NONE);
        ModelPipeline b = handler.supplyDecoder(ModelVisitor.NONE);

        // stream A split at the field boundary: the id field first, the status field on the final fragment
        byte[] a1 = {0x06, 0x69, 0x64, 0x30};
        byte[] a2tail = {0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ByteArrayOutputStream outA = new ByteArrayOutputStream();

        // stream A: first fragment, incomplete -> UNDERFLOW
        ModelPipelineResult ra1 = a.transform(0L, 0L, ModelPipeline.FLAGS_INIT,
            new UnsafeBuffer(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());
        drain(dst, ra1.produced(), outA);

        // stream B: a whole value fed in the middle of A — would corrupt A if state were shared
        ModelPipelineResult rb = b.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(AVRO), 0, AVRO.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());
        assertEquals(JSON, text(dst, rb.produced()));

        // stream A: finish, prepending A's unconsumed remainder (the caller's decode-slot residue)
        byte[] a2 = concat(a1, ra1.consumed(), a2tail);
        ModelPipelineResult ra2 = a.transform(0L, 0L, ModelPipeline.FLAGS_FIN,
            new UnsafeBuffer(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
        drain(dst, ra2.produced(), outA);

        assertEquals(JSON, outA.toString(UTF_8));
    }

    @Test
    public void shouldExtractField()
    {
        AvroModelHandlerImpl handler = newHandler();

        Map<String, String> extracted = new HashMap<>();
        ModelVisitor visitor = (path, buffer, index, length) ->
            extracted.put(path, buffer.getStringWithoutLengthUtf8(index, length));
        ModelPipeline pipeline = handler.supplyDecoder(visitor);

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("id0", extracted.get("$.id"));
        assertEquals("positive", extracted.get("$.status"));
    }

    @Test
    public void shouldExtractScalarFields()
    {
        AvroModelHandlerImpl handler = newHandler(SCALARS_SCHEMA, "json");

        Map<String, String> extracted = new HashMap<>();
        ModelVisitor visitor = (path, buffer, index, length) ->
            extracted.put(path, buffer.getStringWithoutLengthUtf8(index, length));
        ModelPipeline pipeline = handler.supplyDecoder(visitor);

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
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(scalars), 0, scalars.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("5", extracted.get("$.i"));
        assertEquals("7", extracted.get("$.l"));
        assertEquals("1.5", extracted.get("$.f"));
        assertEquals("2.5", extracted.get("$.d"));
        assertEquals("true", extracted.get("$.b"));
        assertEquals("1", extracted.get("$.e"));
    }

    @Test
    public void shouldRejectInvalid()
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        // truncated: id length prefix promises 3 bytes but the status field is missing under FLAGS_FIN
        byte[] invalid = {0x06, 0x69, 0x64, 0x30, 0x10};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(invalid), 0, invalid.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldReportDecodePadding()
    {
        AvroModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        assertTrue(pipeline.padding(new UnsafeBuffer(AVRO), 0, AVRO.length) >= 0);
    }

    @Test
    public void shouldReportIdentityWhenNoView()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, null);
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        assertFalse(pipeline.identity());

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertTrue(pipeline.identity());
    }

    @Test
    public void shouldNotReportIdentityWhenJsonView()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[256]);
        pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertFalse(pipeline.identity());
    }

    private AvroModelHandlerImpl newHandler()
    {
        return newHandler(SCHEMA, "json");
    }

    private AvroModelHandlerImpl newHandler(
        String schema,
        String view)
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
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
        return new AvroModelHandlerImpl(config, model, context);
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
