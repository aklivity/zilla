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

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBlockType;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFinishReason;

public class LlmOpenAiEventMapperTest
{
    private static final int TYPE_ID = 1;

    private LlmOpenAiEventMapper mapper;
    private LlmEventMapperTestSupport support;

    @Before
    public void init()
    {
        mapper = new LlmOpenAiEventMapper(TYPE_ID);
        support = new LlmEventMapperTestSupport();
    }

    @Test
    public void shouldDecodeFirstChunkAsMessageStartAndBlockStart()
    {
        mapper.decode(null, "{\"id\":\"chatcmpl_1\",\"model\":\"gpt-4\"," +
            "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}", support);

        assertThat(support.trace, contains(
            "messageStart:0:chatcmpl_1:gpt-4:assistant",
            "blockStart:0:0:TEXT:null:null"));
    }

    @Test
    public void shouldDecodeFirstChunkWithoutChoices()
    {
        mapper.decode(null, "{\"id\":\"chatcmpl_1\",\"choices\":[]}", support);

        assertThat(support.trace, contains(
            "messageStart:0:chatcmpl_1:null:assistant",
            "blockStart:0:0:TEXT:null:null"));
    }

    @Test
    public void shouldDecodeContentChunk()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}", support);

        assertThat(support.trace, contains("data:Hello"));
    }

    @Test
    public void shouldDecodeFinishReasonChunk()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}", support);

        assertThat(support.trace, contains("blockEnd:0:0", "finish:0:STOP"));
    }

    @Test
    public void shouldDecodeFinishReasonLength()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"length\"}]}", support);

        assertThat(support.trace, contains("blockEnd:0:0", "finish:0:LENGTH"));
    }

    @Test
    public void shouldDecodeFinishReasonToolCalls()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}", support);

        assertThat(support.trace, contains("blockEnd:0:0", "finish:0:TOOL_CALL"));
    }

    @Test
    public void shouldDecodeFinishReasonContentFilter()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"content_filter\"}]}", support);

        assertThat(support.trace, contains("blockEnd:0:0", "finish:0:CONTENT_FILTER"));
    }

    @Test
    public void shouldDecodeUsageChunk()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[],\"usage\":{\"prompt_tokens\":25,\"completion_tokens\":15}}", support);

        assertThat(support.trace, contains("usage:25:15"));
    }

    @Test
    public void shouldDecodeChunkWithoutChoicesKey()
    {
        mapper.decode(null, "{\"id\":\"chatcmpl_1\"}", support);

        assertThat(support.trace, contains(
            "messageStart:0:chatcmpl_1:null:assistant",
            "blockStart:0:0:TEXT:null:null"));
    }

    @Test
    public void shouldDecodeChoiceWithoutDelta()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\"}]}", support);

        assertThat(support.trace, contains("blockEnd:0:0", "finish:0:STOP"));
    }

    @Test
    public void shouldDecodeEmptyContent()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\"},\"finish_reason\":null}]}", support);

        assertThat(support.trace, empty());
    }

    @Test
    public void shouldDecodeEmptyToolCalls()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[]},\"finish_reason\":null}]}", support);

        assertThat(support.trace, empty());
    }

    @Test
    public void shouldDecodeToolCallWithoutFunction()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[" +
            "{\"index\":0,\"id\":\"call_1\"}]},\"finish_reason\":null}]}", support);

        assertThat(support.trace, contains("blockEnd:0:0", "blockStart:0:1:TOOL_CALL:call_1:null"));
    }

    @Test
    public void shouldDecodeFinishReasonAfterBlockAlreadyClosed()
    {
        firstChunk();
        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}", support);
        support.trace.clear();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}", support);

        assertThat(support.trace, contains("finish:0:STOP"));
    }

    @Test
    public void shouldDecodeDone()
    {
        mapper.decode(null, "[DONE]", support);

        assertThat(support.trace, contains("end"));
    }

    @Test
    public void shouldDecodeToolCallStartAndArguments()
    {
        firstChunk();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[" +
            "{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"\"}}]}," +
            "\"finish_reason\":null}]}", support);

        assertThat(support.trace, contains("blockEnd:0:0", "blockStart:0:1:TOOL_CALL:call_1:get_weather"));
    }

    @Test
    public void shouldDecodeToolCallArgumentContinuation()
    {
        firstChunk();
        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[" +
            "{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"\"}}]}," +
            "\"finish_reason\":null}]}", support);
        support.trace.clear();

        mapper.decode(null, "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[" +
            "{\"index\":0,\"function\":{\"arguments\":\"{\\\"city\\\":\"}}]},\"finish_reason\":null}]}", support);

        assertThat(support.trace, contains("data:{\"city\":"));
    }

    private void firstChunk()
    {
        mapper.decode(null, "{\"id\":\"chatcmpl_1\",\"model\":\"gpt-4\"," +
            "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}", support);
        support.trace.clear();
    }

    @Test
    public void shouldEncodeMessageStart()
    {
        mapper.encode(support.messageStart(0, "chatcmpl_1", "gpt-4", "assistant"), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}],\"id\":\"chatcmpl_1\",\"model\":\"gpt-4\"}"));
    }

    @Test
    public void shouldEncodeMessageStartWithoutModel()
    {
        mapper.encode(support.messageStart(0, "chatcmpl_1", null, null), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}],\"id\":\"chatcmpl_1\"}"));
    }

    @Test
    public void shouldEncodeBlockStartTextAsNoOp()
    {
        mapper.encode(support.blockStart(0, 0, LlmBlockType.TEXT, null, null), support);

        assertThat(support.trace, empty());
    }

    @Test
    public void shouldEncodeBlockStartToolCall()
    {
        mapper.encode(support.blockStart(0, 1, LlmBlockType.TOOL_CALL, "call_1", "get_weather"), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{\"tool_calls\":[{\"index\":0,\"type\":\"function\"," +
                "\"function\":{\"arguments\":\"\",\"name\":\"get_weather\"},\"id\":\"call_1\"}]}," +
                "\"finish_reason\":null}]}"));
    }

    @Test
    public void shouldEncodeBlockStartToolCallWithoutIdOrName()
    {
        mapper.encode(support.blockStart(0, 1, LlmBlockType.TOOL_CALL, null, null), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{\"tool_calls\":[{\"index\":0,\"type\":\"function\"," +
                "\"function\":{\"arguments\":\"\"}}]},\"finish_reason\":null}]}"));
    }

    @Test
    public void shouldEncodeDataAsContent()
    {
        DirectBuffer buffer = support.utf8("Hello");
        mapper.encode(buffer, 0, buffer.capacity(), null, support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}"));
    }

    @Test
    public void shouldEncodeDataAsToolCallArguments()
    {
        mapper.encode(support.blockStart(0, 1, LlmBlockType.TOOL_CALL, "call_1", "get_weather"), support);
        support.trace.clear();

        DirectBuffer buffer = support.utf8("{\"city\":\"NYC\"}");
        mapper.encode(buffer, 0, buffer.capacity(), null, support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"city\\\":\\\"NYC\\\"}\"}}]}," +
                "\"finish_reason\":null}]}"));
    }

    @Test
    public void shouldEncodeBlockEndResetsToolCallRouting()
    {
        mapper.encode(support.blockStart(0, 1, LlmBlockType.TOOL_CALL, "call_1", "get_weather"), support);
        mapper.encode(support.blockEnd(0, 1), support);
        support.trace.clear();

        DirectBuffer buffer = support.utf8("Hello");
        mapper.encode(buffer, 0, buffer.capacity(), null, support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}"));
    }

    @Test
    public void shouldEncodeFinishStop()
    {
        mapper.encode(support.finish(0, LlmFinishReason.STOP), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{},\"finish_reason\":\"stop\"}]}"));
    }

    @Test
    public void shouldEncodeFinishLength()
    {
        mapper.encode(support.finish(0, LlmFinishReason.LENGTH), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{},\"finish_reason\":\"length\"}]}"));
    }

    @Test
    public void shouldEncodeFinishToolCalls()
    {
        mapper.encode(support.finish(0, LlmFinishReason.TOOL_CALL), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{},\"finish_reason\":\"tool_calls\"}]}"));
    }

    @Test
    public void shouldEncodeFinishContentFilter()
    {
        mapper.encode(support.finish(0, LlmFinishReason.CONTENT_FILTER), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{},\"finish_reason\":\"content_filter\"}]}"));
    }

    @Test
    public void shouldEncodeFinishError()
    {
        mapper.encode(support.finish(0, LlmFinishReason.ERROR), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0," +
                "\"delta\":{},\"finish_reason\":\"stop\"}]}"));
    }

    @Test
    public void shouldEncodeUsage()
    {
        mapper.encode(support.usage(25, 15), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[]," +
                "\"usage\":{\"prompt_tokens\":25,\"completion_tokens\":15}}"));
    }

    @Test
    public void shouldEncodeUsageWithoutTokens()
    {
        mapper.encode(support.usage(-1, -1), support);

        assertThat(support.trace, contains(
            "event:null:{\"object\":\"chat.completion.chunk\",\"choices\":[],\"usage\":{}}"));
    }

    @Test
    public void shouldEncodeKeepaliveAsNoOp()
    {
        mapper.encode(support.keepalive(), support);

        assertThat(support.trace, empty());
    }

    @Test
    public void shouldEncodeEnd()
    {
        mapper.encodeEnd(support);

        assertThat(support.trace, contains("event:null:[DONE]"));
    }
}
