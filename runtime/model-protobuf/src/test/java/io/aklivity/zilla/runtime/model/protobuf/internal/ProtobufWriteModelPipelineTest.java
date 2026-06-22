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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
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

public class ProtobufWriteModelPipelineTest
{
    private static final String SCHEMA = """
                                            syntax = "proto3";
                                            package io.aklivity.examples.clients.proto;
                                            message SimpleMessage {
                                                string content = 1;
                                                optional string date_time = 2;
                                            }
                                            """;

    private static final String JSON = "{\"content\":\"OK\",\"date_time\":\"01012024\"}";
    // single zero index byte for the first top-level message, then content="OK" and date_time="01012024"
    private static final byte[] WIRE =
        {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldTransformWholeValue()
    {
        ProtobufModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] in = JSON.getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(in.length, result.consumed());
        byte[] out = new byte[result.produced()];
        dst.getBytes(0, out);
        assertArrayEquals(WIRE, out);

        // reset and reuse the same pipeline for the next value
        pipeline.reset();
        ModelPipelineResult reused = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, reused.status());
        byte[] outReused = new byte[reused.produced()];
        dst.getBytes(0, outReused);
        assertArrayEquals(WIRE, outReused);
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        ProtobufModelHandlerImpl handler = newHandler();
        // two per-stream pipelines from the same per-worker handler
        ModelPipeline a = handler.supplyEncoder(ModelVisitor.NONE);
        ModelPipeline b = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] a1 = "{\"content\":\"OK\",".getBytes(UTF_8);
        byte[] a2tail = "\"date_time\":\"01012024\"}".getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ByteArrayOutputStream outA = new ByteArrayOutputStream();

        // stream A: first fragment, incomplete -> UNDERFLOW
        ModelPipelineResult ra1 = a.transform(0L, 0L, ModelPipeline.FLAGS_INIT,
            new UnsafeBufferEx(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());
        drain(dst, ra1.produced(), outA);

        // stream B: a whole value fed in the middle of A — would corrupt A if state were shared
        byte[] bIn = JSON.getBytes(UTF_8);
        ModelPipelineResult rb = b.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bIn), 0, bIn.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());
        byte[] outB = new byte[rb.produced()];
        dst.getBytes(0, outB);
        assertArrayEquals(WIRE, outB);

        // stream A: finish, prepending A's unconsumed remainder (the caller's decode-slot residue)
        byte[] a2 = concat(a1, ra1.consumed(), a2tail);
        ModelPipelineResult ra2 = a.transform(0L, 0L, ModelPipeline.FLAGS_FIN,
            new UnsafeBufferEx(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
        drain(dst, ra2.produced(), outA);

        assertArrayEquals(WIRE, outA.toByteArray());
    }

    @Test
    public void shouldDrainOnOverflow()
    {
        ProtobufModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        // a 2000-byte content value forces the wire output past a small destination window, exercising the
        // bounded-chunk OVERFLOW drain across re-transforms (INIT cleared on every re-call after the first)
        String content = "A".repeat(2000);
        byte[] in = ("{\"content\":\"" + content + "\",\"date_time\":\"01012024\"}").getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[512]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int flags = ModelPipeline.FLAGS_COMPLETE;
        ModelPipelineResult result;
        int guard = 0;
        do
        {
            result = pipeline.transform(0L, 0L, flags, new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
            drain(dst, result.produced(), out);
            flags = ModelPipeline.FLAGS_FIN;
            guard++;
        }
        while (result.status() == ModelStatus.OVERFLOW && guard < 1000);

        assertEquals(ModelStatus.COMPLETE, result.status());
        byte[] wire = out.toByteArray();
        // the single zero index byte then a content field longer than the destination window
        assertEquals(0x00, wire[0]);
        org.junit.Assert.assertTrue(wire.length > 2000);
    }

    @Test
    public void shouldRejectUnknownRecord()
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        ProtobufModelHandlerImpl handler = newHandler("Nonexistent");
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] in = JSON.getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldRejectInvalid()
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        ProtobufModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        // an unknown field is rejected by the strict JSON parser
        byte[] in = "{\"content\":\"OK\",\"unexpected\":\"value\"}".getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    private ProtobufModelHandlerImpl newHandler()
    {
        return newHandler("SimpleMessage");
    }

    private ProtobufModelHandlerImpl newHandler(
        String record)
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
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record(record)
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
}
