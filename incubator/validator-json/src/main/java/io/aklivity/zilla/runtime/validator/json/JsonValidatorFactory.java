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
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactorySpi;
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
    public Validator createReadValidator(
        ValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new JsonReadValidator(JsonValidatorConfig.class.cast(config), resolveId, supplyCatalog);
    }

    @Override
    public Validator createWriteValidator(
        ValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new JsonWriteValidator(JsonValidatorConfig.class.cast(config), resolveId, supplyCatalog);
    }
}
