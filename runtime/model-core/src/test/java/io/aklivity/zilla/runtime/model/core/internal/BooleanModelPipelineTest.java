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
import io.aklivity.zilla.runtime.model.core.config.BooleanModelConfig;

public class BooleanModelPipelineTest
{
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
    public void shouldTransformFalseValue()
    {
        ModelHandler handler = handler();
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

<<<<<<< HEAD
        byte[] bytes = {0x01};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[8]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
=======
        byte[] bytes = {0x00};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[8]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
>>>>>>> origin/develop

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(1, result.produced());
    }

    @Test
    public void shouldRejectTooLong()
    {
        ModelHandler handler = handler();
        ModelPipeline pipeline = handler.supplyDecoder(ModelVisitor.NONE);

<<<<<<< HEAD
        byte[] bytes = {0x05};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[8]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
=======
        byte[] bytes = {0x01, 0x00};
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[8]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(bytes), 0, bytes.length, dst, 0, dst.capacity());
>>>>>>> origin/develop

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    private ModelHandler handler()
    {
        return new BooleanModelContext(context).supplyHandler(BooleanModelConfig.builder().build());
    }
}
