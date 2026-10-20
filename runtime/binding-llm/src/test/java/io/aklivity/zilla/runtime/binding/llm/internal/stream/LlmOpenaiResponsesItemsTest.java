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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class LlmOpenaiResponsesItemsTest
{
    @Test
    public void shouldClassifyKinds()
    {
        assertThat(LlmOpenaiResponsesItems.kind("message"), is(LlmOpenaiResponsesItems.KIND_MESSAGE));
        assertThat(LlmOpenaiResponsesItems.kind("function_call"), is(LlmOpenaiResponsesItems.KIND_FUNCTION_CALL));
        assertThat(LlmOpenaiResponsesItems.kind("function_call_output"), is(LlmOpenaiResponsesItems.KIND_FUNCTION_OUTPUT));
        assertThat(LlmOpenaiResponsesItems.kind("custom_tool_call_output"), is(LlmOpenaiResponsesItems.KIND_RESULT));
        assertThat(LlmOpenaiResponsesItems.kind("tool_search_output"), is(LlmOpenaiResponsesItems.KIND_RESULT));
        assertThat(LlmOpenaiResponsesItems.kind("web_search_call"), is(LlmOpenaiResponsesItems.KIND_CALL));
        assertThat(LlmOpenaiResponsesItems.kind("mcp_list_tools"), is(LlmOpenaiResponsesItems.KIND_CALL));
        assertThat(LlmOpenaiResponsesItems.kind("reasoning"), is(LlmOpenaiResponsesItems.KIND_NONE));
    }

    @Test
    public void shouldDescribeIdentity()
    {
        assertThat(LlmOpenaiResponsesItems.hasCallId("function_call"), is(true));
        assertThat(LlmOpenaiResponsesItems.hasCallId("web_search_call"), is(false));
        assertThat(LlmOpenaiResponsesItems.hasName("mcp_call"), is(true));
        assertThat(LlmOpenaiResponsesItems.hasName("code_interpreter_call"), is(false));
        assertThat(LlmOpenaiResponsesItems.hasStatus("custom_tool_call"), is(false));
        assertThat(LlmOpenaiResponsesItems.hasStatus("function_call"), is(true));
        assertThat(LlmOpenaiResponsesItems.toolName("web_search_call"), equalTo("web_search"));
        assertThat(LlmOpenaiResponsesItems.toolName("mcp_list_tools"), equalTo("mcp_list_tools"));
    }

    @Test
    public void shouldSplitCallsAtResultMember()
    {
        assertThat(LlmOpenaiResponsesItems.split("code_interpreter_call"), is(true));
        assertThat(LlmOpenaiResponsesItems.split("mcp_call"), is(true));
        assertThat(LlmOpenaiResponsesItems.split("web_search_call"), is(false));
        assertThat(LlmOpenaiResponsesItems.resultMember("code_interpreter_call", "outputs"), is(true));
        assertThat(LlmOpenaiResponsesItems.resultMember("code_interpreter_call", "code"), is(false));
        assertThat(LlmOpenaiResponsesItems.resultMember("mcp_call", "error"), is(true));
        assertThat(LlmOpenaiResponsesItems.resultMember("mcp_call", "output"), is(true));
        assertThat(LlmOpenaiResponsesItems.resultMember("mcp_call", "name"), is(false));
        assertThat(LlmOpenaiResponsesItems.resultMember("web_search_call", "output"), is(false));
    }

    @Test
    public void shouldDescribeDeltaMembers()
    {
        assertThat(LlmOpenaiResponsesItems.deltaMember("custom_tool_call"), equalTo("input"));
        assertThat(LlmOpenaiResponsesItems.deltaMember("code_interpreter_call"), equalTo("code"));
        assertThat(LlmOpenaiResponsesItems.deltaMember("mcp_call"), equalTo("arguments"));
        assertThat(LlmOpenaiResponsesItems.deltaMember("web_search_call"), nullValue());
        assertThat(LlmOpenaiResponsesItems.deltaMember("custom_tool_call", "input"), is(true));
        assertThat(LlmOpenaiResponsesItems.deltaMember("code_interpreter_call", "code"), is(true));
        assertThat(LlmOpenaiResponsesItems.deltaMember("mcp_call", "arguments"), is(true));
        assertThat(LlmOpenaiResponsesItems.deltaMember("web_search_call", "action"), is(false));
        assertThat(LlmOpenaiResponsesItems.valueMember("function_call"), equalTo("arguments"));
        assertThat(LlmOpenaiResponsesItems.valueMember("mcp_call"), equalTo("arguments"));
        assertThat(LlmOpenaiResponsesItems.valueMember("image_generation_call"), nullValue());
    }

    @Test
    public void shouldDescribeIdPrefixes()
    {
        assertThat(LlmOpenaiResponsesItems.prefix("function_call"), equalTo("fc"));
        assertThat(LlmOpenaiResponsesItems.prefix("custom_tool_call"), equalTo("ctc"));
        assertThat(LlmOpenaiResponsesItems.prefix("code_interpreter_call"), equalTo("ci"));
        assertThat(LlmOpenaiResponsesItems.prefix("mcp_call"), equalTo("mcp"));
        assertThat(LlmOpenaiResponsesItems.prefix("mcp_list_tools"), equalTo("mcpl"));
        assertThat(LlmOpenaiResponsesItems.prefix("web_search_call"), equalTo("ws"));
        assertThat(LlmOpenaiResponsesItems.prefix("image_generation_call"), equalTo("ig"));
        assertThat(LlmOpenaiResponsesItems.prefix("file_search_call"), equalTo("fs"));
        assertThat(LlmOpenaiResponsesItems.prefix("message"), equalTo("msg"));
        assertThat(LlmOpenaiResponsesItems.prefix("computer_call"), equalTo("computer"));
    }

    @Test
    public void shouldDescribeEvents()
    {
        assertThat(LlmOpenaiResponsesItems.initEvents("web_search_call"), arrayContaining(
            "response.web_search_call.in_progress",
            "response.web_search_call.searching",
            "response.web_search_call.completed"));
        assertThat(LlmOpenaiResponsesItems.initEvents("code_interpreter_call"), arrayContaining(
            "response.code_interpreter_call.in_progress"));
        assertThat(LlmOpenaiResponsesItems.initEvents("mcp_call"), arrayContaining("response.mcp_call.in_progress"));
        assertThat(LlmOpenaiResponsesItems.initEvents("mcp_list_tools"), arrayContaining(
            "response.mcp_list_tools.in_progress",
            "response.mcp_list_tools.completed"));
        assertThat(LlmOpenaiResponsesItems.initEvents("image_generation_call"), arrayContaining(
            "response.image_generation_call.in_progress",
            "response.image_generation_call.generating",
            "response.image_generation_call.completed"));
        assertThat(LlmOpenaiResponsesItems.initEvents("file_search_call"), arrayContaining(
            "response.file_search_call.in_progress",
            "response.file_search_call.searching",
            "response.file_search_call.completed"));
        assertThat(LlmOpenaiResponsesItems.initEvents("function_call"), emptyArray());
        assertThat(LlmOpenaiResponsesItems.deltaEvent("function_call"), equalTo("response.function_call_arguments.delta"));
        assertThat(LlmOpenaiResponsesItems.deltaEvent("custom_tool_call"), equalTo("response.custom_tool_call_input.delta"));
        assertThat(LlmOpenaiResponsesItems.deltaEvent("code_interpreter_call"),
            equalTo("response.code_interpreter_call_code.delta"));
        assertThat(LlmOpenaiResponsesItems.deltaEvent("mcp_call"), equalTo("response.mcp_call_arguments.delta"));
        assertThat(LlmOpenaiResponsesItems.deltaEvent("web_search_call"), nullValue());
        assertThat(LlmOpenaiResponsesItems.doneEvent("function_call"), equalTo("response.function_call_arguments.done"));
        assertThat(LlmOpenaiResponsesItems.doneEvent("custom_tool_call"), equalTo("response.custom_tool_call_input.done"));
        assertThat(LlmOpenaiResponsesItems.doneEvent("code_interpreter_call"),
            equalTo("response.code_interpreter_call_code.done"));
        assertThat(LlmOpenaiResponsesItems.doneEvent("mcp_call"), equalTo("response.mcp_call_arguments.done"));
        assertThat(LlmOpenaiResponsesItems.doneEvent("web_search_call"), nullValue());
        assertThat(LlmOpenaiResponsesItems.doneEvents("code_interpreter_call"), arrayContaining(
            "response.code_interpreter_call.interpreting",
            "response.code_interpreter_call.completed"));
        assertThat(LlmOpenaiResponsesItems.doneEvents("mcp_call"), arrayContaining("response.mcp_call.completed"));
        assertThat(LlmOpenaiResponsesItems.doneEvents("function_call"), emptyArray());
        assertThat(LlmOpenaiResponsesItems.events(), hasItem("response.mcp_call.completed"));
    }
}
