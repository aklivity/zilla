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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertEscapes;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmAnthropicReasoningToContentTest
{
    private static final String TEXT = "Let me think. ".repeat(400);

    private final LlmAnthropicReasoningToContent content = new LlmAnthropicReasoningToContent();

    @Test
    public void shouldExtractTextContent()
    {
        assertEscapes(content, json("{'text':'Let me think.'}"), "Let me think.");
    }

    @Test
    public void shouldExtractSignatureContent()
    {
        assertEscapes(content, json("{'anthropic:signature':'sig-1'}"), "sig-1");
    }

    @Test
    public void shouldExtractRedactedContent()
    {
        assertEscapes(content, json("{'anthropic:data':'opaque'}"), "opaque");
    }

    @Test
    public void shouldExtractEscapedContentWithoutQuotes()
    {
        assertEscapes(content,
            json("{'text':'line one\\nline \\\"two\\\"'}"),
            "line one\\nline \\\"two\\\"");
    }

    @Test
    public void shouldExtractContentLargerThanWindow()
    {
        assertEscapes(content, json("{'text':'" + TEXT + "'}"), TEXT);
    }

    @Test
    public void shouldDropOtherMembers()
    {
        assertEscapes(content,
            json("{'openai:note':{'a':[1,2]},'text':'t','anthropic:cache_control':'x'}"),
            "t");
    }
}
