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
package io.aklivity.zilla.runtime.model.core.internal;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.model.core.config.Int64ModelConfig;

public class Int64ModelContext implements ModelContext
{
    private final EngineContext context;

    public Int64ModelContext(
        EngineContext context)
    {
        this.context = context;
    }

    @Override
    public ConverterHandler supplyReadConverterHandler(
        ModelConfig config)
    {
        return supply(config);
    }

    @Override
    public ConverterHandler supplyWriteConverterHandler(
        ModelConfig config)
    {
        return supply(config);
    }

    @Override
    public ValidatorHandler supplyValidatorHandler(
        ModelConfig config)
    {
        return new Int64ValidatorHandler(Int64ModelConfig.class.cast(config), context);
    }

    private Int64ConverterHandler supply(
        ModelConfig config)
    {
        return new Int64ConverterHandler(Int64ModelConfig.class.cast(config), context);
    }
}
