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
package io.aklivity.zilla.runtime.engine.test.internal.converter;

import java.net.URL;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.converter.Converter;
import io.aklivity.zilla.runtime.engine.converter.ConverterFactorySpi;
import io.aklivity.zilla.runtime.engine.test.internal.converter.config.TestConverterConfig;

public class TestConverterFactory implements ConverterFactorySpi
{
    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public URL schema()
    {
        return getClass().getResource("test.schema.patch.json");
    }

    @Override
    public Converter createReader(
        ConverterConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return create(config, supplyCatalog);
    }

    @Override
    public Converter createWriter(
        ConverterConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return create(config, supplyCatalog);
    }

    private TestConverter create(
        ConverterConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new TestConverter(TestConverterConfig.class.cast(config), supplyCatalog);
    }
}