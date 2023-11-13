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
package io.aklivity.zilla.runtime.validator.json;

import java.net.URL;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactorySpi;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.validator.json.config.JsonValidatorConfig;

public final class JsonValidatorFactory implements ValidatorFactorySpi
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
    public ValueValidator createValueReader(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return createReader(config, supplyCatalog);
    }

    @Override
    public ValueValidator createValueWriter(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return createWriter(config, supplyCatalog);
    }

    @Override
    public FragmentValidator createFragmentReader(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return createReader(config, supplyCatalog);
    }

    @Override
    public FragmentValidator createFragmentWriter(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return createWriter(config, supplyCatalog);
    }

    private JsonReadValidator createReader(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new JsonReadValidator(JsonValidatorConfig.class.cast(config), supplyCatalog);
    }

    private JsonWriteValidator createWriter(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new JsonWriteValidator(JsonValidatorConfig.class.cast(config), supplyCatalog);
    }
}
