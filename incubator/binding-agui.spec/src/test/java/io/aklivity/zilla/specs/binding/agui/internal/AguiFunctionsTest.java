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
package io.aklivity.zilla.specs.binding.agui.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;

import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiAbortExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiBeginExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiEncryptedSubtype;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiRole;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiStateType;

public class AguiFunctionsTest
{
    @Test
    public void shouldGetPrefixName()
    {
        assertEquals("agui", new AguiFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateBeginEx()
    {
        byte[] bytes = AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .parentRunId("run-0")
            .build();

        AguiBeginExFW beginEx = new AguiBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(0x01, beginEx.typeId());
        assertEquals("thread-1", beginEx.threadId().asString());
        assertEquals("run-1", beginEx.runId().asString());
        assertEquals("run-0", beginEx.parentRunId().asString());
    }

    @Test
    public void shouldGenerateBeginExWithoutParentRunId()
    {
        byte[] bytes = AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .build();

        AguiBeginExFW beginEx = new AguiBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertNull(beginEx.parentRunId().asString());
    }

    @Test
    public void shouldMatchBeginEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchBeginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .parentRunId("run-0")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .parentRunId("run-0")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchBeginEx().build();

        ByteBuffer byteBuf = ByteBuffer.wrap(AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchEmptyBeginEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchBeginEx().build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExWithDifferentRunId() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchBeginEx()
            .runId("run-2")
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.beginEx()
            .typeId(0x01)
            .threadId("thread-1")
            .runId("run-1")
            .build()));
    }

    @Test
    public void shouldGenerateTextDataEx()
    {
        byte[] bytes = AguiFunctions.dataEx()
            .typeId(0x01)
            .text()
                .messageId("msg-2")
                .role("USER")
                .build()
            .build();

        AguiDataExFW dataEx = new AguiDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(0x01, dataEx.typeId());
        assertEquals(AguiDataExFW.KIND_TEXT, dataEx.kind());
        assertEquals("msg-2", dataEx.text().messageId().asString());
        assertEquals(AguiRole.USER, dataEx.text().role().get());
    }

    @Test
    public void shouldGenerateTextDataExWithDefaultRole()
    {
        byte[] bytes = AguiFunctions.dataEx()
            .typeId(0x01)
            .text()
                .messageId("msg-2")
                .build()
            .build();

        AguiDataExFW dataEx = new AguiDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(AguiRole.ASSISTANT, dataEx.text().role().get());
    }

    @Test
    public void shouldMatchTextDataEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .typeId(0x01)
            .text()
                .messageId("msg-2")
                .role("ASSISTANT")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .text()
                .messageId("msg-2")
                .build()
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchTextDataExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx().build();

