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

public class LlmOpenaiResponsesToolCallToCanonicalTest
{
    private final LlmOpenaiResponsesToolCallToCanonical transform = new LlmOpenaiResponsesToolCallToCanonical();

    @Test
    public void shouldUnescapeArgumentsAndDropIdentity()
    {
        assertTransforms(transform,
            json("{'id':'fc_1','type':'function_call','status':'completed','arguments':") + quote(json("{'city':'Paris'}")) +
                json(",'call_id':'call_1','name':'get_weather'}"),
            json("{'arguments':{'city':'Paris'}}"));
    }

    @Test
    public void shouldPrefixOtherMembers()
    {
        assertTransforms(transform,
            json("{'type':'function_call','arguments':") + quote(json("{'a':1}")) + json(",'namespace':'ns','call_id':'c'}"),
            json("{'arguments':{'a':1},'openai/rs:namespace':'ns'}"));
    }

    @Test
    public void shouldDefaultEmptyArguments()
    {
        assertTransforms(transform,
            json("{'type':'function_call','call_id':'c','name':'n'}"),
            json("{'arguments':{}}"));
    }

    @Test
    public void shouldRejectArgumentsThatAreNotJson()
    {
        assertRejects(transform, json("{'type':'function_call','arguments':'not json'}"));
    }
}
