/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestModelContext;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class ModelTest
{
    @Test
    public void shouldCreateAndVerifyNoOpValueConverter()
    {
        ConverterHandler converter = ConverterHandler.NONE;

        assertEquals(1, converter.convert(new UnsafeBuffer(), 1, 1, (b, i, l) -> {}));
    }

    @Test
    public void shouldValidateWithoutFlag()
    {
        TestModelConfig modelConfig = TestModelConfig.builder()
            .length(4)
            .build();
        ModelContext context = new TestModelContext(mock(EngineContext.class));
        ValidatorHandler handler = context.supplyValidatorHandler(modelConfig);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 0, 42};
        data.wrap(bytes, 0, bytes.length);
        assertTrue(handler.validate(data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
