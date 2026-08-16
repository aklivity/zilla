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
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.model.core.ext.StringController;
import io.aklivity.zilla.runtime.model.core.ext.StringEvent;
import io.aklivity.zilla.runtime.model.core.ext.StringModelExtContext;
import io.aklivity.zilla.runtime.model.core.ext.StringModelExtHandler;
import io.aklivity.zilla.runtime.model.core.ext.StringSink;
import io.aklivity.zilla.runtime.model.core.ext.StringSource;
import io.aklivity.zilla.runtime.model.core.ext.StringTransform;
import io.aklivity.zilla.runtime.model.core.ext.StringTransformable;

public class StringModelContextTest
{
    private static final int FLAGS_COMPLETE = 0x03;

    @Test
    public void shouldPassThroughUnmodifiedWithZeroExtensions()
    {
        StringModelContext context = new StringModelContext(mock(EngineContext.class), List.of());
        ModelHandler handler = context.supplyHandler(StringModelConfig.builder().build());

        assertThat(handler, instanceOf(CoreModelHandler.class));

        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE);
        byte[] bytes = "hello".getBytes();
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("hello", dst.getStringWithoutLengthUtf8(0, result.produced()));
    }

    @Test
    public void shouldComposeMultipleExtensionsInDiscoveryOrderAheadOfModelsOwnProcessing()
    {
        // ext1 replaces the value with "A" entirely, ignoring input; ext2 appends "B" to whatever it
        // receives; discovery order [ext1, ext2] must produce "AB", proving ext1 runs first and ext2 sees
        // ext1's output rather than the original raw bytes
        StringModelExtContext ext1 = config -> stream(replaceWith("A"));
        StringModelExtContext ext2 = config -> handlerAppending("B", 1);

        StringModelContext context = new StringModelContext(mock(EngineContext.class), List.of(ext1, ext2));
        ModelHandler handler = context.supplyHandler(StringModelConfig.builder().build());

        assertThat(handler, instanceOf(StringExtModelHandler.class));

        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE);
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
        StringModelExtContext ext = config ->
        {
            supplyCount.incrementAndGet();
            return stream(StringTransform.NONE);
        };

        StringModelContext context = new StringModelContext(mock(EngineContext.class), List.of(ext));
        ModelHandler handler = context.supplyHandler(StringModelConfig.builder().build());

        assertEquals(1, supplyCount.get());

        byte[] bytes = "abc".getBytes();
        for (int i = 0; i < 3; i++)
        {
            ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE);
            UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);
            pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
                new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
        }

        // supplyHandler resolved the extension chain once, at model construction; three separate
        // per-stream decode pipelines all reused that same resolved chain
        assertEquals(1, supplyCount.get());
    }

    @Test
    public void shouldNotAffectBytesModelInTheSameEngine()
    {
        EngineContext engine = mock(EngineContext.class);
        StringModelExtContext stringExt = config -> stream(replaceWith("REDACTED"));

        StringModelContext stringContext = new StringModelContext(engine, List.of(stringExt));
        BytesModelContext bytesContext = new BytesModelContext(engine, List.of());

        ModelHandler stringHandler = stringContext.supplyHandler(StringModelConfig.builder().build());
        ModelHandler bytesHandler = bytesContext.supplyHandler(BytesModelConfig.builder().build());

        assertThat(stringHandler, instanceOf(StringExtModelHandler.class));
        assertThat(bytesHandler, instanceOf(CoreModelHandler.class));

        ModelPipeline bytesPipeline = bytesHandler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE);
        byte[] bytes = { 1, 2, 3, 4 };
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult result = bytesPipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
    }

    private static StringModelExtHandler stream(
        StringTransform transform)
    {
        return new StringModelExtHandler()
        {
            @Override
            public <T extends StringTransformable<T>> T decode(
                T stream)
            {
                return stream.transform(transform);
            }
        };
    }

    private static StringTransform replaceWith(
        String value)
    {
        return new Substitute(value);
    }

    private static StringModelExtHandler handlerAppending(
        String suffix,
        int padding)
    {
        StringTransform append = new Suffix(suffix);
        return new StringModelExtHandler()
        {
            @Override
            public <T extends StringTransformable<T>> T decode(
                T stream)
            {
                return stream.transform(append);
            }

            @Override
            public int decodePadding()
            {
                return padding;
            }
        };
    }

    // drops the value's own bytes and emits its replacement once, at value end
    private static final class Substitute implements StringTransform
    {
        private final UnsafeBufferEx value;
        private final StringSource source;

        private Substitute(
            String value)
        {
            this.value = new UnsafeBufferEx(value.getBytes());
            this.source = () -> this.value;
        }

        @Override
        public ModelStatus transform(
            StringController control,
            StringSource source,
            StringEvent event,
            StringSink sink)
        {
            ModelStatus status = ModelStatus.OK;
            if (event == StringEvent.END_VALUE)
            {
                status = sink.transform(control, this.source, StringEvent.SEGMENT);
                if (status == ModelStatus.OK)
                {
                    status = sink.transform(control, source, event);
                }
            }
            else if (event == StringEvent.START_VALUE)
            {
                status = sink.transform(control, source, event);
            }
            return status;
        }
    }

    // forwards every segment, then emits its own suffix once, at value end
    private static final class Suffix implements StringTransform
    {
        private final UnsafeBufferEx suffix;
        private final StringSource source;

        private Suffix(
            String suffix)
        {
            this.suffix = new UnsafeBufferEx(suffix.getBytes());
            this.source = () -> this.suffix;
        }

        @Override
        public ModelStatus transform(
            StringController control,
            StringSource source,
            StringEvent event,
            StringSink sink)
        {
            ModelStatus status = ModelStatus.OK;
            if (event == StringEvent.END_VALUE)
            {
                status = sink.transform(control, this.source, StringEvent.SEGMENT);
                if (status == ModelStatus.OK)
                {
                    status = sink.transform(control, source, event);
                }
            }
            else
            {
                status = sink.transform(control, source, event);
            }
            return status;
        }
    }
}
