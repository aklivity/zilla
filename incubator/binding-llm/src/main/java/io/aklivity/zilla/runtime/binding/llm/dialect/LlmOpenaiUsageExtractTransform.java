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
 * Extracts OpenAI's {@code usage} object -- {@code prompt_tokens}, {@code completion_tokens},
 * {@code total_tokens}, and the nested {@code prompt_tokens_details.cached_tokens}/
 * {@code completion_tokens_details.reasoning_tokens} -- reported once, in a dedicated trailing chunk whose
 * {@code choices} array is empty. OpenAI reports no cache-write cost of its own, so
 * {@link #cacheWriteTokens(int)} is never called by this dialect.
 */
final class LlmOpenaiUsageExtractTransform extends LlmUsageExtractTransform
{
    LlmOpenaiUsageExtractTransform(
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
        case "usage.prompt_tokens":
            inputTokens(source.getInt());
            break;
        case "usage.completion_tokens":
            outputTokens(source.getInt());
            break;
        case "usage.total_tokens":
            totalTokens(source.getInt());
            break;
        case "usage.prompt_tokens_details.cached_tokens":
            cacheReadTokens(source.getInt());
            break;
        case "usage.completion_tokens_details.reasoning_tokens":
            reasoningTokens(source.getInt());
            break;
        default:
            break;
        }
    }
}
