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
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class StringModelPipelineTest
{
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
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

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
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

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
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldOverflowBoundedDestination()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "Valid String".getBytes();
        MutableDirectBuffer src = new UnsafeBufferEx(bytes);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[bytes.length]);

        // first call: only 5 bytes of room -> OVERFLOW, 5 consumed/produced
        ModelPipelineResult first = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            src, 0, bytes.length, dst, 0, 5);
        assertEquals(ModelStatus.OVERFLOW, first.status());
        assertEquals(5, first.consumed());
        assertEquals(5, first.produced());

        // re-call advancing the source by what was consumed, INIT cleared per the driver contract
        int progress = first.consumed();
        ModelPipelineResult second = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_FIN,
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
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[0]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, 0);

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
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[64]);

        ModelPipelineResult ra1 = a.transform(0L, 0L, ModelPipeline.FLAGS_INIT,
            new UnsafeBufferEx(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());

        ModelPipelineResult rb = b.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(whole), 0, whole.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());

        ModelPipelineResult ra2 = a.transform(0L, 0L, ModelPipeline.FLAGS_FIN,
            new UnsafeBufferEx(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
    }

    @Test
    public void shouldResetForNextValue()
    {
        ModelHandler handler = handler(StringModelConfig.builder().encoding("utf_8").build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "abc".getBytes();
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[16]);
        pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
        pipeline.reset();

        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    private ModelHandler handler(
        StringModelConfig config)
    {
        return new StringModelContext(context).supplyHandler(config);
    }
}
