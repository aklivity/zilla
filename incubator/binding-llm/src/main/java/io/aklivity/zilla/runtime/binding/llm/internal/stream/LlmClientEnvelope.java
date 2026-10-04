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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmErrorFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmUsageFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

final class LlmClientEnvelope
{
    static final String ENVELOPE_USAGE_INPUT_TOKENS = "usage.inputTokens";
    static final String ENVELOPE_USAGE_CACHE_WRITE_TOKENS = "usage.cacheWriteTokens";
    static final String ENVELOPE_USAGE_CACHE_READ_TOKENS = "usage.cacheReadTokens";
    static final String ENVELOPE_USAGE_OUTPUT_TOKENS = "usage.outputTokens";
    static final String ENVELOPE_USAGE_REASONING_TOKENS = "usage.reasoningTokens";
    static final String ENVELOPE_USAGE_TOTAL_TOKENS = "usage.totalTokens";
    static final String ENVELOPE_ERROR_STATUS = "error.status";
    static final String ENVELOPE_ERROR_TYPE = "error.type";
    static final String ENVELOPE_ERROR_MESSAGE = "error.message";

    static void populateUsage(
        LlmUsageFW.Builder usage,
        JsonEnvelope envelope)
    {
        int inputTokens = usageTokens(envelope, ENVELOPE_USAGE_INPUT_TOKENS);
        int cacheWriteTokens = usageTokens(envelope, ENVELOPE_USAGE_CACHE_WRITE_TOKENS);
        int cacheReadTokens = usageTokens(envelope, ENVELOPE_USAGE_CACHE_READ_TOKENS);
        int outputTokens = usageTokens(envelope, ENVELOPE_USAGE_OUTPUT_TOKENS);
        int reasoningTokens = usageTokens(envelope, ENVELOPE_USAGE_REASONING_TOKENS);
        int totalTokens = usageTokens(envelope, ENVELOPE_USAGE_TOTAL_TOKENS);

        usage.inputTokens(inputTokens)
            .cacheWriteTokens(cacheWriteTokens)
            .cacheReadTokens(cacheReadTokens)
            .outputTokens(outputTokens)
            .reasoningTokens(reasoningTokens)
            .totalTokens(totalTokens);

        String nativeUsage = nativeUsage(inputTokens, cacheWriteTokens, cacheReadTokens, outputTokens,
            reasoningTokens, totalTokens);
        if (nativeUsage != null)
        {
            usage.nativeUsage(nativeUsage);
        }
    }

    static void populateError(
        LlmErrorFW.Builder error,
        JsonEnvelope envelope)
    {
        error.status(envelopeInt(envelope, ENVELOPE_ERROR_STATUS))
            .type(envelopeString(envelope, ENVELOPE_ERROR_TYPE))
            .message(envelopeString(envelope, ENVELOPE_ERROR_MESSAGE));
    }

    static boolean failed(
        JsonEnvelope envelope)
    {
        return envelope.count(ENVELOPE_ERROR_STATUS) > 0 ||
            envelope.count(ENVELOPE_ERROR_TYPE) > 0 ||
            envelope.count(ENVELOPE_ERROR_MESSAGE) > 0;
    }

    static String envelopeString(
        JsonEnvelope envelope,
        String name)
    {
        int count = envelope.count(name);
        String value = null;

        if (count > 0)
        {
            DirectBufferEx buffer = envelope.get(name, count - 1);
            value = buffer.getStringWithoutLengthUtf8(0, buffer.capacity());
        }

        return value;
    }

    static int envelopeInt(
        JsonEnvelope envelope,
        String name)
    {
        String value = envelopeString(envelope, name);
        return value != null ? Integer.parseInt(value) : -1;
    }

    static int usageTokens(
        JsonEnvelope envelope,
        String name)
    {
        return envelopeInt(envelope, name);
    }

    // Reconstructed from whichever fields were actually recognized -- not a byte-exact capture of the
    // provider's own usage object -- so a caller reconciling billing sees every field Zilla extracted,
    // named consistently across dialects, without needing the raw wire bytes this binding never retains.
    static String nativeUsage(
        int inputTokens,
        int cacheWriteTokens,
        int cacheReadTokens,
        int outputTokens,
        int reasoningTokens,
        int totalTokens)
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        addIfPresent(builder, "input_tokens", inputTokens);
        addIfPresent(builder, "cache_write_tokens", cacheWriteTokens);
        addIfPresent(builder, "cache_read_tokens", cacheReadTokens);
        addIfPresent(builder, "output_tokens", outputTokens);
        addIfPresent(builder, "reasoning_tokens", reasoningTokens);
        addIfPresent(builder, "total_tokens", totalTokens);

        JsonObject usage = builder.build();
        return usage.isEmpty() ? null : usage.toString();
    }

    static void addIfPresent(
        JsonObjectBuilder builder,
        String name,
        int value)
    {
        if (value != -1)
        {
            builder.add(name, value);
        }
    }

    private LlmClientEnvelope()
    {
    }
}
