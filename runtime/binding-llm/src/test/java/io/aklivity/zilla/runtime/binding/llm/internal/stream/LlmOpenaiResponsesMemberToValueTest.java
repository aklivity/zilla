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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRejects;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.quote;

import org.junit.Test;

public class LlmOpenaiResponsesMemberToValueTest
{
    @Test
    public void shouldExtractStringMember()
    {
        assertTransforms(new LlmOpenaiResponsesMemberToValue("code"),
            json("{'arguments':{'code':'a b','container_id':'c'}}"),
            quote("a b"));
    }

    @Test
    public void shouldExtractEscapedStringMember()
    {
        assertTransforms(new LlmOpenaiResponsesMemberToValue("input"),
            json("{'arguments':{'input':'say \\\"hi\\\"\\n'}}"),
            json("'say \\\"hi\\\"\\n'"));
    }

    @Test
    public void shouldRejectMemberThatIsNotString()
    {
        assertRejects(new LlmOpenaiResponsesMemberToValue("code"), json("{'arguments':{'code':1}}"));
    }
}
