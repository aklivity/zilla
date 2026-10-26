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

public class LlmOpenaiResponsesServerToolToCanonicalTest
{
    private final LlmOpenaiResponsesServerToolToCanonical call = new LlmOpenaiResponsesServerToolToCanonical(true);
    private final LlmOpenaiResponsesServerToolToCanonical result = new LlmOpenaiResponsesServerToolToCanonical(false);

    @Test
    public void shouldWrapCallMembersAsArguments()
    {
        assertTransforms(call,
            json("{'id':'ws_1','type':'web_search_call','status':'completed','action':{'type':'search'}}"),
            json("{'arguments':{'action':{'type':'search'}}}"));
    }

    @Test
    public void shouldPassResultMembers()
    {
        assertTransforms(result,
            json("{'error':null,'output':'Java','server_label':'deepwiki'}"),
            json("{'error':null,'output':'Java','server_label':'deepwiki'}"));
    }

    @Test
    public void shouldDropPromotedMembersFromResult()
    {
        assertTransforms(result,
            json("{'call_id':'c','name':'n','output':'x'}"),
            json("{'output':'x'}"));
    }
}
