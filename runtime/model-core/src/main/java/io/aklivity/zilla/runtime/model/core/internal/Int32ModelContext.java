/*
 * Copyright 2021-2024 Aklivity Inc
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
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;

public class Int32ModelContext implements ModelContext
{
    private final EngineContext context;

    public Int32ModelContext(
        EngineContext context)
    {
        this.context = context;
    }

    @Override
    public ModelHandler supplyHandler(
        ModelConfig config)
    {
        return supplyCoreHandler(config);
    }

    private CoreModelHandler supplyCoreHandler(
        ModelConfig config)
    {
        return new CoreModelHandler(context, Int32Model.NAME,
            Int32ModelValidator.supplier(Int32ModelConfig.class.cast(config)));
    }
}
