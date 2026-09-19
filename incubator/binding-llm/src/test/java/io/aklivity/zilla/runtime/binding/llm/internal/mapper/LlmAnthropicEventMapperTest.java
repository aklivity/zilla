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
package io.aklivity.zilla.runtime.binding.llm.internal.mapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Test;

public class LlmAnthropicEventMapperTest
{
    private LlmAnthropicEventMapper mapper;
    private LlmEventMapperTestSupport support;

    @Before
    public void init()
    {
        mapper = new LlmAnthropicEventMapper();
        support = new LlmEventMapperTestSupport();
    }

    @Test
    public void shouldDecodeMessageStart()
    {
        mapper.decode("message_start", "{\"message\":{\"id\":\"msg_1\",\"model\":\"claude-3\",\"role\":\"assistant\"," +
            "\"usage\":{\"input_tokens\":25}}}", support);

        assertThat(support.trace, contains("messageStart:0:msg_1:claude-3:assistant"));
    }

    @Test
    public void shouldDecodeMessageStartWithoutUsage()
    {
        mapper.decode("message_start", "{\"message\":{\"id\":\"msg_1\"}}", support);

        assertThat(support.trace, contains("messageStart:0:msg_1:null:null"));
    }

    @Test
    public void shouldDecodeContentBlockStartTextWithoutEvent()
    {
        mapper.decode("content_block_start", "{\"index\":0,\"content_block\":{\"type\":\"text\"}}", support);

        assertThat(support.trace, empty());
    }

    @Test
    public void shouldDecodeContentBlockStartToolUse()
    {
        mapper.decode("content_block_start",
            "{\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool_1\",\"name\":\"get_weather\"}}", support);

        assertThat(support.trace, contains("blockStart:0:1:TOOL_CALL:tool_1:get_weather"));
    }

    @Test
    public void shouldDecodePing()
    {
        mapper.decode("ping", "{\"type\":\"ping\"}", support);

        assertThat(support.trace, empty());
    }

    @Test
    public void shouldDecodeContentBlockDeltaText()
    {
        mapper.decode("content_block_delta", "{\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}", support);

        assertThat(support.trace, contains("data:Hello"));
    }

    @Test
    public void shouldDecodeContentBlockDeltaToolInput()
    {
        mapper.decode("content_block_start",
            "{\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool_1\",\"name\":\"get_weather\"}}", support);
        support.trace.clear();

        mapper.decode("content_block_delta",
            "{\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"a\\\":1}\"}}", support);

        assertThat(support.trace, contains("data:{\"a\":1}"));
    }

    @Test
    public void shouldDecodeContentBlockStopText()
    {
        mapper.decode("content_block_start", "{\"index\":0,\"content_block\":{\"type\":\"text\"}}", support);
        support.trace.clear();

        mapper.decode("content_block_stop", "{\"index\":0}", support);

        assertThat(support.trace, empty());
    }

    @Test
    public void shouldDecodeContentBlockStopToolUse()
    {
        mapper.decode("content_block_start",
            "{\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool_1\",\"name\":\"get_weather\"}}", support);
        support.trace.clear();

        mapper.decode("content_block_stop", "{\"index\":1}", support);

        assertThat(support.trace, contains("blockEnd:0:1"));
    }

