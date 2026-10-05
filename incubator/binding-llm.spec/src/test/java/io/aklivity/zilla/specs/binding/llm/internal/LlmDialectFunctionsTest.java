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
package io.aklivity.zilla.specs.binding.llm.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmAnthropicRequestExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmAnthropicResponseExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmOllamaRequestExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmOllamaResponseExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmOpenaiRequestExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmOpenaiResponseExFW;

public class LlmDialectFunctionsTest
{
    @Test
    public void shouldPrefixDialectFunctions()
    {
        assertEquals("llm_openai", new LlmOpenaiFunctions.Mapper().getPrefixName());
        assertEquals("llm_anthropic", new LlmAnthropicFunctions.Mapper().getPrefixName());
        assertEquals("llm_ollama", new LlmOllamaFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateOpenaiRequestEx()
    {
        final byte[] bytes = LlmOpenaiFunctions.requestEx()
            .stream("true")
            .streamOptions("{\"include_usage\":true}")
            .temperature("0.7")
            .topP("0.9")
            .maxTokens("100")
            .seed("42")
            .stop("[\"END\"]")
            .build();

        final LlmOpenaiRequestExFW requestEx = new LlmOpenaiRequestExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("true", requestEx.stream().asString());
        assertEquals("{\"include_usage\":true}", requestEx.streamOptions().asString());
        assertEquals("0.7", requestEx.temperature().asString());
        assertEquals("0.9", requestEx.topP().asString());
        assertEquals("100", requestEx.maxTokens().asString());
        assertEquals("42", requestEx.seed().asString());
        assertEquals("[\"END\"]", requestEx.stop().asString());
    }

    @Test
    public void shouldGenerateOpenaiRequestExWithAbsentFields()
    {
        final byte[] bytes = LlmOpenaiFunctions.requestEx().temperature("0.7").build();

        final LlmOpenaiRequestExFW requestEx = new LlmOpenaiRequestExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("0.7", requestEx.temperature().asString());
        assertNull(requestEx.maxTokens().asString());
    }

    @Test
    public void shouldGenerateOpenaiResponseEx()
    {
        final byte[] bytes = LlmOpenaiFunctions.responseEx()
            .created("1700000000")
            .serviceTier("default")
            .systemFingerprint("fp_abc")
            .build();

        final LlmOpenaiResponseExFW responseEx = new LlmOpenaiResponseExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("1700000000", responseEx.created().asString());
        assertEquals("default", responseEx.serviceTier().asString());
        assertEquals("fp_abc", responseEx.systemFingerprint().asString());
    }

    @Test
    public void shouldGenerateAnthropicRequestEx()
    {
        final byte[] bytes = LlmAnthropicFunctions.requestEx()
            .stream("true")
            .temperature("0.5")
            .topP("0.9")
            .topK("40")
            .maxTokens("1024")
            .stopSequences("[\"END\"]")
            .build();

        final LlmAnthropicRequestExFW requestEx = new LlmAnthropicRequestExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("true", requestEx.stream().asString());
        assertEquals("0.5", requestEx.temperature().asString());
        assertEquals("0.9", requestEx.topP().asString());
        assertEquals("40", requestEx.topK().asString());
        assertEquals("1024", requestEx.maxTokens().asString());
        assertEquals("[\"END\"]", requestEx.stopSequences().asString());
    }

    @Test
    public void shouldGenerateAnthropicResponseEx()
    {
        final byte[] bytes = LlmAnthropicFunctions.responseEx()
            .stopSequence("END")
            .containerId("container_1")
            .build();

        final LlmAnthropicResponseExFW responseEx =
            new LlmAnthropicResponseExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("END", responseEx.stopSequence().asString());
        assertEquals("container_1", responseEx.containerId().asString());
    }

    @Test
    public void shouldGenerateOllamaRequestEx()
    {
        final byte[] bytes = LlmOllamaFunctions.requestEx()
            .stream("false")
            .options("{\"temperature\":0.8}")
            .format("json")
            .think("true")
            .keepAlive("5m")
            .build();

        final LlmOllamaRequestExFW requestEx = new LlmOllamaRequestExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("false", requestEx.stream().asString());
        assertEquals("{\"temperature\":0.8}", requestEx.options().asString());
        assertEquals("json", requestEx.format().asString());
        assertEquals("true", requestEx.think().asString());
        assertEquals("5m", requestEx.keepAlive().asString());
    }

    @Test
    public void shouldGenerateOllamaResponseEx()
    {
        final byte[] bytes = LlmOllamaFunctions.responseEx()
            .createdAt("2026-10-05T00:00:00Z")
            .totalDuration("100")
            .loadDuration("10")
            .build();

        final LlmOllamaResponseExFW responseEx = new LlmOllamaResponseExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("2026-10-05T00:00:00Z", responseEx.createdAt().asString());
        assertEquals("100", responseEx.totalDuration().asString());
        assertEquals("10", responseEx.loadDuration().asString());
    }
}
