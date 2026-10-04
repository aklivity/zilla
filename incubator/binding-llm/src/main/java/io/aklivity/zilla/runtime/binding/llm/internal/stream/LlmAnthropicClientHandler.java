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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAnthropicDialectFactorySpi.NAME;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

final class LlmAnthropicClientHandler implements LlmDialectHandler
{
    private final BeginFW beginRO = new BeginFW();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();

    private final LlmDialectHandler canonical;
    private final LlmDialectHandler legacy;

    LlmAnthropicClientHandler(
        LlmDialectHandler canonical,
        LlmDialectHandler legacy)
    {
        this.canonical = canonical;
        this.legacy = legacy;
    }

    @Override
    public boolean detect(
        JsonEnvelope headers)
    {
        return false;
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
        final LlmBeginExFW llmBeginEx = begin.extension().get(llmBeginExRO::tryWrap);
        final boolean sections = llmBeginEx != null &&
            (NAME.equals(llmBeginEx.dialect().asString()) || llmBeginEx.model().asString() != null);

        return (sections ? canonical : legacy).newStream(msgTypeId, buffer, index, length, sender);
    }
}
