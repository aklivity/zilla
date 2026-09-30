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
package io.aklivity.zilla.runtime.binding.llm.internal;

import java.util.EnumMap;
import java.util.Map;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

final class LlmBindingContext implements BindingContext
{
    private final Map<KindConfig, BindingHandler> handlers;

    LlmBindingContext(
        LlmConfiguration config,
        EngineContext context)
    {
        this.handlers = new EnumMap<>(KindConfig.class);
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        return handlers.get(binding.kind);
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), handlers);
    }
}
