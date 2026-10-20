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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public class LlmOpenaiResponsesAttributeProbeTest
{
    private static final int OBJECT_PREFIX_EVENTS = 2;

    private final LlmOpenaiResponsesAttributeProbe probe = new LlmOpenaiResponsesAttributeProbe();

    @Test
    public void shouldResolveFunctionCallFromCallIdAndName()
    {
        assertThat(run(LlmSectionType.TOOL_CALL, "function_call", null, null,
            "{\"call_id\":\"call_1\",\"name\":\"get_weather\",\"x\":1}"),
            equalTo(LlmAttributeProbe.Status.RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldCompleteUnresolvedWhenIdentityIsMissing()
    {
        assertThat(run(LlmSectionType.TOOL_CALL, "function_call", null, null, "{\"call_id\":\"call_1\",\"x\":1}"),
            equalTo(LlmAttributeProbe.Status.COMPLETE));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
        assertThat(probe.attributes().toolName(), nullValue());
    }

    @Test
    public void shouldUseKnownNameAndIdForProviderCall()
    {
        assertThat(run(LlmSectionType.SERVER_TOOL_CALL, "web_search_call", "ws_1", null, "{\"x\":1}"),
            equalTo(LlmAttributeProbe.Status.RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("ws_1"));
        assertThat(probe.attributes().toolName(), equalTo("web_search"));
    }

    @Test
    public void shouldUseNameMemberForNamedProviderCall()
    {
        assertThat(run(LlmSectionType.SERVER_TOOL_CALL, "mcp_call", "mcp_1", null, "{\"name\":\"ask\"}"),
            equalTo(LlmAttributeProbe.Status.RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("mcp_1"));
        assertThat(probe.attributes().toolName(), equalTo("ask"));
    }

    @Test
    public void shouldResolveProviderResultFromId()
    {
        assertThat(run(LlmSectionType.SERVER_TOOL_RESULT, "code_interpreter_call", "ci_1", null, "{\"outputs\":[]}"),
            equalTo(LlmAttributeProbe.Status.RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("ci_1"));
    }

    @Test
    public void shouldResolveProviderResultFromCallId()
    {
        assertThat(run(LlmSectionType.SERVER_TOOL_RESULT, "custom_tool_call_output", null, null,
            "{\"call_id\":\"call_1\",\"output\":\"x\"}"),
            equalTo(LlmAttributeProbe.Status.RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
    }

    @Test
    public void shouldResolveToolDefinitionFromName()
    {
        assertThat(run(LlmSectionType.TOOL_DEFINITION, "function", null, null,
            "{\"description\":\"d\",\"name\":\"get_weather\"}"),
            equalTo(LlmAttributeProbe.Status.RESOLVED));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldNameServerToolDefinitionFromTypeUntilNameIsKnown()
    {
        assertThat(run(LlmSectionType.SERVER_TOOL_DEFINITION, "web_search", null, null, "{\"x\":1}"),
            equalTo(LlmAttributeProbe.Status.COMPLETE));
        assertThat(probe.attributes().toolName(), equalTo("web_search"));
        assertThat(run(LlmSectionType.SERVER_TOOL_DEFINITION, "custom", null, null, "{\"name\":\"code_exec\"}"),
            equalTo(LlmAttributeProbe.Status.RESOLVED));
        assertThat(probe.attributes().toolName(), equalTo("code_exec"));
    }

    @Test
    public void shouldRejectIdentityLargerThanWindow()
    {
        assertThat(run(LlmSectionType.TOOL_CALL, "function_call", null, null,
            "{\"call_id\":\"" + "x".repeat(4096) + "\"}", 96),
            equalTo(LlmAttributeProbe.Status.REJECTED));
    }

    private LlmAttributeProbe.Status run(
        LlmSectionType type,
        String itemType,
        String id,
        String name,
        String json)
    {
        return run(type, itemType, id, name, json, json.length());
    }

    private LlmAttributeProbe.Status run(
        LlmSectionType type,
        String itemType,
        String id,
        String name,
        String json,
        int window)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();

        parser.wrap(new UnsafeBufferEx(bytes), 0, Math.min(window, bytes.length), window >= bytes.length);

        for (int event = 0; event < OBJECT_PREFIX_EVENTS; event++)
        {
            parser.nextEvent();
        }

        probe.begin(type);
        probe.configure(itemType, id, name);

        LlmAttributeProbe.Status status = LlmAttributeProbe.Status.PENDING;

        while (status == LlmAttributeProbe.Status.PENDING && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            status = probe.onEvent(event, parser);
        }

        return status;
    }
}
