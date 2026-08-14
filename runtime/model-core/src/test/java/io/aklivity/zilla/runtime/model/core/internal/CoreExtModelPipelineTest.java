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
package io.aklivity.zilla.runtime.model.core.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.model.core.BytesModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtHandler;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransform;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransformable;

public class CoreExtModelPipelineTest
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMPLETE = 0x03;

    @Test
    public void shouldRejectWithoutDeliveryWhenExtensionSignalsOmit()
    {
        BytesModelExtContext ext = config -> stream -> stream.transform(omit());
        ModelHandler handler = handler(ext);
        ModelPipeline pipeline = handler.supplyDecoder(ModelTransform.NONE);

        byte[] bytes = "secret".getBytes();
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[32]);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
        assertEquals(0, result.produced());
    }

    @Test
    public void shouldAccumulateFragmentsBeforeApplyingExtension()
    {
        BytesModelExtContext ext = config -> stream -> stream.transform(uppercase());
        ModelHandler handler = handler(ext);
        ModelPipeline pipeline = handler.supplyDecoder(ModelTransform.NONE);

        byte[] head = "va".getBytes();
        byte[] tail = "lue".getBytes();
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[32]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, 0L, FLAGS_INIT,
            new UnsafeBufferEx(head), 0, head.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, first.status());
        assertEquals(0, first.produced());

        ModelPipelineResult second = pipeline.transform(0L, 0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(tail), 0, tail.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, second.status());
        assertEquals("VALUE", dst.getStringWithoutLengthUtf8(0, second.produced()));
    }

    @Test
    public void shouldDrainOverflowAcrossMultipleCalls()
    {
        BytesModelExtContext ext = config -> stream -> stream.transform(uppercase());
        ModelHandler handler = handler(ext);
        ModelPipeline pipeline = handler.supplyDecoder(ModelTransform.NONE);

        byte[] bytes = "abcdef".getBytes();
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[bytes.length]);

        // only 3 bytes of room -> OVERFLOW
        ModelPipelineResult first = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, 3);
        assertEquals(ModelStatus.OVERFLOW, first.status());
        assertEquals(bytes.length, first.consumed());
        assertEquals(3, first.produced());

        // drain the remainder
        ModelPipelineResult second = pipeline.transform(0L, 0L, 0L, FLAGS_FIN,
            new UnsafeBufferEx(bytes), bytes.length, bytes.length, dst, 3, bytes.length);
        assertEquals(ModelStatus.COMPLETE, second.status());
        assertEquals(0, second.consumed());
        assertEquals(3, second.produced());
        assertEquals("ABCDEF", dst.getStringWithoutLengthUtf8(0, 6));
    }

    @Test
    public void shouldReportSummedExtensionPadding()
    {
        BytesModelExtContext ext1 = config -> paddedHandler(4);
        BytesModelExtContext ext2 = config -> paddedHandler(6);

        BytesModelContext context = new BytesModelContext(mock(EngineContext.class), List.of(ext1, ext2));
        ModelHandler handler = context.supplyHandler(BytesModelConfig.builder().build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelTransform.NONE);

        assertEquals(10, pipeline.padding(new UnsafeBufferEx(new byte[0]), 0, 0));
    }

    private static ModelHandler handler(
        BytesModelExtContext ext)
    {
        BytesModelContext context = new BytesModelContext(mock(EngineContext.class), List.of(ext));
        return context.supplyHandler(BytesModelConfig.builder().build());
    }

    private static BytesTransform omit()
    {
        return (value, index, length, dst, dstIndex) -> BytesTransform.OMIT;
    }

    private static BytesTransform uppercase()
    {
        return (value, index, length, dst, dstIndex) ->
        {
            for (int i = 0; i < length; i++)
            {
                byte b = value.getByte(index + i);
                dst.putByte(dstIndex + i, (byte) Character.toUpperCase((char) b));
            }
            return length;
        };
    }

    private static BytesModelExtHandler paddedHandler(
        int amount)
    {
        return new BytesModelExtHandler()
        {
            @Override
            public BytesTransformable transform(
                BytesTransformable stream)
            {
                return stream.transform(BytesTransform.NONE);
            }

            @Override
            public int padding()
            {
                return amount;
            }
        };
    }
}
