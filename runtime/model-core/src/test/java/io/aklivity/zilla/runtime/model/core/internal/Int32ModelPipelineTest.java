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
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;

public class Int32ModelPipelineTest
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
    public void shouldTransformSignedValue()
    {
        ModelHandler handler = handler(Int32ModelConfig.builder().format("text").build());
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] bytes = "+8449999".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
    }

    @Test
    public void shouldTransformNegativeValue()
    {
        ModelHandler handler = handler(Int32ModelConfig.builder().format("text").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "-125".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
    }

    @Test
    public void shouldRejectAtExclusiveMaxLimit()
    {
        ModelHandler handler = handler(Int32ModelConfig.builder()
            .format("text")
            .max(999)
            .exclusiveMax(true)
            .build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "999".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldRejectAtExclusiveMinLimit()
    {
        ModelHandler handler = handler(Int32ModelConfig.builder()
            .format("text")
            .min(999)
            .exclusiveMin(true)
            .build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "999".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldTransformBinaryWholeValue()
    {
        ModelHandler handler = handler(Int32ModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] bytes = {0, 0, 0, 42};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    @Test
    public void shouldRejectBinaryTooLong()
    {
        ModelHandler handler = handler(Int32ModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "Test value".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldRejectBinaryFragmentedTooShort()
    {
        ModelHandler handler = handler(Int32ModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] head = {0, 0, 0};
        byte[] tail = {0, 42};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(head), 0, head.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, first.status());

        ModelPipelineResult second = pipeline.transform(0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(tail), 0, tail.length, dst, head.length, dst.capacity());
        assertEquals(ModelStatus.REJECTED, second.status());
    }

    @Test
    public void shouldTransformBinaryFragmented()
    {
        ModelHandler handler = handler(Int32ModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] head = {0x00, 0x00};
        byte[] tail = {0x00, 0x2a};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(head), 0, head.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, first.status());
        assertEquals(head.length, first.consumed());

        ModelPipelineResult second = pipeline.transform(0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(tail), 0, tail.length, dst, head.length, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, second.status());
        assertEquals(tail.length, second.consumed());
    }

    private ModelHandler handler(
        Int32ModelConfig config)
    {
        return new Int32ModelContext(context).supplyHandler(config);
    }
}
