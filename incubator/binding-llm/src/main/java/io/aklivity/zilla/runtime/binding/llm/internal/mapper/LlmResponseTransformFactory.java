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

import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Creates the decode {@link JsonTransform}/encode {@link JsonSink} pair for a dialect name -- every
 * registered dialect (openai, anthropic) has one. A decode transform tells a whole non-streaming document
 * apart from one streaming increment using its own native signal (e.g. Anthropic's out-of-band SSE event
 * name), so it needs no {@link JsonEnvelope}. An encode sink has no native signal of its own to read that
 * from -- it only ever sees canonical actions -- so {@code envelope} carries the per-stream streaming flag
 * the client stream handler writes at response-begin, letting the sink choose between emitting per action
 * (streaming) and accumulating into one document emitted once at the end (non-streaming).
 */
public final class LlmResponseTransformFactory
{
    private static final String OPENAI = "openai";

    private LlmResponseTransformFactory()
    {
    }

    public static JsonTransform supplyDecodeTransform(
        String dialectName)
    {
        return OPENAI.equals(dialectName) ? new LlmOpenaiDecodeTransform() : new LlmAnthropicDecodeTransform();
    }

    public static JsonSink supplyEncodeSink(
        String dialectName,
        JsonEnvelope envelope,
        LlmNativeEventOutput output)
    {
        return OPENAI.equals(dialectName)
            ? new LlmOpenaiEncodeSink(envelope, output)
            : new LlmAnthropicEncodeSink(envelope, output);
    }
}
