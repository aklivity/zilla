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

import java.util.function.Consumer;

import org.junit.Test;

public class LlmOpenaiResponsesToolCallFromCanonicalTest
{
    private final LlmOpenaiResponsesToolCallFromCanonical same = new LlmOpenaiResponsesToolCallFromCanonical("openai/rs");
    private final LlmOpenaiResponsesToolCallFromCanonical foreign = new LlmOpenaiResponsesToolCallFromCanonical("openai/ccs");

    private final Consumer<LlmSectionAttributes> id = attributes -> attributes.toolCallId("call_1");
    private final Consumer<LlmSectionAttributes> name = attributes -> attributes.toolName("get_weather");
    private final Consumer<LlmSectionAttributes> both = id.andThen(name);

    @Test
    public void shouldEscapeArguments()
    {
        assertTransforms(same, both, null,
            json("{'arguments':{'city':'Paris'}}"),
            json("{'type':'function_call','arguments':") + quote(json("{'city':'Paris'}")) +
                json(",'call_id':'call_1','name':'get_weather'}"));
    }

    @Test
    public void shouldRestoreOwnMembers()
    {
        assertTransforms(same, both, null,
            json("{'arguments':{'a':1},'openai/rs:namespace':'ns'}"),
            json("{'type':'function_call','arguments':") + quote(json("{'a':1}")) +
                json(",'namespace':'ns','call_id':'call_1','name':'get_weather'}"));
    }

    @Test
    public void shouldDropOtherDialectMembers()
    {
        assertTransforms(foreign, both, null,
            json("{'arguments':{'a':1},'openai/rs:namespace':'ns'}"),
            json("{'type':'function_call','arguments':") + quote(json("{'a':1}")) +
                json(",'call_id':'call_1','name':'get_weather'}"));
    }

    @Test
    public void shouldDefaultEmptyArguments()
    {
        assertTransforms(same, both, null,
            json("{}"),
            json("{'type':'function_call','arguments':'{}','call_id':'call_1','name':'get_weather'}"));
    }

    @Test
    public void shouldRejectMissingIdentity()
    {
        assertRejects(same, name, null, json("{'arguments':{}}"));
        assertRejects(same, id, null, json("{'arguments':{}}"));
    }
}
