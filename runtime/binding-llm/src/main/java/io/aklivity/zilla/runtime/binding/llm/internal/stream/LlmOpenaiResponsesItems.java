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

import java.util.Set;

final class LlmOpenaiResponsesItems
{
    static final int KIND_NONE = 0;
    static final int KIND_MESSAGE = 1;
    static final int KIND_FUNCTION_CALL = 2;
    static final int KIND_FUNCTION_OUTPUT = 3;
    static final int KIND_CALL = 4;
    static final int KIND_RESULT = 5;
    static final int KIND_REASONING = 6;

    static final String TYPE_MESSAGE = "message";
    static final String TYPE_REASONING = "reasoning";
    static final String TYPE_FUNCTION_CALL = "function_call";
    static final String TYPE_FUNCTION_OUTPUT = "function_call_output";
    static final String TYPE_CUSTOM_CALL = "custom_tool_call";
    static final String TYPE_CODE_INTERPRETER_CALL = "code_interpreter_call";
    static final String TYPE_MCP_CALL = "mcp_call";
    static final String TYPE_MCP_LIST_TOOLS = "mcp_list_tools";

    private static final String SUFFIX_CALL = "_call";
    private static final String SUFFIX_CALL_OUTPUT = "_call_output";
    private static final Set<String> CALL_ID_TYPES = Set.of(TYPE_FUNCTION_CALL, TYPE_CUSTOM_CALL, "computer_call",
        "local_shell_call", "shell_call", "apply_patch_call", "tool_search_call");
    private static final Set<String> NAMED_TYPES = Set.of(TYPE_FUNCTION_CALL, TYPE_CUSTOM_CALL, TYPE_MCP_CALL,
        "mcp_approval_request");
    private static final Set<String> CALL_TYPES = Set.of(TYPE_MCP_LIST_TOOLS, "mcp_approval_request");
    private static final Set<String> RESULT_TYPES = Set.of("mcp_approval_response", "tool_search_output");
    private static final String[] NO_EVENTS = new String[0];
    private static final Set<String> EVENTS = Set.of(
        "response.function_call_arguments.delta",
        "response.function_call_arguments.done",
        "response.custom_tool_call_input.delta",
        "response.custom_tool_call_input.done",
        "response.code_interpreter_call.in_progress",
        "response.code_interpreter_call_code.delta",
        "response.code_interpreter_call_code.done",
        "response.code_interpreter_call.interpreting",
        "response.code_interpreter_call.completed",
        "response.mcp_call.in_progress",
        "response.mcp_call_arguments.delta",
        "response.mcp_call_arguments.done",
        "response.mcp_call.completed",
        "response.mcp_list_tools.in_progress",
        "response.mcp_list_tools.completed",
        "response.reasoning_summary_part.added",
        "response.reasoning_summary_part.done",
        "response.reasoning_summary_text.delta",
        "response.reasoning_summary_text.done",
        "response.refusal.delta",
        "response.refusal.done",
        "response.output_text.annotation.added",
        "response.web_search_call.in_progress",
        "response.web_search_call.searching",
        "response.web_search_call.completed",
        "response.image_generation_call.in_progress",
        "response.image_generation_call.generating",
        "response.image_generation_call.completed",
        "response.file_search_call.in_progress",
        "response.file_search_call.searching",
        "response.file_search_call.completed");

    static int kind(
        String type)
    {
        final int kind;

        if (TYPE_MESSAGE.equals(type))
        {
            kind = KIND_MESSAGE;
        }
        else if (TYPE_REASONING.equals(type))
        {
            kind = KIND_REASONING;
        }
        else if (TYPE_FUNCTION_CALL.equals(type))
        {
            kind = KIND_FUNCTION_CALL;
        }
        else if (TYPE_FUNCTION_OUTPUT.equals(type))
        {
            kind = KIND_FUNCTION_OUTPUT;
        }
        else if (type.endsWith(SUFFIX_CALL_OUTPUT) || RESULT_TYPES.contains(type))
        {
            kind = KIND_RESULT;
        }
        else if (type.endsWith(SUFFIX_CALL) || CALL_TYPES.contains(type))
        {
            kind = KIND_CALL;
        }
        else
        {
            kind = KIND_NONE;
        }

        return kind;
    }

    static boolean hasCallId(
        String type)
    {
        return CALL_ID_TYPES.contains(type);
    }

    static boolean hasName(
        String type)
    {
        return NAMED_TYPES.contains(type);
    }

    static boolean hasStatus(
        String type)
    {
        return !TYPE_CUSTOM_CALL.equals(type);
    }

    static boolean split(
        String type)
    {
        return TYPE_CODE_INTERPRETER_CALL.equals(type) || TYPE_MCP_CALL.equals(type);
    }

    static boolean resultMember(
        String type,
        CharSequence key)
    {
        final boolean result;

        if (TYPE_CODE_INTERPRETER_CALL.equals(type))
        {
            result = matches(key, "outputs");
        }
        else if (TYPE_MCP_CALL.equals(type))
        {
            result = matches(key, "error") || matches(key, "output");
        }
        else
        {
            result = false;
        }

        return result;
    }

