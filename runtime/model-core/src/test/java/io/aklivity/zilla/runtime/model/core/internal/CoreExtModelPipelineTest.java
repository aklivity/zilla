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
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtHandler;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransform;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransformable;

public class CoreExtModelPipelineTest
{
    // Reject-on-omit, fragment accumulation, and OVERFLOW/drain are covered end-to-end by CoreModelIT's
    // client.received.bytes.ext.* scenarios, which drive a real engine with a test-only
    // BytesModelExtFactorySpi installed. Summed padding across multiple simultaneously installed
    // extensions has no realistic wire-level equivalent (no single real extension composes with itself
    // twice), so it stays here as the one case this class still needs to cover.
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
