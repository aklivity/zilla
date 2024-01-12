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

import java.util.function.LongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.converter.Converter;
import io.aklivity.zilla.runtime.engine.converter.ConverterFactory;
import io.aklivity.zilla.runtime.engine.test.internal.converter.TestConverter;
import io.aklivity.zilla.runtime.engine.test.internal.converter.config.TestConverterConfig;

public class ConverterFactoryTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateReader()
    {
        // GIVEN
        ConverterConfig config = TestConverterConfig.builder()
                .length(0)
                .catalog()
                    .name("test0")
                        .schema()
                        .id(1)
                        .build()
                    .build()
                .read(true)
                .build();
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        ConverterFactory factory = ConverterFactory.instantiate();

        // WHEN
        Converter reader = factory.createReader(config, supplyCatalog);

        // THEN
        assertThat(reader, instanceOf(TestConverter.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateWriter()
    {
        // GIVEN
        ConverterConfig config = TestConverterConfig.builder()
                .length(0)
                .catalog()
                    .name("test0")
                        .schema()
                        .id(1)
                        .build()
                    .build()
                .read(false)
                .build();
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        ConverterFactory factory = ConverterFactory.instantiate();

        // WHEN
        Converter writer = factory.createWriter(config, supplyCatalog);

        // THEN
        assertThat(writer, instanceOf(TestConverter.class));
    }
}
