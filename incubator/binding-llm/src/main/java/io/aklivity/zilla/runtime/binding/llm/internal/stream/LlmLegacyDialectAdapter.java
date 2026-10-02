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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;
import static io.aklivity.zilla.config.engine.KindConfig.SERVER;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectContext;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;

public final class LlmLegacyDialectAdapter implements LlmDialect
{
    private final String name;
    private final LlmConfiguration config;

    public LlmLegacyDialectAdapter(
        String name,
        LlmConfiguration config)
    {
        this.name = name;
        this.config = config;
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public LlmDialectContext supply(
        EngineContext context)
    {
        return new LlmLegacyDialectAdapterContext(name, config, context);
    }

    private static final class LlmLegacyDialectAdapterContext implements LlmDialectContext
    {
        private final String name;
        private final LlmConfiguration config;
        private final EngineContext context;

        private LlmLegacyServerFactory server;
        private LlmLegacyClientFactory client;

        private LlmLegacyDialectAdapterContext(
            String name,
            LlmConfiguration config,
            EngineContext context)
        {
            this.name = name;
            this.config = config;
            this.context = context;
        }

        @Override
        public LlmDialectHandler attach(
            LlmBindingConfig binding)
        {
            final String fixed = binding.options.dialect;

            LlmDialectHandler handler = null;

            switch (binding.kind)
            {
            case SERVER:
                if (fixed == null || fixed.equals(name))
                {
                    handler = new LlmLegacyServerHandler(name, supplyServer(), binding);
                }
                break;
            case CLIENT:
                if (name.equals(fixed))
                {
                    handler = new LlmLegacyClientHandler(name, supplyClient(), binding);
                }
                break;
            default:
                break;
            }

            return handler;
        }

        private LlmLegacyServerFactory supplyServer()
        {
            if (server == null)
            {
                server = new LlmLegacyServerFactory(config, context);
            }
            return server;
        }

        private LlmLegacyClientFactory supplyClient()
        {
            if (client == null)
            {
                client = new LlmLegacyClientFactory(config, context);
            }
            return client;
        }
    }
}
