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

public class LlmOpenaiResponsesReasoningFromCanonicalTest
{
    private static final String ENCRYPTED = "QUJD".repeat(2500);

    private final LlmOpenaiResponsesReasoningFromCanonical same = new LlmOpenaiResponsesReasoningFromCanonical("openai/rs");
    private final LlmOpenaiResponsesReasoningFromCanonical foreign = new LlmOpenaiResponsesReasoningFromCanonical("openai/ccs");

    @Test
    public void shouldRestoreMembersWithoutText()
    {
        assertTransforms(same,
            json("{'text':'Let me think','openai/rs:id':'rs_1','openai/rs:encrypted_content':'QUJD'}"),
            json("{'id':'rs_1','encrypted_content':'QUJD'}"));
    }

    @Test
    public void shouldRestoreEncryptedContentLargerThanWindow()
    {
        assertTransforms(same,
            json("{'text':'Let me think','openai/rs:id':'rs_1','openai/rs:encrypted_content':'" + ENCRYPTED + "'}"),
            json("{'id':'rs_1','encrypted_content':'" + ENCRYPTED + "'}"));
    }

    @Test
    public void shouldRestoreMembersOfSectionWithoutText()
    {
        assertTransforms(same,
            json("{'openai/rs:id':'rs_1','openai/rs:encrypted_content':'QUJD'}"),
            json("{'id':'rs_1','encrypted_content':'QUJD'}"));
    }

    @Test
    public void shouldDropOtherDialectMembers()
    {
        assertTransforms(same,
            json("{'text':'Let me think','anthropic:signature':'sig','openai/rs:id':'rs_1'}"),
            json("{'id':'rs_1'}"));
    }

    @Test
    public void shouldDropUnprefixedMembers()
    {
        assertTransforms(same,
            json("{'text':'Let me think','extra':1}"),
            json("{}"));
    }

    @Test
    public void shouldDropEveryMemberAcrossDialects()
    {
        assertTransforms(foreign,
            json("{'text':'Let me think','openai/rs:id':'rs_1','openai/rs:encrypted_content':'QUJD'}"),
            json("{}"));
    }
}
