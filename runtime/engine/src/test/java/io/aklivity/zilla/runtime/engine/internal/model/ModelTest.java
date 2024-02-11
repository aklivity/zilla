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
package io.aklivity.zilla.runtime.engine.internal.model;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestConverterHandler;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class ModelTest
{
    @Test
    public void shouldValidateWithoutFlag()
    {
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        ModelConfig config = TestModelConfig.builder()
            .length(4)
            .catalog()
                .name("test0")
                    .schema()
                    .id(1)
                    .build()
                .build()
            .read(true)
            .build();
        ConverterHandler handler = new TestConverterHandler(TestModelConfig.class.cast(config), supplyCatalog);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 0, 42};
        data.wrap(bytes, 0, bytes.length);
        assertEquals(data.capacity(), handler.convert(data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
