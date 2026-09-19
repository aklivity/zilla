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

import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Creates the decode {@link JsonTransform}/encode {@link JsonSink} pair for a dialect name, or {@code null}
 * when no such pair is registered for that name.
 */
public final class LlmResponseTransformFactory
{
    private static final String OPENAI = "openai";
    private static final String ANTHROPIC = "anthropic";

    private LlmResponseTransformFactory()
    {
    }

    /**
     * Whether a genuine decode/encode pair is registered for {@code dialectName} -- a dialect without one
     * (e.g. a third-party or test-only dialect) falls back to schema-validate-and-passthrough instead of
     * streaming dialect translation, exactly as a same-dialect route already does.
     */
    public static boolean supports(
        String dialectName)
    {
        return OPENAI.equals(dialectName) || ANTHROPIC.equals(dialectName);
    }

    public static JsonTransform supplyDecodeTransform(
        String dialectName)
    {
        final JsonTransform transform;
        if (OPENAI.equals(dialectName))
        {
            transform = new LlmOpenaiDecodeTransform();
        }
        else if (ANTHROPIC.equals(dialectName))
        {
            transform = new LlmAnthropicDecodeTransform();
        }
        else
        {
            transform = null;
        }
        return transform;
    }

    public static JsonSink supplyEncodeSink(
        String dialectName,
        LlmNativeEventOutput output)
    {
        final JsonSink sink;
        if (OPENAI.equals(dialectName))
        {
            sink = new LlmOpenaiEncodeSink(output);
        }
        else if (ANTHROPIC.equals(dialectName))
        {
            sink = new LlmAnthropicEncodeSink(output);
        }
        else
        {
            sink = null;
        }
        return sink;
    }
}
