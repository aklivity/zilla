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
import io.aklivity.zilla.runtime.model.core.config.DoubleModelConfig;

public class DoubleModelPipelineTest
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
    public void shouldTransformSignedDecimalValue()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("text").build());
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

<<<<<<< HEAD
        byte[] bytes = "4.2".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
=======
        byte[] bytes = "+10.1119998321".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
>>>>>>> origin/develop

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    @Test
    public void shouldTransformNegativeLeadingDotValue()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("text").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

<<<<<<< HEAD
        byte[] bytes = "4.x".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
=======
        byte[] bytes = "-.11190092111111112".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
    }

    @Test
    public void shouldRejectMultipleDecimalPoints()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("text").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "-.11.19987654321".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
>>>>>>> origin/develop

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldTransformWithinMaxLimit()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("text").max(99.9987654321).build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "99.9987654321".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
    }

    @Test
    public void shouldRejectAtExclusiveMaxLimit()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder()
            .format("text")
            .max(99.9987654321)
            .exclusiveMax(true)
            .build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "99.9987654321".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldRejectBelowExclusiveMinLimit()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder()
            .format("text")
            .min(10.0)
            .exclusiveMin(true)
            .build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "10.0".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldRejectNotMultiple()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("text").multiple(10.0).build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "25".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldTransformBinaryValue()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] bytes = {-64, 0, 0, 0, 0, 0, 0, 0};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    @Test
    public void shouldRejectBinaryTooLong()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "Invalid Double".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldTransformBinaryFragmented()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] head = {-1, -17, 94};
        byte[] mid = {-95};
        byte[] tail = {-120, 23, -78, 63};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(head), 0, head.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, first.status());

        ModelPipelineResult second = pipeline.transform(0L, 0L, 0x00,
            new UnsafeBufferEx(mid), 0, mid.length, dst, head.length, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, second.status());

        ModelPipelineResult third = pipeline.transform(0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(tail), 0, tail.length, dst, head.length + mid.length, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, third.status());
    }

    @Test
    public void shouldRejectBinaryFragmentedTooShort()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] head = {0, 0, 0};
        byte[] mid = {0, 0};
        byte[] tail = {42};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(head), 0, head.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, first.status());

        ModelPipelineResult second = pipeline.transform(0L, 0L, 0x00,
            new UnsafeBufferEx(mid), 0, mid.length, dst, head.length, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, second.status());

        ModelPipelineResult third = pipeline.transform(0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(tail), 0, tail.length, dst, head.length + mid.length, dst.capacity());
        assertEquals(ModelStatus.REJECTED, third.status());
    }

    @Test
    public void shouldResetForNextValue()
    {
        ModelHandler handler = handler(DoubleModelConfig.builder().format("text").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "4.2".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);
        pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
        pipeline.reset();

        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, result.status());
    }

    private ModelHandler handler(
        DoubleModelConfig config)
    {
        return new DoubleModelContext(context).supplyHandler(config);
    }
}