        ByteBuffer byteBuf = ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .text()
                .messageId("msg-2")
                .build()
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchEmptyDataEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx().build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchTextDataExWithDifferentMessageId() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .text()
                .messageId("msg-3")
                .build()
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .text()
                .messageId("msg-2")
                .build()
            .build()));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchTextDataExWithDifferentRole() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .text()
                .role("USER")
                .build()
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .text()
                .messageId("msg-2")
                .build()
            .build()));
    }

    @Test
    public void shouldGenerateEndEx()
    {
        byte[] bytes = AguiFunctions.endEx()
            .typeId(0x01)
            .outcome("SUCCESS")
            .build();

        AguiEndExFW endEx = new AguiEndExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(0x01, endEx.typeId());
        assertEquals(AguiOutcomeType.SUCCESS, endEx.outcome().get());
    }

    @Test
    public void shouldGenerateEndExWithDefaultOutcome()
    {
        byte[] bytes = AguiFunctions.endEx()
            .typeId(0x01)
            .build();

        AguiEndExFW endEx = new AguiEndExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(AguiOutcomeType.NONE, endEx.outcome().get());
    }

    @Test
    public void shouldMatchEndEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchEndEx()
            .typeId(0x01)
            .outcome("SUCCESS")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(AguiFunctions.endEx()
            .typeId(0x01)
            .outcome("SUCCESS")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchEmptyEndEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchEndEx().build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchEndExWithDifferentOutcome() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchEndEx()
            .outcome("INTERRUPT")
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.endEx()
            .typeId(0x01)
            .outcome("SUCCESS")
            .build()));
    }

    @Test
    public void shouldGenerateReasoningDataEx()
    {
        byte[] bytes = AguiFunctions.dataEx()
            .typeId(0x01)
            .reasoning()
                .messageId("r1")
                .groupId("g1")
                .build()
            .build();

        AguiDataExFW dataEx = new AguiDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(AguiDataExFW.KIND_REASONING, dataEx.kind());
        assertEquals("r1", dataEx.reasoning().messageId().asString());
        assertEquals("g1", dataEx.reasoning().groupId().asString());
    }

    @Test
    public void shouldGenerateReasoningDataExWithoutGroupId()
    {
        byte[] bytes = AguiFunctions.dataEx()
            .typeId(0x01)
            .reasoning()
                .messageId("r1")
                .build()
            .build();

        AguiDataExFW dataEx = new AguiDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertNull(dataEx.reasoning().groupId().asString());
    }

    @Test
    public void shouldMatchReasoningDataEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .reasoning()
                .messageId("r1")
                .groupId("g1")
                .build()
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .reasoning()
                .messageId("r1")
                .groupId("g1")
                .build()
            .build())));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchReasoningDataExWithDifferentGroupId() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .reasoning()
                .groupId("g2")
                .build()
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .reasoning()
                .messageId("r1")
                .groupId("g1")
                .build()
            .build()));
    }

    @Test
    public void shouldGenerateReasoningEncryptedDataEx()
    {
        byte[] bytes = AguiFunctions.dataEx()
            .typeId(0x01)
            .reasoningEncrypted()
                .subtype("TOOL_CALL")
                .entityId("call-1")
                .build()
            .build();

        AguiDataExFW dataEx = new AguiDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(AguiDataExFW.KIND_REASONING_ENCRYPTED, dataEx.kind());
        assertEquals(AguiEncryptedSubtype.TOOL_CALL, dataEx.reasoningEncrypted().subtype().get());
        assertEquals("call-1", dataEx.reasoningEncrypted().entityId().asString());
    }

    @Test
    public void shouldMatchReasoningEncryptedDataEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .reasoningEncrypted()
                .subtype("MESSAGE")
                .entityId("r1")
                .build()
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .reasoningEncrypted()
                .subtype("MESSAGE")
                .entityId("r1")
                .build()
            .build())));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchReasoningEncryptedDataExWithDifferentSubtype() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .reasoningEncrypted()
                .subtype("TOOL_CALL")
                .build()
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .reasoningEncrypted()
                .subtype("MESSAGE")
                .entityId("r1")
                .build()
            .build()));
    }

    @Test
    public void shouldGenerateToolCallDataEx()
    {
        byte[] bytes = AguiFunctions.dataEx()
            .typeId(0x01)
            .toolCall()
                .toolCallId("call-1")
                .toolCallName("get_weather")
                .parentMessageId("msg-2")
                .build()
            .build();

        AguiDataExFW dataEx = new AguiDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(AguiDataExFW.KIND_TOOL_CALL, dataEx.kind());
        assertEquals("call-1", dataEx.toolCall().toolCallId().asString());
        assertEquals("get_weather", dataEx.toolCall().toolCallName().asString());
        assertEquals("msg-2", dataEx.toolCall().parentMessageId().asString());
    }

    @Test
    public void shouldMatchToolCallDataEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .toolCall()
                .toolCallId("call-1")
                .toolCallName("get_weather")
                .parentMessageId("msg-2")
                .build()
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .toolCall()
                .toolCallId("call-1")
                .toolCallName("get_weather")
                .parentMessageId("msg-2")
                .build()
            .build())));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchToolCallDataExWithDifferentName() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .toolCall()
                .toolCallName("get_time")
                .build()
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .toolCall()
                .toolCallId("call-1")
                .toolCallName("get_weather")
                .build()
            .build()));
    }

    @Test
    public void shouldGenerateToolResultDataEx()
    {
        byte[] bytes = AguiFunctions.dataEx()
            .typeId(0x01)
            .toolResult()
                .messageId("msg-3")
                .toolCallId("call-1")
                .build()
            .build();

        AguiDataExFW dataEx = new AguiDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(AguiDataExFW.KIND_TOOL_RESULT, dataEx.kind());
        assertEquals("msg-3", dataEx.toolResult().messageId().asString());
        assertEquals("call-1", dataEx.toolResult().toolCallId().asString());
    }

    @Test
    public void shouldMatchToolResultDataEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .toolResult()
                .messageId("msg-3")
                .toolCallId("call-1")
                .build()
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .toolResult()
                .messageId("msg-3")
                .toolCallId("call-1")
                .build()
            .build())));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchToolResultDataExWithDifferentToolCallId() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .toolResult()
                .toolCallId("call-2")
                .build()
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .toolResult()
                .messageId("msg-3")
                .toolCallId("call-1")
                .build()
            .build()));
    }

    @Test
    public void shouldGenerateStateDataEx()
    {
        byte[] bytes = AguiFunctions.dataEx()
            .typeId(0x01)
            .state()
                .type("DELTA")
                .build()
            .build();

        AguiDataExFW dataEx = new AguiDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(AguiDataExFW.KIND_STATE, dataEx.kind());
        assertEquals(AguiStateType.DELTA, dataEx.state().type().get());
    }

    @Test
    public void shouldMatchStateDataEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .state()
                .type("MESSAGES_SNAPSHOT")
                .build()
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .state()
                .type("MESSAGES_SNAPSHOT")
                .build()
            .build())));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchStateDataExWithDifferentType() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .state()
                .type("SNAPSHOT")
                .build()
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .state()
                .type("DELTA")
                .build()
            .build()));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchDataExWithDifferentKind() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchDataEx()
            .state()
                .type("SNAPSHOT")
                .build()
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.dataEx()
            .typeId(0x01)
            .text()
                .messageId("msg-2")
                .build()
            .build()));
    }

    @Test
    public void shouldGenerateAbortEx()
    {
        byte[] bytes = AguiFunctions.abortEx()
            .typeId(0x01)
            .code("agent_failure")
            .message("boom")
            .build();

        AguiAbortExFW abortEx = new AguiAbortExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(0x01, abortEx.typeId());
        assertEquals("agent_failure", abortEx.error().code().asString());
        assertEquals("boom", abortEx.error().message().asString());
    }

    @Test
    public void shouldMatchAbortEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchAbortEx()
            .typeId(0x01)
            .code("agent_failure")
            .message("boom")
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(AguiFunctions.abortEx()
            .typeId(0x01)
            .code("agent_failure")
            .message("boom")
            .build())));
    }

    @Test
    public void shouldNotMatchEmptyAbortEx() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchAbortEx().build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAbortExWithDifferentCode() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchAbortEx()
            .code("other")
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.abortEx()
            .typeId(0x01)
            .code("agent_failure")
            .message("boom")
            .build()));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAbortExWithDifferentMessage() throws Exception
    {
        BytesMatcher matcher = AguiFunctions.matchAbortEx()
            .message("other")
            .build();

        matcher.match(ByteBuffer.wrap(AguiFunctions.abortEx()
            .typeId(0x01)
            .code("agent_failure")
            .message("boom")
            .build()));
    }
}
