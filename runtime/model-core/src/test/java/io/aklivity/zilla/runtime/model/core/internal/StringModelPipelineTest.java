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
package io.aklivity.zilla.runtime.model.core.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class StringModelPipelineTest
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMPLETE = 0x03;

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    @Test
    public void shouldTransformWholeValue()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "Valid String".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.consumed());
        assertEquals(bytes.length, result.produced());
        assertEquals("Valid String", dst.getStringWithoutLengthUtf8(0, result.produced()));
    }

    @Test
    public void shouldRejectInvalidEncoding()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = {(byte) 0xc0};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
        assertEquals(0, result.consumed());
        assertEquals(0, result.produced());
    }

    @Test
    public void shouldRejectInvalidPattern()
    {
        ModelHandler handler = handler(StringModelConfig.builder()
            .encoding("utf_8")
            .pattern("^[a-zA-Z\\s]+$")
            .build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "Hello123".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldOverflowBoundedDestination()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "Valid String".getBytes();
        MutableDirectBuffer src = new UnsafeBuffer(bytes);
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[bytes.length]);

        // first call: only 5 bytes of room -> OVERFLOW, 5 consumed/produced
        ModelPipelineResult first = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            src, 0, bytes.length, dst, 0, 5);
        assertEquals(ModelStatus.OVERFLOW, first.status());
        assertEquals(5, first.consumed());
        assertEquals(5, first.produced());

        // re-call advancing the source by what was consumed, INIT cleared per the driver contract
        int progress = first.consumed();
        ModelPipelineResult second = pipeline.transform(0L, 0L, FLAGS_FIN,
            src, progress, bytes.length, dst, progress, bytes.length);
        assertEquals(ModelStatus.COMPLETE, second.status());
        assertEquals(bytes.length - progress, second.consumed());
        assertEquals(bytes.length - progress, second.produced());
        assertEquals("Valid String", dst.getStringWithoutLengthUtf8(0, bytes.length));
    }

    @Test
    public void shouldOverflowWithEmptyDestination()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "Valid String".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[0]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, 0);

        assertEquals(ModelStatus.OVERFLOW, result.status());
        assertEquals(0, result.consumed());
        assertEquals(0, result.produced());
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        // one per-worker handler vends two per-stream pipelines; fragmenting A across B must not corrupt A
        ModelPipeline a = handler.supplyDecoder(ModelVisitor.NONE);
        ModelPipeline b = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] a1 = "Valid ".getBytes();
        byte[] a2 = "String".getBytes();
        byte[] whole = "Other Value".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);

        ModelPipelineResult ra1 = a.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBuffer(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());

        ModelPipelineResult rb = b.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(whole), 0, whole.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());

        ModelPipelineResult ra2 = a.transform(0L, 0L, FLAGS_FIN,
            new UnsafeBuffer(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
    }

    @Test
    public void shouldResetForNextValue()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "abc".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());
        pipeline.reset();

        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    @Test
    public void shouldTransformMultiByteValue()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] bytes = "Héllo wörld €".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    @Test
    public void shouldTransformMultiByteFragmented()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        // "é€" split at the character boundary: "é" is 2 bytes, "€" is 3 bytes
        byte[] head = "é".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tail = "€".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBuffer(head), 0, head.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, first.status());

        ModelPipelineResult second = pipeline.transform(0L, 0L, FLAGS_FIN,
            new UnsafeBuffer(tail), 0, tail.length, dst, first.produced(), dst.capacity());
        assertEquals(ModelStatus.COMPLETE, second.status());
    }

    @Test
    public void shouldRejectInvalidUtf8Continuation()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = {(byte) 0xe2, (byte) 0x28, (byte) 0xa1};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldTransformMatchingPattern()
    {
        ModelHandler handler = handler(StringModelConfig.builder()
            .encoding("utf_8")
            .pattern("^[a-zA-Z\\s]+$")
            .build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "Hello World".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
    }

    private ModelHandler handler(
        StringModelConfig config)
    {
        return new StringModelContext(context).supplyHandler(config);
    }
}
