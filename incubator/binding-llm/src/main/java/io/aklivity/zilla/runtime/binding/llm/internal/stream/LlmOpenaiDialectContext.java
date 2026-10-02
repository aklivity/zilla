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
import io.aklivity.zilla.runtime.binding.llm.internal.openai.LlmOpenaiRequestDecoder;
import io.aklivity.zilla.runtime.engine.EngineContext;

final class LlmOpenaiDialectContext implements LlmDialectContext
{
    private final LlmConfiguration config;
    private final EngineContext context;
    private final LlmContentCodecFactory codecs;
    private final LlmOpenaiRequestDecoder decoder;

    private LlmLegacyServerFactory server;
    private LlmLegacyClientFactory client;

    LlmOpenaiDialectContext(
        LlmConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.codecs = new LlmContentCodecFactory();
        this.decoder = new LlmOpenaiRequestDecoder();
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
                handler = new LlmOpenaiServerHandler(supplyServer(), supplyBinding(binding), decoder);
            }
            break;
        case CLIENT:
            if (NAME.equals(fixed))
            {
                handler = new LlmLegacyClientHandler(NAME, supplyClient(), supplyBinding(binding));
            }
            break;
        default:
            break;
        }

        return handler;
    }

    private LlmLegacyBindingConfig supplyBinding(
        LlmBindingConfig binding)
    {
        return new LlmLegacyBindingConfig(binding, context, codecs);
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
