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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Extracts Anthropic's {@code usage} object, reported across two documents: {@code message_start} carries
 * it nested under {@code message.usage.*} (input and cache tokens, already final), {@code message_delta}
 * carries it as a top-level {@code usage.*} (the final {@code output_tokens}, and -- defensively, in case a
 * future API revision repeats them there -- the cache fields too). A non-streaming whole message carries
 * the same top-level {@code usage.*} shape. Anthropic reports no reasoning-token or total-token breakdown of
 * its own, so {@link #reasoningTokens(int)}/{@link #totalTokens(int)} are never called by this dialect.
 */
final class LlmAnthropicUsageExtractTransform extends LlmUsageExtractTransform
{
    LlmAnthropicUsageExtractTransform(
        JsonEnvelope envelope)
    {
        super(envelope);
    }

    @Override
    protected void onField(
        String fieldPath,
        JsonSource source,
        JsonEvent event)
    {
        if (event != JsonEvent.VALUE_NUMBER)
        {
            return;
        }

        switch (fieldPath)
        {
        case "message.usage.input_tokens":
        case "usage.input_tokens":
            inputTokens(source.getInt());
            break;
        case "message.usage.cache_creation_input_tokens":
        case "usage.cache_creation_input_tokens":
            cacheWriteTokens(source.getInt());
            break;
        case "message.usage.cache_read_input_tokens":
        case "usage.cache_read_input_tokens":
            cacheReadTokens(source.getInt());
            break;
        case "usage.output_tokens":
            outputTokens(source.getInt());
            break;
        default:
            break;
        }
    }
}
