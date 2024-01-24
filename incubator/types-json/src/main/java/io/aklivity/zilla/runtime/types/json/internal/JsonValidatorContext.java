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

import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.ValidatorContext;
import io.aklivity.zilla.runtime.engine.validator.ValidatorHandler;
import io.aklivity.zilla.runtime.types.json.config.JsonValidatorConfig;

public class JsonValidatorContext implements ValidatorContext
{
    private final LongFunction<CatalogHandler> supplyCatalog;

    public JsonValidatorContext(
        EngineContext context)
    {
        this.supplyCatalog = context::supplyCatalog;
    }

    @Override
    public ValidatorHandler supplyHandler(
        ValidatorConfig config)
    {
        return new JsonValidatorHandler(JsonValidatorConfig.class.cast(config), supplyCatalog);
    }
}
