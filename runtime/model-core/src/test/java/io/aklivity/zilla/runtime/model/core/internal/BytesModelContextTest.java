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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import io.aklivity.zilla.config.model.core.BytesModelConfig;
import io.aklivity.zilla.config.model.core.StringModelConfig;
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

public class BytesModelContextTest
{
    private static final int FLAGS_COMPLETE = 0x03;

    @Test
    public void shouldPassThroughUnmodifiedWithZeroExtensions()
    {
        BytesModelContext context = new BytesModelContext(mock(EngineContext.class), List.of());
        ModelHandler handler = context.supplyHandler(BytesModelConfig.builder().build());

        assertThat(handler, instanceOf(CoreModelHandler.class));

        ModelPipeline pipeline = handler.supplyDecoder(ModelTransform.NONE);
        byte[] bytes = { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF };
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    @Test
    public void shouldComposeMultipleExtensionsInDiscoveryOrderAheadOfModelsOwnProcessing()
    {
        // ext1 replaces the value with "A" entirely, ignoring input; ext2 appends "B" to whatever it
        // receives; discovery order [ext1, ext2] must produce "AB", proving ext1 runs first and ext2 sees
        // ext1's output rather than the original raw bytes
        BytesModelExtContext ext1 = config -> stream(replaceWith("A"));
        BytesModelExtContext ext2 = config -> handlerAppending("B", 1);

        BytesModelContext context = new BytesModelContext(mock(EngineContext.class), List.of(ext1, ext2));
        ModelHandler handler = context.supplyHandler(BytesModelConfig.builder().build());

        assertThat(handler, instanceOf(CoreExtModelHandler.class));

        ModelPipeline pipeline = handler.supplyDecoder(ModelTransform.NONE);
        byte[] bytes = "ignored".getBytes();
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("AB", dst.getStringWithoutLengthUtf8(0, result.produced()));
    }

    @Test
    public void shouldSupplyHandlerOncePerModelConstructionNotPerMessage()
    {
        AtomicInteger supplyCount = new AtomicInteger();
        BytesModelExtContext ext = config ->
        {
            supplyCount.incrementAndGet();
            return stream(BytesTransform.NONE);
        };

        BytesModelContext context = new BytesModelContext(mock(EngineContext.class), List.of(ext));
        ModelHandler handler = context.supplyHandler(BytesModelConfig.builder().build());

        assertEquals(1, supplyCount.get());

        byte[] bytes = "abc".getBytes();
        for (int i = 0; i < 3; i++)
        {
            ModelPipeline pipeline = handler.supplyDecoder(ModelTransform.NONE);
            UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);
            pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
                new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
        }

        // supplyHandler resolved the extension chain once, at model construction; three separate
        // per-stream decode pipelines all reused that same resolved chain
        assertEquals(1, supplyCount.get());
    }

    @Test
    public void shouldNotAffectStringModelInTheSameEngine()
    {
        EngineContext engine = mock(EngineContext.class);
        BytesModelExtContext bytesExt = config -> stream(replaceWith("REDACTED"));

        BytesModelContext bytesContext = new BytesModelContext(engine, List.of(bytesExt));
        StringModelContext stringContext = new StringModelContext(engine, List.of());

        ModelHandler bytesHandler = bytesContext.supplyHandler(BytesModelConfig.builder().build());
        ModelHandler stringHandler = stringContext.supplyHandler(StringModelConfig.builder().build());

        assertThat(bytesHandler, instanceOf(CoreExtModelHandler.class));
        assertThat(stringHandler, instanceOf(CoreModelHandler.class));

        ModelPipeline stringPipeline = stringHandler.supplyDecoder(ModelTransform.NONE);
        byte[] bytes = "unaffected".getBytes();
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[32]);

        ModelPipelineResult result = stringPipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("unaffected", dst.getStringWithoutLengthUtf8(0, result.produced()));
    }

    private static BytesModelExtHandler stream(
        BytesTransform transform)
    {
        return stream -> stream.transform(transform);
    }

    private static BytesTransform replaceWith(
        String value)
    {
        byte[] replacement = value.getBytes();
        return (src, index, length, dst, dstIndex) ->
        {
            dst.putBytes(dstIndex, new UnsafeBufferEx(replacement), 0, replacement.length);
            return replacement.length;
        };
    }

    private static BytesModelExtHandler handlerAppending(
        String suffix,
        int padding)
    {
        byte[] suffixBytes = suffix.getBytes();
        BytesTransform append = (value, index, length, dst, dstIndex) ->
        {
            dst.putBytes(dstIndex, value, index, length);
            dst.putBytes(dstIndex + length, new UnsafeBufferEx(suffixBytes), 0, suffixBytes.length);
            return length + suffixBytes.length;
        };
        return new BytesModelExtHandler()
        {
            @Override
            public BytesTransformable transform(
                BytesTransformable stream)
            {
                return stream.transform(append);
            }

            @Override
            public int padding()
            {
                return padding;
            }
        };
    }
}
