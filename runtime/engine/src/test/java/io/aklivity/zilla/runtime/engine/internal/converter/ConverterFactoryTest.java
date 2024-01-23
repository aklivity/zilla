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
package io.aklivity.zilla.runtime.engine.internal.converter;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.converter.Converter;
import io.aklivity.zilla.runtime.engine.converter.ConverterContext;
import io.aklivity.zilla.runtime.engine.converter.ConverterFactory;
import io.aklivity.zilla.runtime.engine.test.internal.converter.TestConverter;
import io.aklivity.zilla.runtime.engine.test.internal.converter.TestConverterContext;
import io.aklivity.zilla.runtime.engine.test.internal.converter.TestConverterHandler;
import io.aklivity.zilla.runtime.engine.test.internal.converter.config.TestConverterConfig;

public class ConverterFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        ConverterFactory factory = ConverterFactory.instantiate();
        Converter converter = factory.create("test", config);

        TestConverterConfig converterConfig = TestConverterConfig.builder().length(4).build();
        ConverterContext context = new TestConverterContext(mock(EngineContext.class));

        assertThat(converter, instanceOf(TestConverter.class));
        assertThat(context.supplyReadHandler(converterConfig), instanceOf(TestConverterHandler.class));
        assertThat(context.supplyWriteHandler(converterConfig), instanceOf(TestConverterHandler.class));
    }
}
