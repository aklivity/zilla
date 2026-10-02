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

import java.util.Map;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectContext;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class LlmClientFactory implements LlmStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";

    private final BeginFW beginRO = new BeginFW();

    private final EngineContext context;
    private final Map<String, LlmDialectContext> dialects;
    private final Long2ObjectHashMap<LlmDialectHandler> bindings;
    private final int httpTypeId;
    private final int llmTypeId;

    public LlmClientFactory(
        EngineContext context,
        Map<String, LlmDialectContext> dialects)
    {
        this.context = context;
        this.dialects = dialects;
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.llmTypeId = context.supplyTypeId(LlmBinding.NAME);
    }

    @Override
    public int originTypeId()
    {
        return llmTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return httpTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        final LlmBindingConfig config = new LlmBindingConfig(binding, context);
        final String fixed = config.options.dialect;
        final LlmDialectContext dialect = fixed != null ? dialects.get(fixed) : null;
        final LlmDialectHandler handler = dialect != null ? dialect.attach(config) : null;

        if (handler != null)
        {
            bindings.put(binding.id, handler);
        }
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
        dialects.values().forEach(dialect -> dialect.detach(bindingId));
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final LlmDialectHandler handler = bindings.get(begin.routedId());

        return handler != null
            ? handler.newStream(msgTypeId, buffer, index, length, sender, JsonEnvelope.NONE)
            : null;
    }
}
