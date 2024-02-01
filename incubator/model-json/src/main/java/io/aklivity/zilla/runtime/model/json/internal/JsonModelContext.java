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
package io.aklivity.zilla.runtime.model.json.internal;

import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonModelContext implements ModelContext
{
    private final LongFunction<CatalogHandler> supplyCatalog;

    public JsonModelContext(EngineContext context)
    {
        this.supplyCatalog = context::supplyCatalog;
    }

    @Override
    public ConverterHandler supplyReadConverterHandler(
        ModelConfig config)
    {
        return new JsonReadConverterHandler(JsonModelConfig.class.cast(config), supplyCatalog);
    }

    @Override
    public ConverterHandler supplyWriteConverterHandler(
        ModelConfig config)
    {
        return new JsonWriteConverterHandler(JsonModelConfig.class.cast(config), supplyCatalog);
    }

    @Override
    public ValidatorHandler supplyValidatorHandler(
        ModelConfig config)
    {
        return new JsonValidatorHandler(JsonModelConfig.class.cast(config), supplyCatalog);
    }
}
