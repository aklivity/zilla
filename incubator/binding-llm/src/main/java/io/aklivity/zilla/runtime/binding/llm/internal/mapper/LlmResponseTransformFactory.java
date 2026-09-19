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
 * Creates the decode {@link JsonTransform}/encode {@link JsonSink} pair for a dialect name -- every
 * registered dialect (openai, anthropic) has one.
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
        LlmNativeEventOutput output)
    {
        return OPENAI.equals(dialectName) ? new LlmOpenaiEncodeSink(output) : new LlmAnthropicEncodeSink(output);
    }
}
