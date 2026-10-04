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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmOpenaiDialectFactorySpi.NAME;

import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectContext;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.codec.LlmContentCodecFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmLegacyBindingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;

final class LlmOpenaiDialectContext implements LlmDialectContext
{
    private final LlmConfiguration config;
    private final EngineContext context;
    private final LlmContentCodecFactory codecs;

    private LlmOpenaiServerFactory server;
    private LlmOpenaiClientFactory client;
    private LlmLegacyClientFactory legacy;

    LlmOpenaiDialectContext(
        LlmConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.codecs = new LlmContentCodecFactory();
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
            if (fixed == null || NAME.equals(fixed))
            {
                handler = supplyServer();
                server.attach(binding);
            }
            break;
        case CLIENT:
            if (NAME.equals(fixed))
            {
                supplyClient().attach(binding);
                handler = new LlmOpenaiClientHandler(
                    client,
                    new LlmLegacyClientHandler(NAME, supplyLegacy(), new LlmLegacyBindingConfig(binding, context, codecs)));
            }
            break;
        default:
            break;
        }

        return handler;
    }

    @Override
    public void detach(
        long bindingId)
    {
        if (server != null)
        {
            server.detach(bindingId);
        }

        if (client != null)
        {
            client.detach(bindingId);
        }
    }

    private LlmOpenaiServerFactory supplyServer()
    {
        if (server == null)
        {
            server = new LlmOpenaiServerFactory(context);
        }
        return server;
    }

    private LlmLegacyClientFactory supplyLegacy()
    {
        if (legacy == null)
        {
            legacy = new LlmLegacyClientFactory(config, context);
        }
        return legacy;
    }

    private LlmOpenaiClientFactory supplyClient()
    {
        if (client == null)
        {
            client = new LlmOpenaiClientFactory(context);
        }
        return client;
    }
}
