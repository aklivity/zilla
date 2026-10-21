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

import java.util.function.Consumer;

import org.junit.Test;

public class LlmOpenaiResponsesServerToolFromCanonicalTest
{
    private final Consumer<LlmSectionAttributes> id = attributes -> attributes.toolCallId("ws_1");
    private final Consumer<LlmSectionAttributes> named = id.andThen(attributes -> attributes.toolName("code_exec"));

    private LlmOpenaiResponsesServerToolFromCanonical transform(
        boolean sameDialect,
        int part,
        String itemType,
        String exclude)
    {
        final LlmOpenaiResponsesServerToolFromCanonical transform = new LlmOpenaiResponsesServerToolFromCanonical(sameDialect);

        transform.configure(part, itemType, exclude);

        return transform;
    }

    @Test
    public void shouldWriteMembersWithoutExcluded()
    {
        assertTransforms(transform(true, LlmOpenaiResponsesServerToolFromCanonical.PART_MEMBERS, "code_interpreter_call", "code"),
            json("{'arguments':{'code':'1','container_id':'c'}}"),
            json("{'container_id':'c'}"));
    }

    @Test
    public void shouldWriteResultMembers()
    {
        assertTransforms(transform(true, LlmOpenaiResponsesServerToolFromCanonical.PART_RESULT, "code_interpreter_call", null),
            json("{'outputs':[1,2]}"),
            json("{'outputs':[1,2]}"));
    }

    @Test
    public void shouldWriteExtrasWithoutArguments()
    {
        assertTransforms(transform(true, LlmOpenaiResponsesServerToolFromCanonical.PART_EXTRAS, "function_call", null),
            json("{'arguments':{'a':1},'openai:namespace':'ns'}"),
            json("{'namespace':'ns'}"));
    }

    @Test
    public void shouldDropOtherDialectExtras()
    {
        assertTransforms(transform(false, LlmOpenaiResponsesServerToolFromCanonical.PART_EXTRAS, "function_call", null),
            json("{'arguments':{'a':1},'openai:namespace':'ns','kept':1}"),
            json("{'kept':1}"));
    }

    @Test
    public void shouldWriteCallItemWithIdentityFromId()
    {
        assertTransforms(transform(true, LlmOpenaiResponsesServerToolFromCanonical.PART_CALL, "web_search_call", null),
            id, null,
            json("{'arguments':{'action':{'type':'search'}}}"),
            json("{'type':'web_search_call','id':'ws_1','status':'completed','action':{'type':'search'}}"));
    }

    @Test
    public void shouldWriteCallItemWithCallIdAndName()
    {
        assertTransforms(transform(true, LlmOpenaiResponsesServerToolFromCanonical.PART_CALL, "custom_tool_call", null),
            named, null,
            json("{'arguments':{'input':'x'}}"),
            json("{'type':'custom_tool_call','input':'x','call_id':'ws_1','name':'code_exec'}"));
    }

    @Test
    public void shouldWriteOutputItem()
    {
        assertTransforms(transform(true, LlmOpenaiResponsesServerToolFromCanonical.PART_OUTPUT, "custom_tool_call_output", null),
            id, null,
            json("{'output':'x'}"),
            json("{'type':'custom_tool_call_output','call_id':'ws_1','output':'x'}"));
    }
}
