/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.converter.core;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.function.LongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.converter.core.config.IntegerConverterConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.converter.Converter;

public class IntegerConverterFactoryTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateReader()
    {
        // GIVEN
        ConverterConfig converter = new IntegerConverterConfig();
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        IntegerConverterFactory factory = new IntegerConverterFactory();

        // WHEN
        Converter reader = factory.createReader(converter, supplyCatalog);

        // THEN
        assertThat(reader, instanceOf(IntegerConverter.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateWriter()
    {
        // GIVEN
        ConverterConfig converter = new IntegerConverterConfig();
        LongFunction<CatalogHandler> supplyCatalog = mock(LongFunction.class);
        IntegerConverterFactory factory = new IntegerConverterFactory();

        // WHEN
        Converter writer = factory.createWriter(converter, supplyCatalog);

        // THEN
        assertThat(writer, instanceOf(IntegerConverter.class));
    }
}