    @Test
    public void shouldDecodeMessageDeltaEndTurn()
    {
        mapper.decode("message_start", "{\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":25}}}", support);
        support.trace.clear();

        mapper.decode("message_delta",
            "{\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":15}}", support);

        assertThat(support.trace, contains("finish:0:STOP", "usage:25:15"));
    }

    @Test
    public void shouldDecodeMessageDeltaMaxTokens()
    {
        mapper.decode("message_delta", "{\"delta\":{\"stop_reason\":\"max_tokens\"}}", support);

        assertThat(support.trace, contains("finish:0:LENGTH", "usage:-1:-1"));
    }

    @Test
    public void shouldDecodeMessageDeltaToolUse()
    {
        mapper.decode("message_delta", "{\"delta\":{\"stop_reason\":\"tool_use\"}}", support);

        assertThat(support.trace, contains("finish:0:TOOL_CALL", "usage:-1:-1"));
    }

    @Test
    public void shouldDecodeMessageStop()
    {
        mapper.decode("message_stop", "{\"type\":\"message_stop\"}", support);

        assertThat(support.trace, contains("end"));
    }

    @Test
    public void shouldEncodeMessageStart()
    {
        mapper.encodeMessageStart(0, "msg_1", "claude-3", "assistant", support);

        assertThat(support.trace, contains(
            "event:message_start:{\"type\":\"message_start\",\"message\":" +
                "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-3\"}}"));
    }

    @Test
    public void shouldEncodeMessageStartWithoutModelDefaultsRole()
    {
        mapper.encodeMessageStart(0, "msg_1", null, null, support);

        assertThat(support.trace, contains(
            "event:message_start:{\"type\":\"message_start\",\"message\":" +
                "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\"}}"));
    }

    // llm.idl LlmDataEx: choiceIndex never survives a cross-dialect route to
    // Anthropic when n > 1 upstream -- a parallel completion's own message_start is
    // indistinguishable from choiceIndex 0's, since Anthropic has no concept of parallel choices
    @Test
    public void shouldCollapseChoiceIndexOnMessageStart()
    {
        mapper.encodeMessageStart(1, "msg_1", "claude-3", "assistant", support);

        assertThat(support.trace, contains(
            "event:message_start:{\"type\":\"message_start\",\"message\":" +
                "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-3\"}}"));
    }

    @Test
    public void shouldEncodeBlockStartText()
    {
        mapper.encodeBlockStart(0, 0, LlmCanonicalBlockKind.TEXT, null, null, support);

        assertThat(support.trace, contains(
            "event:content_block_start:{\"type\":\"content_block_start\",\"index\":0," +
                "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"));
    }

    @Test
    public void shouldEncodeBlockStartToolCall()
    {
        mapper.encodeBlockStart(0, 1, LlmCanonicalBlockKind.TOOL_CALL, "tool_1", "get_weather", support);

        assertThat(support.trace, contains(
            "event:content_block_start:{\"type\":\"content_block_start\",\"index\":1," +
                "\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool_1\",\"name\":\"get_weather\"}}"));
    }

    @Test
    public void shouldEncodeBlockStartToolCallWithoutIdOrName()
    {
        mapper.encodeBlockStart(0, 1, LlmCanonicalBlockKind.TOOL_CALL, null, null, support);

        assertThat(support.trace, contains(
            "event:content_block_start:{\"type\":\"content_block_start\",\"index\":1," +
                "\"content_block\":{\"type\":\"tool_use\"}}"));
    }

    @Test
    public void shouldEncodeDataAsTextDelta()
    {
        mapper.encodeBlockStart(0, 0, LlmCanonicalBlockKind.TEXT, null, null, support);
        support.trace.clear();

        DirectBuffer buffer = support.utf8("Hello");
        mapper.encode(buffer, 0, buffer.capacity(), support);

        assertThat(support.trace, contains(
            "event:content_block_delta:{\"type\":\"content_block_delta\",\"index\":0," +
                "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}"));
    }

    @Test
    public void shouldEncodeDataAsInputJsonDelta()
    {
        mapper.encodeBlockStart(0, 1, LlmCanonicalBlockKind.TOOL_CALL, "tool_1", "get_weather", support);
        support.trace.clear();

        DirectBuffer buffer = support.utf8("{\"a\":1}");
        mapper.encode(buffer, 0, buffer.capacity(), support);

        assertThat(support.trace, contains(
            "event:content_block_delta:{\"type\":\"content_block_delta\",\"index\":1," +
                "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"a\\\":1}\"}}"));
    }

    @Test
    public void shouldEncodeBlockEnd()
    {
        mapper.encodeBlockEnd(0, 1, support);

        assertThat(support.trace, contains(
            "event:content_block_stop:{\"type\":\"content_block_stop\",\"index\":1}"));
    }

    @Test
    public void shouldEncodeFinishStop()
    {
        mapper.encodeFinish(0, LlmCanonicalFinishReason.STOP, support);

        assertThat(support.trace, contains(
            "event:message_delta:{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}," +
                "\"usage\":{\"output_tokens\":0}}"));
    }

    @Test
    public void shouldEncodeFinishMaxTokens()
    {
        mapper.encodeFinish(0, LlmCanonicalFinishReason.LENGTH, support);

        assertThat(support.trace, contains(
            "event:message_delta:{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}," +
                "\"usage\":{\"output_tokens\":0}}"));
    }

    @Test
    public void shouldEncodeFinishToolUse()
    {
        mapper.encodeFinish(0, LlmCanonicalFinishReason.TOOL_CALL, support);

        assertThat(support.trace, contains(
            "event:message_delta:{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}," +
                "\"usage\":{\"output_tokens\":0}}"));
    }

    @Test
    public void shouldHoldUsageUntilFinish()
    {
        mapper.encodeUsage(25, 15, support);
        assertThat(support.trace, empty());

        mapper.encodeFinish(0, LlmCanonicalFinishReason.STOP, support);

        assertThat(support.trace, contains(
            "event:message_delta:{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}," +
                "\"usage\":{\"output_tokens\":15}}"));
    }

    @Test
    public void shouldDropUsageAfterFinishAlreadySent()
    {
        mapper.encodeFinish(0, LlmCanonicalFinishReason.STOP, support);
        support.trace.clear();

        mapper.encodeUsage(25, 15, support);

        assertThat(support.trace, empty());
    }

    @Test
    public void shouldEncodeEnd()
    {
        mapper.encodeEnd(support);

        assertThat(support.trace, contains("event:message_stop:{\"type\":\"message_stop\"}"));
    }
}
