/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.classify.internal;

import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.model.classify.ClassifyModelConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;

public final class ClassifyModelContext implements ModelContext
{
    private final EngineContext context;
    private final ClassifyModelConfiguration config;

    ClassifyModelContext(
        EngineContext context,
        ClassifyModelConfiguration config)
    {
        this.context = context;
        this.config = config;
    }

    @Override
    public ModelHandler supplyHandler(
        ModelConfig config)
    {
        ClassifyModelConfig options = ClassifyModelConfig.class.cast(config);

        return new ClassifyModelHandlerImpl(context, options, this.config.maxLength());
    }
}
