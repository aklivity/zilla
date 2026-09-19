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
package io.aklivity.zilla.runtime.binding.llm.internal.mapper;

import jakarta.json.JsonObject;

import org.agrona.DirectBuffer;

/**
 * Translates one LLM dialect's native events to and from the canonical vocabulary -- both the
 * streaming, event-by-event shape ({@link #decode}/{@code encode*}/{@link #encodeEnd}) and the
 * non-streaming, whole-document shape ({@link #decodeMessage}/{@link #encodeMessage}).
 * <p>
 * {@code decode} parses a native chunk structurally via a {@code common-json} {@code JsonSink}, calling
 * {@code output} as it recognizes each canonical event; the {@code encode*} methods write a dialect's
 * native JSON directly with a {@code common-json} {@code JsonGeneratorEx}, one per canonical event kind
 * since there is no longer a single flyweight to dispatch on.
 * </p>
 * <p>
 * {@link LlmOpenaiEventMapper} and {@link LlmAnthropicEventMapper} are the two current
 * implementations; {@link LlmEventMapperFactory} selects between them by dialect name. Holds
 * per-stream state, so a fresh instance is required per stream; instances are not shared across
 * streams.
 * </p>
 */
public interface LlmEventMapper
{
    void decode(
        String event,
        String data,
        LlmCanonicalOutput output);

    void encodeMessageStart(
        int choiceIndex,
        String id,
        String model,
        String role,
        LlmNativeEventOutput output);

    void encodeBlockStart(
        int choiceIndex,
        int blockId,
        LlmCanonicalBlockKind type,
        String toolId,
        String toolName,
        LlmNativeEventOutput output);

    void encode(
        DirectBuffer buffer,
        int offset,
        int length,
        LlmNativeEventOutput output);

    void encodeBlockEnd(
        int choiceIndex,
        int blockId,
        LlmNativeEventOutput output);

    void encodeFinish(
        int choiceIndex,
        LlmCanonicalFinishReason reason,
        LlmNativeEventOutput output);

    void encodeUsage(
        int inputTokens,
        int outputTokens,
        LlmNativeEventOutput output);

    void encodeEnd(
        LlmNativeEventOutput output);

    /**
     * Translates a non-streaming response's whole native JSON document into the canonical
     * non-streaming shape: {@code id}/{@code model} (both nullable, omitted when absent),
     * {@code role}, a {@code content} array of {@code {"type":"text","text":...}} and
     * {@code {"type":"tool_call","toolId":...,"toolName":...,"arguments":...}} entries,
     * {@code finishReason} (an {@link LlmCanonicalFinishReason} name), and a {@code usage} object
     * with {@code inputTokens}/{@code outputTokens} (-1 when absent).
     *
     * @param data  the native response document
     * @return the canonical non-streaming document
     */
    JsonObject decodeMessage(
        String data);

    /**
     * Translates the canonical non-streaming document {@link #decodeMessage} produces into this
     * dialect's native non-streaming response document.
     *
     * @param message  the canonical non-streaming document
     * @return the native response document
     */
    String encodeMessage(
        JsonObject message);
}
