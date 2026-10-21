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

import java.util.LinkedHashMap;
import java.util.Map;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectContext;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class LlmClientFactory implements LlmStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";

    private final BeginFW beginRO = new BeginFW();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();

    private final EngineContext context;
    private final Map<String, LlmDialectContext> dialects;
    private final Long2ObjectHashMap<LlmClientBinding> bindings;
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
        final Map<String, LlmDialectHandler> handlers = new LinkedHashMap<>();

        for (Map.Entry<String, LlmDialectContext> entry : dialects.entrySet())
        {
            final LlmDialectHandler handler = entry.getValue().attach(config);
            if (handler != null)
            {
                handlers.put(entry.getKey(), handler);
            }
        }

        if (!handlers.isEmpty())
        {
            bindings.put(binding.id, new LlmClientBinding(config.options.dialect, handlers));
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
        final LlmClientBinding binding = bindings.get(begin.routedId());

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final OctetsFW extension = begin.extension();
            final LlmBeginExFW llmBeginEx = extension.get(llmBeginExRO::tryWrap);
            final LlmDialectHandler handler = binding.resolve(llmBeginEx != null ? llmBeginEx.dialect().asString() : null);

            newStream = handler.newStream(msgTypeId, buffer, index, length, sender);
        }

        return newStream;
    }

    private static final class LlmClientBinding
    {
        private final Map<String, LlmDialectHandler> handlers;
        private final LlmDialectHandler fallback;

        private LlmClientBinding(
            String dialect,
            Map<String, LlmDialectHandler> handlers)
        {
            this.handlers = handlers;
            this.fallback = handlers.getOrDefault(dialect, handlers.values().iterator().next());
        }

        private LlmDialectHandler resolve(
            String inbound)
        {
            return handlers.getOrDefault(inbound, fallback);
        }
    }
}
