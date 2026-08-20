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
package io.aklivity.zilla.runtime.model.core.internal;

import java.util.List;

import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.config.model.core.BytesModelConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtHandler;

public class BytesModelContext implements ModelContext
{
    private final EngineContext context;
    private final List<BytesModelExtContext> exts;

    public BytesModelContext(
        EngineContext context,
        List<BytesModelExtContext> exts)
    {
        this.context = context;
        this.exts = exts;
    }

    @Override
    public ModelHandler supplyHandler(
        ModelConfig config)
    {
        BytesModelConfig options = BytesModelConfig.class.cast(config);
        boolean decodeLenient = config.validate.decode == ValidateMode.LENIENT;
        boolean encodeLenient = config.validate.encode == ValidateMode.LENIENT;

        // resolved once per model config, at handler construction -- there is no schema to resolve per,
        // so the same extension handlers extend every stream this handler serves
        List<BytesModelExtHandler> handlers = exts.stream().map(ext -> ext.supplyHandler(options)).toList();

        return handlers.isEmpty()
            ? new CoreModelHandler(context, BytesModel.NAME, BytesModelValidator.supplier(), decodeLenient, encodeLenient)
            : new BytesExtModelHandler(context, BytesModelValidator.supplier(), decodeLenient, encodeLenient, handlers);
    }
}
