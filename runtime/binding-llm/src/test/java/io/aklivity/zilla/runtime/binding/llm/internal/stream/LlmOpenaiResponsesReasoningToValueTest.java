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

public class LlmOpenaiResponsesReasoningToValueTest
{
    private final LlmOpenaiResponsesReasoningToValue same = new LlmOpenaiResponsesReasoningToValue(true, "id");
    private final LlmOpenaiResponsesReasoningToValue foreign = new LlmOpenaiResponsesReasoningToValue(false, "id");

    @Test
    public void shouldExtractMemberValue()
    {
        assertTransforms(same,
            json("{'text':'Let me think','openai:id':'rs_1','openai:encrypted_content':'QUJD'}"),
            json("'rs_1'"));
    }

    @Test
    public void shouldExtractNothingWhenAbsent()
    {
        assertTransforms(same,
            json("{'text':'Let me think'}"),
            "");
    }

    @Test
    public void shouldExtractNothingAcrossDialects()
    {
        assertTransforms(foreign,
            json("{'text':'Let me think','openai:id':'rs_1'}"),
            "");
    }
}
