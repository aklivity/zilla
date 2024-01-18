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
package io.aklivity.zilla.runtime.types.json;

import java.net.URL;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.converter.Converter;
import io.aklivity.zilla.runtime.engine.converter.ConverterFactorySpi;
import io.aklivity.zilla.runtime.types.json.config.JsonConverterConfig;

public final class JsonConverterFactory implements ConverterFactorySpi
{
    @Override
    public String type()
    {
        return "json";
    }

    public URL schema()
    {
        return getClass().getResource("schema/json.schema.patch.json");
    }

    @Override
    public Converter createReader(
        ConverterConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new JsonReadConverter(JsonConverterConfig.class.cast(config), supplyCatalog);
    }

    @Override
    public Converter createWriter(
        ConverterConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new JsonWriteConverter(JsonConverterConfig.class.cast(config), supplyCatalog);
    }
}