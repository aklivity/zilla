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

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialect;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

final class LlmLegacyServerHandler implements LlmDialectHandler
{
    private final String dialect;
    private final LlmLegacyServerFactory factory;
    private final LlmBindingConfig binding;

    LlmLegacyServerHandler(
        String dialect,
        LlmLegacyServerFactory factory,
        LlmBindingConfig binding)
    {
        this.dialect = dialect;
        this.factory = factory;
        this.binding = binding;
    }

    @Override
    public boolean detect(
        JsonEnvelope headers)
    {
        final LlmLegacyDialect legacy = binding.dialectNamed(dialect);

        return legacy != null && legacy.detect(headers);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender,
        JsonEnvelope headers)
    {
        final LlmLegacyDialect legacy = binding.dialectNamed(dialect);

        return factory.newStream(binding, legacy, (LlmModelEnvelope) headers, buffer, index, length, sender);
    }
}
