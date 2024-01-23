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
package io.aklivity.zilla.runtime.types.core.internal;

import java.net.URL;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.converter.Converter;
import io.aklivity.zilla.runtime.engine.converter.ConverterFactorySpi;
import io.aklivity.zilla.runtime.types.core.config.IntegerConverterConfig;

public class IntegerConverterFactory implements ConverterFactorySpi
{
    @Override
    public String type()
    {
        return "integer";
    }

    @Override
    public URL schema()
    {
        return getClass().getResource("schema/integer.schema.patch.json");
    }

    @Override
    public Converter createReader(
        ConverterConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return create(config);
    }

    @Override
    public Converter createWriter(
        ConverterConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return create(config);
    }

    private IntegerConverter create(
        ConverterConfig config)
    {
        return new IntegerConverter(IntegerConverterConfig.class.cast(config));
    }
}
