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
package io.aklivity.zilla.runtime.types.json.internal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.function.LongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.converter.Converter;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.types.json.config.JsonConverterConfig;

public class JsonConverterFactoryTest
{
    @Test
    public void shouldCreateReader()
    {
        // GIVEN
        ConverterConfig converter = JsonConverterConfig.builder()
                .catalog()
                    .name("test0")
                    .build()
                .build();
        LongFunction<CatalogHandler> supplyCatalog = i -> new TestCatalogHandler(
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema("schema0")
                .build());
        JsonConverterFactory factory = new JsonConverterFactory();

        // WHEN
        Converter reader = factory.createReader(converter, supplyCatalog);

        // THEN
        assertThat(reader, instanceOf(JsonReadConverter.class));
    }

    @Test
    public void shouldCreateWriter()
    {
        // GIVEN
        ConverterConfig converter = JsonConverterConfig.builder()
                .catalog()
                    .name("test0")
                    .build()
                .build();
        LongFunction<CatalogHandler> supplyCatalog = i -> new TestCatalogHandler(
            TestCatalogOptionsConfig.builder()
                .id(1)
                .schema("schema0")
                .build());
        JsonConverterFactory factory = new JsonConverterFactory();

        // WHEN
        Converter writer = factory.createWriter(converter, supplyCatalog);

        // THEN
        assertThat(writer, instanceOf(JsonWriteConverter.class));
    }
}