    static boolean deltaMember(
        String type,
        CharSequence key)
    {
        final boolean delta;

        if (TYPE_CUSTOM_CALL.equals(type))
        {
            delta = matches(key, "input");
        }
        else if (TYPE_CODE_INTERPRETER_CALL.equals(type))
        {
            delta = matches(key, "code");
        }
        else if (TYPE_MCP_CALL.equals(type))
        {
            delta = matches(key, "arguments");
        }
        else
        {
            delta = false;
        }

        return delta;
    }

    static String deltaMember(
        String type)
    {
        final String member;

        if (TYPE_CUSTOM_CALL.equals(type))
        {
            member = "input";
        }
        else if (TYPE_CODE_INTERPRETER_CALL.equals(type))
        {
            member = "code";
        }
        else if (TYPE_MCP_CALL.equals(type))
        {
            member = "arguments";
        }
        else
        {
            member = null;
        }

        return member;
    }

    static String toolName(
        String type)
    {
        return type.endsWith(SUFFIX_CALL) ? type.substring(0, type.length() - SUFFIX_CALL.length()) : type;
    }

    static String valueMember(
        String type)
    {
        return TYPE_FUNCTION_CALL.equals(type) ? "arguments" : deltaMember(type);
    }

    static String prefix(
        String type)
    {
        final String prefix;

        switch (type)
        {
        case TYPE_FUNCTION_CALL:
            prefix = "fc";
            break;
        case TYPE_CUSTOM_CALL:
            prefix = "ctc";
            break;
        case TYPE_CODE_INTERPRETER_CALL:
            prefix = "ci";
            break;
        case TYPE_MCP_CALL:
            prefix = "mcp";
            break;
        case TYPE_MCP_LIST_TOOLS:
            prefix = "mcpl";
            break;
        case "web_search_call":
            prefix = "ws";
            break;
        case "image_generation_call":
            prefix = "ig";
            break;
        case "file_search_call":
            prefix = "fs";
            break;
        case TYPE_MESSAGE:
            prefix = "msg";
            break;
        case TYPE_REASONING:
            prefix = "rs";
            break;
        default:
            prefix = toolName(type);
            break;
        }

        return prefix;
    }

    static String[] initEvents(
        String type)
    {
        final String[] events;

        switch (type)
        {
        case TYPE_CODE_INTERPRETER_CALL:
            events = new String[] {"response.code_interpreter_call.in_progress"};
            break;
        case TYPE_MCP_CALL:
            events = new String[] {"response.mcp_call.in_progress"};
            break;
        case TYPE_MCP_LIST_TOOLS:
            events = new String[] {"response.mcp_list_tools.in_progress", "response.mcp_list_tools.completed"};
            break;
        case "web_search_call":
            events = new String[] {"response.web_search_call.in_progress", "response.web_search_call.searching",
                "response.web_search_call.completed"};
            break;
        case "image_generation_call":
            events = new String[] {"response.image_generation_call.in_progress", "response.image_generation_call.generating",
                "response.image_generation_call.completed"};
            break;
        case "file_search_call":
            events = new String[] {"response.file_search_call.in_progress", "response.file_search_call.searching",
                "response.file_search_call.completed"};
            break;
        default:
            events = NO_EVENTS;
            break;
        }

        return events;
    }

    static String deltaEvent(
        String type)
    {
        final String event;

        switch (type)
        {
        case TYPE_FUNCTION_CALL:
            event = "response.function_call_arguments.delta";
            break;
        case TYPE_CUSTOM_CALL:
            event = "response.custom_tool_call_input.delta";
            break;
        case TYPE_CODE_INTERPRETER_CALL:
            event = "response.code_interpreter_call_code.delta";
            break;
        case TYPE_MCP_CALL:
            event = "response.mcp_call_arguments.delta";
            break;
        default:
            event = null;
            break;
        }

        return event;
    }

    static String doneEvent(
        String type)
    {
        final String event;

        switch (type)
        {
        case TYPE_FUNCTION_CALL:
            event = "response.function_call_arguments.done";
            break;
        case TYPE_CUSTOM_CALL:
            event = "response.custom_tool_call_input.done";
            break;
        case TYPE_CODE_INTERPRETER_CALL:
            event = "response.code_interpreter_call_code.done";
            break;
        case TYPE_MCP_CALL:
            event = "response.mcp_call_arguments.done";
            break;
        default:
            event = null;
            break;
        }

        return event;
    }

    static String[] doneEvents(
        String type)
    {
        final String[] events;

        switch (type)
        {
        case TYPE_CODE_INTERPRETER_CALL:
            events = new String[] {"response.code_interpreter_call.interpreting",
                "response.code_interpreter_call.completed"};
            break;
        case TYPE_MCP_CALL:
            events = new String[] {"response.mcp_call.completed"};
            break;
        default:
            events = NO_EVENTS;
            break;
        }

        return events;
    }

    static Set<String> events()
    {
        return EVENTS;
    }

    static boolean matches(
        CharSequence value,
        String expected)
    {
        final int length = expected.length();
        boolean matches = value.length() == length;

        for (int index = 0; matches && index < length; index++)
        {
            matches = value.charAt(index) == expected.charAt(index);
        }

        return matches;
    }

    private LlmOpenaiResponsesItems()
    {
    }
}
