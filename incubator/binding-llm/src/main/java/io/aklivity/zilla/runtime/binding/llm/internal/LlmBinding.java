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

import java.net.URL;
import java.util.ServiceLoader;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectFactorySpi;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class LlmBinding implements Binding
{
    public static final String NAME = "llm";

    private final LlmConfiguration config;
    private final LlmSystemNamespaceGenerator generator;

    LlmBinding(
        LlmConfiguration config)
    {
        this.config = config;
        this.generator = new LlmSystemNamespaceGenerator();
    }

    @Override
    public String name()
    {
        return LlmBinding.NAME;
    }

    @Override
    public LlmBindingContext supply(
        EngineContext context)
    {
        return new LlmBindingContext(config, context);
    }

    @Override
    public URL system()
    {
        return generator.generate(ServiceLoader.load(LlmDialectFactorySpi.class));
    }
}
