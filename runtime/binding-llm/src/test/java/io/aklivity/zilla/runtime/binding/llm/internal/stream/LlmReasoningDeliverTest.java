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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmReasoningDeliverTest
{
    private static final int[] WINDOWS = {1, 7, 13, 64, 65, 100, 1000};
    private static final String TEXT = "Let me think. ".repeat(400);

    @Test
    public void shouldDeliverTextAndSignature()
    {
        assertDelivers("{\"text\":\"Let me think.\",\"anthropic:signature\":\"sig-1\"}",
            Map.of("text", "Let me think.", "anthropic:signature", "sig-1"));
    }

    @Test
    public void shouldDeliverSignatureBeforeText()
    {
        assertDelivers("{\"anthropic:signature\":\"sig-1\",\"text\":\"Let me think.\"}",
            Map.of("text", "Let me think.", "anthropic:signature", "sig-1"));
    }

    @Test
    public void shouldDeliverRedactedData()
    {
        assertDelivers("{\"anthropic:data\":\"opaque\"}", Map.of("anthropic:data", "opaque"));
    }

    @Test
    public void shouldDeliverDecodedText()
    {
        assertDelivers("{\"text\":\"line one\\nline \\\"two\\\" caf\\u00e9 \\ud83d\\ude00\"}",
            Map.of("text", "line one\nline \"two\" café 😀"));
    }

    @Test
    public void shouldDeliverTextLargerThanWindow()
    {
        assertDelivers("{\"text\":\"" + TEXT + "\"}", Map.of("text", TEXT));
    }

    @Test
    public void shouldDropOtherMembers()
    {
        assertDelivers("{\"openai:note\":{\"a\":[1,2]},\"text\":\"t\",\"anthropic:cache_control\":\"x\"}",
            Map.of("text", "t"));
    }

    private static void assertDelivers(
        String json,
        Map<String, String> expected)
    {
        for (int window : WINDOWS)
        {
            final Map<String, ByteArrayOutputStream> delivered = new LinkedHashMap<>();
            final LlmSectionPipeline pipeline = new LlmSectionPipeline(
                new LlmReasoningDeliver((member, buffer, offset, length, last) ->
                {
                    final byte[] chunk = new byte[length];
                    buffer.getBytes(offset, chunk);
                    delivered.computeIfAbsent(member, k -> new ByteArrayOutputStream()).write(chunk, 0, length);
                }),
                new UnsafeBufferEx(new byte[64]));

            pipeline.begin(new LlmSectionAttributes());

            final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
            boolean accepted = true;

            for (int offset = 0; accepted && offset < bytes.length; offset += window)
            {
                final int length = Math.min(window, bytes.length - offset);

                accepted = pipeline.feed(input, offset, length, offset + length == bytes.length, (b, o, l, last) ->
                {
                });
            }

            assertThat("accepted window " + window, accepted, is(true));

            final Map<String, String> actual = new LinkedHashMap<>();
            delivered.forEach((member, text) -> actual.put(member, text.toString(StandardCharsets.UTF_8)));

            assertThat("window " + window, actual, equalTo(expected));
        }
    }
}
