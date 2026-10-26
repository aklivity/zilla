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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmOpenaiResponsesDialectFactorySpi.NAME;

import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectContext;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.engine.EngineContext;

final class LlmOpenaiResponsesDialectContext implements LlmDialectContext
{
    private final EngineContext context;

    private LlmOpenaiResponsesServerFactory server;
    private LlmOpenaiResponsesClientFactory client;

    LlmOpenaiResponsesDialectContext(
        EngineContext context)
    {
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
            if (binding.serves(NAME))
            {
                handler = supplyServer();
                server.attach(binding);
            }
            break;
        case CLIENT:
            if (fixed != null && binding.serves(NAME))
            {
                supplyClient().attach(binding);
                handler = client;
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

    private LlmOpenaiResponsesServerFactory supplyServer()
    {
        if (server == null)
        {
            server = new LlmOpenaiResponsesServerFactory(context);
        }
        return server;
    }

    private LlmOpenaiResponsesClientFactory supplyClient()
    {
        if (client == null)
        {
            client = new LlmOpenaiResponsesClientFactory(context);
        }
        return client;
    }
}
