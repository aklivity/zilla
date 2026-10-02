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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.LinkedHashMap;
import java.util.Map;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectContext;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.codec.LlmContentCodecFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class LlmServerFactory implements LlmStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";

    private final BeginFW beginRO = new BeginFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();

    private final EngineContext context;
    private final LlmContentCodecFactory codecs;
    private final Map<String, LlmDialectContext> dialects;
    private final Long2ObjectHashMap<LlmServerBinding> bindings;
    private final int httpTypeId;
    private final int llmTypeId;

    public LlmServerFactory(
        EngineContext context,
        Map<String, LlmDialectContext> dialects)
    {
        this.context = context;
        this.codecs = new LlmContentCodecFactory();
        this.dialects = dialects;
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.llmTypeId = context.supplyTypeId(LlmBinding.NAME);
    }

    @Override
    public int originTypeId()
    {
        return httpTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return llmTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        final LlmBindingConfig config = new LlmBindingConfig(binding, context, codecs);
        final Map<String, LlmDialectHandler> handlers = new LinkedHashMap<>();

        for (Map.Entry<String, LlmDialectContext> entry : dialects.entrySet())
        {
            final LlmDialectHandler handler = entry.getValue().attach(config);
            if (handler != null)
            {
                handlers.put(entry.getKey(), handler);
            }
        }

        bindings.put(binding.id, new LlmServerBinding(config.options.dialect, handlers));
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
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final LlmServerBinding binding = bindings.get(begin.routedId());

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final LlmModelEnvelope envelope = new LlmModelEnvelope();

            if (extractHeaders(begin, envelope))
            {
                final LlmDialectHandler handler = binding.resolve(envelope);

                if (handler != null)
                {
                    newStream = handler.newStream(msgTypeId, buffer, index, length, network, envelope);
                }
            }
        }

        return newStream;
    }

    private boolean extractHeaders(
        BeginFW begin,
        LlmModelEnvelope envelope)
    {
        final OctetsFW extension = begin.extension();
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

        if (httpBeginEx != null)
        {
            httpBeginEx.headers().forEach(h -> envelope.set(h.name().asString(), asBuffer(h.value().asString())));
        }

        return httpBeginEx != null;
    }

    private static DirectBufferEx asBuffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }

    private static final class LlmServerBinding
    {
        private final String dialect;
        private final Map<String, LlmDialectHandler> handlers;

        private LlmServerBinding(
            String dialect,
            Map<String, LlmDialectHandler> handlers)
        {
            this.dialect = dialect;
            this.handlers = handlers;
        }

        private LlmDialectHandler resolve(
            JsonEnvelope headers)
        {
            return dialect != null ? handlers.get(dialect) : detect(headers);
        }

        private LlmDialectHandler detect(
            JsonEnvelope headers)
        {
            LlmDialectHandler matched = null;
            boolean ambiguous = false;

            for (LlmDialectHandler handler : handlers.values())
            {
                if (handler.detect(headers))
                {
                    ambiguous |= matched != null;
                    matched = handler;
                }
            }

            return ambiguous ? null : matched;
        }
    }
}
