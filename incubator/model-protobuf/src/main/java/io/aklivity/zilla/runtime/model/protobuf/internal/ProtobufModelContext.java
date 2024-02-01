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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufModelContext implements ModelContext
{
    private final LongFunction<CatalogHandler> supplyCatalog;

    public ProtobufModelContext(
        EngineContext context)
    {
        this.supplyCatalog = context::supplyCatalog;
    }

    @Override
    public ConverterHandler supplyReadConverterHandler(
        ModelConfig config)
    {
        return new ProtobufReadConverterHandler(ProtobufModelConfig.class.cast(config), supplyCatalog);
    }

    @Override
    public ConverterHandler supplyWriteConverterHandler(
        ModelConfig config)
    {
        return new ProtobufWriteConverterHandler(ProtobufModelConfig.class.cast(config), supplyCatalog);
    }
}
