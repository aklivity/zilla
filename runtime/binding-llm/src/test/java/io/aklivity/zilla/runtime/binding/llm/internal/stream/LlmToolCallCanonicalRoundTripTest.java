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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRoundTrips;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.quote;

import java.util.function.Consumer;

import org.junit.Test;

public class LlmToolCallCanonicalRoundTripTest
{
    private static final String LONG = "x".repeat(300);

    private final Consumer<LlmSectionAttributes> id = attributes -> attributes.toolCallId("toolu_1");
    private final Consumer<LlmSectionAttributes> name = attributes -> attributes.toolName("get_weather");
    private final Consumer<LlmSectionAttributes> attributes = id.andThen(name);

    @Test
    public void shouldRoundTripAnthropicToolUse()
    {
        assertRoundTrips(new LlmAnthropicToolCallToCanonical(), new LlmAnthropicToolCallFromCanonical("tool_use"),
            attributes,
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'city':'Paris'}}"));
    }

    @Test
    public void shouldRoundTripAnthropicServerToolUse()
    {
        assertRoundTrips(new LlmAnthropicToolCallToCanonical(), new LlmAnthropicToolCallFromCanonical("server_tool_use"),
            attributes,
            json("{'type':'server_tool_use','id':'toolu_1','name':'get_weather','input':{'query':'zilla'}}"));
    }

    @Test
    public void shouldRoundTripCacheControlBeforeInput()
    {
        assertRoundTrips(new LlmAnthropicToolCallToCanonical(), new LlmAnthropicToolCallFromCanonical("tool_use"),
            attributes,
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','cache_control':{'type':'ephemeral'}," +
                "'input':{'city':'Paris'}}"));
    }

    @Test
    public void shouldRoundTripCacheControlAfterInput()
    {
        assertRoundTrips(new LlmAnthropicToolCallToCanonical(), new LlmAnthropicToolCallFromCanonical("server_tool_use"),
            attributes,
            json("{'type':'server_tool_use','id':'toolu_1','name':'get_weather','input':{'city':'Paris'}," +
                "'cache_control':{'type':'ephemeral','ttl':[1,{'a':null}]}}"));
    }

    @Test
    public void shouldRoundTripLongValuesAndManyNativeOnlyMembers()
    {
        assertRoundTrips(new LlmAnthropicToolCallToCanonical(), new LlmAnthropicToolCallFromCanonical("tool_use"),
            attributes,
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','note':'" + LONG + "','count':12345," +
                "'tags':[1,2],'input':{'text':'" + LONG + "'},'gap':null}"));
    }

    @Test
    public void shouldRoundTripOpenaiToolCall()
    {
        assertRoundTrips(new LlmOpenaiToolCallToCanonical(), new LlmOpenaiToolCallFromCanonical(),
            attributes,
            json("{'id':'toolu_1','type':'function','function':{'name':'get_weather','arguments':") +
                quote(json("{'city':'Paris'}")) + "}}");
    }

    @Test
    public void shouldRoundTripOpenaiNativeOnlyMembersBeforeAndAfterArguments()
    {
        assertRoundTrips(new LlmOpenaiToolCallToCanonical(), new LlmOpenaiToolCallFromCanonical(),
            attributes,
            json("{'id':'toolu_1','type':'function','index':0,'function':{'name':'get_weather','arguments':") +
                quote(json("{'text':'" + LONG + "'}")) + json("},'note':'" + LONG + "'}"));
    }
}
