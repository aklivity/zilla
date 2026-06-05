/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.mcp.internal;

import static io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_GET;
import static io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import io.aklivity.zilla.runtime.metrics.mcp.internal.types.String16FW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW;

enum McpMethod
{
    INITIALIZE(KIND_LIFECYCLE, "initialize", "session establishment"),
    TOOLS_LIST(KIND_TOOLS_LIST, "tools.list", "tool catalog requests"),
    TOOLS_CALL(KIND_TOOLS_CALL, "tools.call", "tool invocations"),
    RESOURCES_LIST(KIND_RESOURCES_LIST, "resources.list", "resource catalog requests"),
    RESOURCES_READ(KIND_RESOURCES_READ, "resources.read", "resource reads"),
    PROMPTS_LIST(KIND_PROMPTS_LIST, "prompts.list", "prompt catalog requests"),
    PROMPTS_GET(KIND_PROMPTS_GET, "prompts.get", "prompt resolutions");

    private final int kind;
    private final String segment;
    private final String summary;

    McpMethod(
        int kind,
        String segment,
        String summary)
    {
        this.kind = kind;
        this.segment = segment;
        this.summary = summary;
    }

    int kind()
    {
        return kind;
    }

    String segment()
    {
        return segment;
    }

    String summary()
    {
        return summary;
    }

    String resolve(
        String field,
        McpBeginExFW beginEx)
    {
        String value;
        switch (field)
        {
        case "tool":
            value = this == TOOLS_CALL ? asString(beginEx.toolsCall().name()) : null;
            break;
        case "resource":
            value = this == RESOURCES_READ ? asString(beginEx.resourcesRead().uri()) : null;
            break;
        case "prompt":
            value = this == PROMPTS_GET ? asString(beginEx.promptsGet().name()) : null;
            break;
        case "session":
            value = session(beginEx);
            break;
        case "method":
            value = segment;
            break;
        default:
            value = null;
            break;
        }
        return value;
    }

    private String session(
        McpBeginExFW beginEx)
    {
        String value;
        switch (kind)
        {
        case KIND_LIFECYCLE:
            value = asString(beginEx.lifecycle().sessionId());
            break;
        case KIND_TOOLS_LIST:
            value = asString(beginEx.toolsList().sessionId());
            break;
        case KIND_TOOLS_CALL:
            value = asString(beginEx.toolsCall().sessionId());
            break;
        case KIND_PROMPTS_LIST:
            value = asString(beginEx.promptsList().sessionId());
            break;
        case KIND_PROMPTS_GET:
            value = asString(beginEx.promptsGet().sessionId());
            break;
        case KIND_RESOURCES_LIST:
            value = asString(beginEx.resourcesList().sessionId());
            break;
        case KIND_RESOURCES_READ:
            value = asString(beginEx.resourcesRead().sessionId());
            break;
        default:
            value = null;
            break;
        }
        return value;
    }

    private static String asString(
        String16FW value)
    {
        return value != null ? value.asString() : null;
    }
}
