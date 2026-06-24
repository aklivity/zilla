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
import io.aklivity.zilla.runtime.engine.config.ValidateConfig;
import io.aklivity.zilla.runtime.engine.config.ValidateMode;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class CoreModelLenientTest
{
    private static final ValidateConfig LENIENT =
        new ValidateConfig(ValidateMode.LENIENT, ValidateMode.LENIENT);
    private static final ValidateConfig STRICT =
        new ValidateConfig(ValidateMode.STRICT, ValidateMode.STRICT);
    private static final ValidateConfig DECODE_LENIENT_ENCODE_STRICT =
        new ValidateConfig(ValidateMode.LENIENT, ValidateMode.STRICT);

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    // STRICT (default): an out-of-range value (structurally valid, constraint violated) is rejected.
    @Test
    public void shouldRejectConstraintViolationUnderStrict()
    {
        ModelHandler handler = new Int32ModelContext(context).supplyHandler(
            Int32ModelConfig.builder().format("text").max(10).validate(STRICT).build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "42".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    // LENIENT: the SAME out-of-range value passes through unchanged.
    @Test
    public void shouldPassConstraintViolationThroughUnderLenient()
    {
        ModelHandler handler = new Int32ModelContext(context).supplyHandler(
            Int32ModelConfig.builder().format("text").max(10).validate(LENIENT).build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "42".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(bytes.length, result.produced());
        assertEquals("42", dst.getStringWithoutLengthUtf8(0, result.produced()));
    }

    // A genuine parse failure (non-numeric bytes) rejects in BOTH modes — never relaxed.
    @Test
    public void shouldRejectMalformedUnderStrict()
    {
        ModelHandler handler = new Int32ModelContext(context).supplyHandler(
            Int32ModelConfig.builder().format("text").validate(STRICT).build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "12x".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldRejectMalformedUnderLenient()
    {
        ModelHandler handler = new Int32ModelContext(context).supplyHandler(
            Int32ModelConfig.builder().format("text").validate(LENIENT).build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "12x".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    // decode vs encode independence: {decode: lenient, encode: strict} relaxes only the read side.
    @Test
    public void shouldRelaxDecodeOnlyWhenEncodeStrict()
    {
        ModelHandler handler = new Int32ModelContext(context).supplyHandler(
            Int32ModelConfig.builder().format("text").max(10).validate(DECODE_LENIENT_ENCODE_STRICT).build());

        byte[] bytes = "42".getBytes();

        ModelPipeline decoder = handler.supplyDecoder(ModelVisitor.NONE);
        MutableDirectBuffer decodeDst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult decoded = decoder.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, decodeDst, 0, decodeDst.capacity());
        assertEquals(ModelStatus.COMPLETE, decoded.status());

        ModelPipeline encoder = handler.supplyEncoder(ModelVisitor.NONE);
        MutableDirectBuffer encodeDst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult encoded = encoder.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, encodeDst, 0, encodeDst.capacity());
        assertEquals(ModelStatus.REJECTED, encoded.status());
    }

    // String constraint (maxLength) violation: rejected STRICT, passed through LENIENT; the encoding itself
    // is valid UTF-8, so this is a semantic INVALID rather than a structural MALFORMED.
    @Test
    public void shouldRejectStringLengthViolationUnderStrict()
    {
        ModelHandler handler = new StringModelContext(context).supplyHandler(
            StringModelConfig.builder().maxLength(2).validate(STRICT).build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "hello".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldPassStringLengthViolationThroughUnderLenient()
    {
        ModelHandler handler = new StringModelContext(context).supplyHandler(
            StringModelConfig.builder().maxLength(2).validate(LENIENT).build());
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        byte[] bytes = "hello".getBytes();
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("hello", dst.getStringWithoutLengthUtf8(0, result.produced()));
    }
}
