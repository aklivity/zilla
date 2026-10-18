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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmAnthropicReasoningToValueTest
{
    private static final String TEXT = "Let me think. ".repeat(400);

    private final LlmAnthropicReasoningToValue value = new LlmAnthropicReasoningToValue();

    @Test
    public void shouldExtractTextContent()
    {
        assertTransforms(value, json("{'text':'Let me think.'}"), json("'Let me think.'"));
    }

    @Test
    public void shouldExtractSignatureContent()
    {
        assertTransforms(value, json("{'anthropic:signature':'sig-1'}"), json("'sig-1'"));
    }

    @Test
    public void shouldExtractRedactedContent()
    {
        assertTransforms(value, json("{'anthropic:data':'opaque'}"), json("'opaque'"));
    }

    @Test
    public void shouldExtractEscapedValue()
    {
        assertTransforms(value,
            json("{'text':'line one\\nline \\\"two\\\"'}"),
            json("'line one\\nline \\\"two\\\"'"));
    }

    @Test
    public void shouldExtractContentLargerThanWindow()
    {
        assertTransforms(value, json("{'text':'" + TEXT + "'}"), json("'" + TEXT + "'"));
    }

    @Test
    public void shouldDropOtherMembers()
    {
        assertTransforms(value,
            json("{'openai:note':{'a':[1,2]},'text':'t','anthropic:cache_control':'x'}"),
            json("'t'"));
    }
}
