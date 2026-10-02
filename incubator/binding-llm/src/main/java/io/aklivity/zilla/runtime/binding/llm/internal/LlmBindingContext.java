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

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;
import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static io.aklivity.zilla.config.engine.KindConfig.SERVER;

import java.util.LinkedHashMap;
import java.util.Map;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectContext;
import io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmClientFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmProxyFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmServerFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmStreamFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

final class LlmBindingContext implements BindingContext
{
    private final Map<KindConfig, LlmStreamFactory> factories;

    LlmBindingContext(
        LlmConfiguration config,
        EngineContext context,
        Map<String, LlmDialect> dialects)
    {
        final Map<String, LlmDialectContext> dialectContexts = new LinkedHashMap<>();
        dialects.forEach((name, dialect) -> dialectContexts.put(name, dialect.supply(context)));

        this.factories = Map.of(
            SERVER, new LlmServerFactory(context, dialectContexts),
            CLIENT, new LlmClientFactory(context, dialectContexts),
            PROXY, new LlmProxyFactory(config, context));
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        LlmStreamFactory factory = factories.get(binding.kind);

        if (factory != null)
        {
            factory.attach(binding);
        }

        return factory;
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        LlmStreamFactory factory = factories.get(binding.kind);

        if (factory != null)
        {
            factory.detach(binding.id);
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), factories);
    }
}
