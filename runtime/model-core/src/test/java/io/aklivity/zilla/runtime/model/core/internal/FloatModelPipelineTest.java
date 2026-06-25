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
import io.aklivity.zilla.runtime.model.core.config.FloatModelConfig;

public class FloatModelPipelineTest
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
    public void shouldTransformSignedSuffixedValue()
    {
        ModelHandler handler = handler(FloatModelConfig.builder().format("text").build());
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

<<<<<<< HEAD
        byte[] bytes = "4.2".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
=======
        byte[] bytes = "+10.1119f".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
>>>>>>> origin/develop

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    @Test
    public void shouldTransformNegativeSuffixedValue()
    {
        ModelHandler handler = handler(FloatModelConfig.builder().format("text").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

<<<<<<< HEAD
        byte[] bytes = "4.x".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
=======
        byte[] bytes = "-.1119f".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
    }

    @Test
    public void shouldRejectMultipleDecimalPoints()
    {
        ModelHandler handler = handler(FloatModelConfig.builder().format("text").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "-.11.19f".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
>>>>>>> origin/develop

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldTransformWithinMaxLimit()
    {
        ModelHandler handler = handler(FloatModelConfig.builder().format("text").max(99.99f).build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "99.99f".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
    }

    @Test
    public void shouldRejectAtExclusiveMaxLimit()
    {
        ModelHandler handler = handler(FloatModelConfig.builder()
            .format("text")
            .max(99.99f)
            .exclusiveMax(true)
            .build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "99.99f".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[32]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldRejectBelowExclusiveMinLimit()
    {
        ModelHandler handler = handler(FloatModelConfig.builder()
            .format("text")
            .min(10.0f)
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
        ModelHandler handler = handler(FloatModelConfig.builder().format("text").multiple(10.0f).build());
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
        ModelHandler handler = handler(FloatModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] bytes = {62, -128, 5, 8};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    @Test
    public void shouldRejectBinaryTooLong()
    {
        ModelHandler handler = handler(FloatModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "Invalid Float".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldTransformBinaryFragmented()
    {
        ModelHandler handler = handler(FloatModelConfig.builder().format("binary").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] head = {62, -128};
        byte[] tail = {5, 8};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(head), 0, head.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, first.status());

        ModelPipelineResult second = pipeline.transform(0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(tail), 0, tail.length, dst, head.length, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, second.status());
    }

    private ModelHandler handler(
        FloatModelConfig config)
    {
        return new FloatModelContext(context).supplyHandler(config);
    }
}
