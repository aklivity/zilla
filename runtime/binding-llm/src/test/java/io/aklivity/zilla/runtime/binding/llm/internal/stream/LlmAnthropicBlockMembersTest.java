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

public class LlmAnthropicBlockMembersTest
{
    @Test
    public void shouldDecodeThinkingSignatureAndRedactedData()
    {
        assertTransforms(new LlmAnthropicReasoningToCanonical(),
            json("{'type':'thinking','thinking':'t','signature':'sig'}"),
            json("{'text':'t','x:signature':'sig'}"));
        assertTransforms(new LlmAnthropicReasoningToCanonical(),
            json("{'type':'redacted_thinking','data':'opaque'}"),
            json("{'x:redacted_data':'opaque'}"));
    }
}
