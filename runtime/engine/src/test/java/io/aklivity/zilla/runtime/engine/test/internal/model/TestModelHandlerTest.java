/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.test.internal.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ValidateConfig;
import io.aklivity.zilla.runtime.engine.config.ValidateMode;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class TestModelHandlerTest
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMPLETE = 0x03;

    private final EngineContext context = mock(EngineContext.class);

    @Test
    public void shouldTransformWholeValue()
    {
        ModelPipeline pipeline = readPipeline(4);

        byte[] bytes = {1, 2, 3, 4};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(4, result.consumed());
        assertEquals(4, result.produced());
    }

    @Test
    public void shouldRejectWrongLength()
    {
        ModelPipeline pipeline = readPipeline(4);

        byte[] bytes = {1, 2, 3};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldForwardWrongLengthUnderLenientDecode()
    {
        ModelPipeline pipeline = lenientReadPipeline(4);

        byte[] bytes = {1, 2, 3};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(3, result.consumed());
        assertEquals(3, result.produced());
    }

    @Test
    public void shouldRejectWrongLengthUnderStrictEncodeWhenDecodeLenient()
    {
        TestModelConfig config = TestModelConfig.builder()
            .length(4)
            .validate(new ValidateConfig(ValidateMode.LENIENT, ValidateMode.STRICT))
            .build();
        ModelHandler handler = new TestModelContext(context).supplyHandler(config);

        byte[] bytes = {1, 2, 3};

        ModelPipeline decoder = handler.supplyDecoder(ModelVisitor.NONE);
        MutableDirectBuffer decodeDst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult decoded = decoder.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, decodeDst, 0, decodeDst.capacity());
        assertEquals(ModelStatus.COMPLETE, decoded.status());

        ModelPipeline encoder = handler.supplyEncoder(ModelVisitor.NONE);
        MutableDirectBuffer encodeDst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult encoded = encoder.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, encodeDst, 0, encodeDst.capacity());
        assertEquals(ModelStatus.REJECTED, encoded.status());
    }

    @Test
    public void shouldOverflowThenComplete()
    {
        ModelPipeline pipeline = writePipeline(4);

        byte[] bytes = {1, 2, 3, 4};
        MutableDirectBuffer src = new UnsafeBuffer(bytes);
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[4]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            src, 0, bytes.length, dst, 0, 2);
        assertEquals(ModelStatus.OVERFLOW, first.status());
        assertEquals(2, first.consumed());

        int progress = first.consumed();
        ModelPipelineResult second = pipeline.transform(0L, 0L, FLAGS_FIN,
            src, progress, bytes.length, dst, progress, bytes.length);
        assertEquals(ModelStatus.COMPLETE, second.status());
    }

    @Test
    public void shouldUnderflowOnPartialFragment()
    {
        ModelPipeline pipeline = readPipeline(4);

        byte[] head = {1, 2};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_INIT,
            new UnsafeBuffer(head), 0, head.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.UNDERFLOW, result.status());
        assertEquals(2, result.consumed());
    }

    @Test
    public void shouldOverflowWithEmptyDestination()
    {
        ModelPipeline pipeline = readPipeline(4);

        byte[] bytes = {1, 2, 3, 4};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[0]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, 0);

        assertEquals(ModelStatus.OVERFLOW, result.status());
        assertEquals(0, result.consumed());
        assertEquals(0, result.produced());
    }

    @Test
    public void shouldReportNoPadding()
    {
        ModelPipeline pipeline = readPipeline(4);

        byte[] bytes = {1, 2, 3, 4};
        assertEquals(0, pipeline.padding(new UnsafeBuffer(bytes), 0, bytes.length));
    }

    @Test
    public void shouldResetForNextValue()
    {
        ModelPipeline pipeline = readPipeline(4);

        byte[] bytes = {1, 2, 3, 4};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[16]);
        pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());
        pipeline.reset();

        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBuffer(bytes), 0, bytes.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, result.status());
    }

    @Test
    public void shouldReportIdentityWhenNoTransformConfigured()
    {
        ModelPipeline pipeline = readPipeline(4);

        assertTrue(pipeline.identity());
    }

    @Test
    public void shouldNotReportIdentityWhenTransformConfigured()
    {
        TestModelConfig config = TestModelConfig.builder()
            .length(4)
            .transformLength(8)
            .build();
        ModelHandler handler = new TestModelContext(context).supplyHandler(config);
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

        assertFalse(pipeline.identity());
    }

    private ModelPipeline readPipeline(
        int length)
    {
        ModelHandler handler = new TestModelContext(context).supplyHandler(config(length));
        return handler.supplyDecoder(ModelVisitor.NONE);
    }

    private ModelPipeline writePipeline(
        int length)
    {
        ModelHandler handler = new TestModelContext(context).supplyHandler(config(length));
        return handler.supplyEncoder();
    }

    private ModelPipeline lenientReadPipeline(
        int length)
    {
        TestModelConfig config = TestModelConfig.builder()
            .length(length)
            .validate(new ValidateConfig(ValidateMode.LENIENT, ValidateMode.LENIENT))
            .build();
        ModelHandler handler = new TestModelContext(context).supplyHandler(config);
        return handler.supplyDecoder(ModelVisitor.NONE);
    }

    private static TestModelConfig config(
        int length)
    {
        return TestModelConfig.builder().length(length).build();
    }
}
