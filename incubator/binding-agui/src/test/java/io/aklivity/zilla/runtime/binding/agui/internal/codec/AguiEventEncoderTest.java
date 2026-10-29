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
package io.aklivity.zilla.runtime.binding.agui.internal.codec;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.agui.internal.AguiFunctions;

public class AguiEventEncoderTest
{
    private final AguiEventEncoder encoder = new AguiEventEncoder("thread-1", "run-1", null);

    @Test
    public void shouldEncodeRunStarted()
    {
        assertThat(text(encoder.runStarted()),
            equalTo("{\"type\":\"RUN_STARTED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}"));
    }

    @Test
    public void shouldEncodeRunStartedWithParentRunId()
    {
        final AguiEventEncoder resumed = new AguiEventEncoder("thread-1", "run-2", "run-0");

        assertThat(text(resumed.runStarted()),
            equalTo("{\"type\":\"RUN_STARTED\",\"threadId\":\"thread-1\",\"runId\":\"run-2\",\"parentRunId\":\"run-0\"}"));
    }

    @Test
    public void shouldEncodeRunFinished()
    {
        assertThat(text(encoder.runFinished(endEx(AguiFunctions.endEx().typeId(1).build()))),
            equalTo("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}"));
    }

    @Test
    public void shouldEncodeRunFinishedWithoutEndEx()
    {
        assertThat(text(encoder.runFinished(null)),
            equalTo("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}"));
    }

    @Test
    public void shouldEncodeRunFinishedWithSuccessDetail()
    {
        final AguiEndExFW endEx = endEx(AguiFunctions.endEx()
            .typeId(1)
            .outcome("SUCCESS")
            .detail("{\"result\":{\"answer\":42},\"outcome\":{\"pendingToolCallIds\":[\"call-1\"]}}")
            .build());

        assertThat(text(encoder.runFinished(endEx)),
            equalTo("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"," +
                "\"result\":{\"answer\":42}," +
                "\"outcome\":{\"type\":\"success\",\"pendingToolCallIds\":[\"call-1\"]}}"));
    }

    @Test
    public void shouldEncodeRunFinishedWithInterruptDetail()
    {
        final AguiEndExFW endEx = endEx(AguiFunctions.endEx()
            .typeId(1)
            .outcome("INTERRUPT")
            .detail("{\"outcome\":{\"interrupts\":[{\"id\":\"int-1\",\"reason\":\"confirmation\"}]}}")
            .build());

        assertThat(text(encoder.runFinished(endEx)),
            equalTo("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"," +
                "\"outcome\":{\"type\":\"interrupt\",\"interrupts\":[{\"id\":\"int-1\",\"reason\":\"confirmation\"}]}}"));
    }

    @Test
    public void shouldEncodeRunFinishedWithCancelledOutcome()
    {
        final AguiEndExFW endEx = endEx(AguiFunctions.endEx().typeId(1).outcome("CANCELLED").build());

        assertThat(text(encoder.runFinished(endEx)),
            equalTo("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"," +
                "\"outcome\":{\"type\":\"cancelled\"}}"));
    }

    @Test
    public void shouldEncodeRunFinishedWithDetailWithoutOutcomeType()
    {
        final AguiEndExFW endEx = endEx(AguiFunctions.endEx()
            .typeId(1)
            .detail("{\"usage\":[{\"inputTokens\":3}]}")
            .build());

        assertThat(text(encoder.runFinished(endEx)),
            equalTo("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"," +
                "\"usage\":[{\"inputTokens\":3}]}"));
    }

    @Test(expected = AguiSequenceException.class)
    public void shouldRejectRunFinishedDetailThatIsNotObject()
    {
        encoder.runFinished(endEx(AguiFunctions.endEx().typeId(1).detail("[1,2]").build()));
    }

    @Test
    public void shouldEncodeRunError()
    {
        assertThat(text(encoder.runError("boom", "agent_failure")),
            equalTo("{\"type\":\"RUN_ERROR\",\"message\":\"boom\",\"code\":\"agent_failure\"}"));
    }

    @Test
    public void shouldEncodeRunErrorWithoutCode()
    {
        assertThat(text(encoder.runError("boom \"quoted\"", null)),
            equalTo("{\"type\":\"RUN_ERROR\",\"message\":\"boom \\\"quoted\\\"\"}"));
    }

    @Test
    public void shouldEncodeTextMessage()
    {
        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .text()
                .messageId("msg-2")
                .role("ASSISTANT")
                .build()
            .build()), false);

        assertThat(encoding.mode, equalTo(AguiContentMode.DELTA_EVENTS));
        assertThat(text(encoding.open),
            equalTo("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}"));
        assertThat(new String(encoding.prefix, UTF_8),
            equalTo("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\""));
        assertThat(new String(encoding.suffix, UTF_8), equalTo("\"}"));
        assertThat(text(encoding.close), equalTo("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}"));
        assertThat(encoding.contentOpen, equalTo(new byte[0][]));
        assertThat(encoding.contentClose, equalTo(new byte[0][]));
    }

    @Test
    public void shouldEncodeTextMessageWithMeta()
    {
        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .text()
                .timestamp(1730130000123L)
                .subagent("sub-1")
                .metadata("{\"source\":\"test\"}")
                .messageId("msg-2")
                .role("USER")
                .build()
            .build()), false);

        assertThat(text(encoding.open),
            equalTo("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"user\"," +
                "\"timestamp\":1730130000123,\"subagentRunId\":\"sub-1\",\"metadata\":{\"source\":\"test\"}}"));
        assertThat(new String(encoding.suffix, UTF_8), equalTo("\",\"subagentRunId\":\"sub-1\"}"));
        assertThat(text(encoding.close),
            equalTo("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\",\"subagentRunId\":\"sub-1\"}"));
    }

    @Test
    public void shouldEncodeReasoningInOwnSpan()
    {
        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .reasoning()
                .messageId("r1")
                .build()
            .build()), false);

        assertThat(text(encoding.open), equalTo("{\"type\":\"REASONING_START\",\"messageId\":\"r1\"}"));
        assertThat(text(encoding.contentOpen),
            equalTo("{\"type\":\"REASONING_MESSAGE_START\",\"messageId\":\"r1\",\"role\":\"reasoning\"}"));
        assertThat(new String(encoding.prefix, UTF_8),
            equalTo("{\"type\":\"REASONING_MESSAGE_CONTENT\",\"messageId\":\"r1\",\"delta\":\""));
        assertThat(text(encoding.contentClose), equalTo("{\"type\":\"REASONING_MESSAGE_END\",\"messageId\":\"r1\"}"));
        assertThat(encoding.close.length, equalTo(0));
    }

    @Test
    public void shouldKeepReasoningSpanOpenWithinGroup()
    {
        encoder.message(reasoning("m1", "g1"), false);

        final AguiMessageEncoding second = encoder.message(reasoning("m2", "g1"), false);

        assertThat(second.open.length, equalTo(0));
    }

    @Test
    public void shouldCloseReasoningSpanWhenGroupChanges()
    {
        encoder.message(reasoning("m1", "g1"), false);

        final AguiMessageEncoding next = encoder.message(reasoning("m3", "g2"), false);

        assertThat(text(next.open),
            equalTo("{\"type\":\"REASONING_END\",\"messageId\":\"g1\"}" +
                "{\"type\":\"REASONING_START\",\"messageId\":\"g2\"}"));
    }

    @Test
    public void shouldCloseReasoningSpanOnOtherMessageKind()
    {
        encoder.message(reasoning("m1", "g1"), false);

        final AguiMessageEncoding next = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .text()
                .messageId("msg-2")
                .build()
            .build()), false);

        assertThat(text(next.open),
            equalTo("{\"type\":\"REASONING_END\",\"messageId\":\"g1\"}" +
                "{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}"));
    }

    @Test
    public void shouldCloseReasoningSpanOnRunFinished()
    {
        encoder.message(reasoning("m1", "g1"), false);

        assertThat(text(encoder.runFinished(endEx(AguiFunctions.endEx().typeId(1).build()))),
            equalTo("{\"type\":\"REASONING_END\",\"messageId\":\"g1\"}" +
                "{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}"));
    }

    @Test
    public void shouldRetainSubagentAttributionOnReasoningSpan()
    {
        encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .reasoning()
                .subagent("sub-1")
                .messageId("m1")
                .groupId("g1")
                .build()
            .build()), false);

        assertThat(text(encoder.runFinished(endEx(AguiFunctions.endEx().typeId(1).build()))),
            equalTo("{\"type\":\"REASONING_END\",\"messageId\":\"g1\",\"subagentRunId\":\"sub-1\"}" +
                "{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}"));
    }

    @Test
    public void shouldEncodeReasoningEncryptedWithinSpan()
    {
        encoder.message(reasoning("r1", null), false);

        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .reasoningEncrypted()
                .subtype("TOOL_CALL")
                .entityId("call-1")
                .build()
            .build()), false);

        assertThat(encoding.mode, equalTo(AguiContentMode.STRING_MEMBER));
        assertThat(encoding.open.length, equalTo(0));
        assertThat(new String(encoding.prefix, UTF_8),
            equalTo("{\"type\":\"REASONING_ENCRYPTED_VALUE\",\"subtype\":\"tool-call\",\"entityId\":\"call-1\"," +
                "\"encryptedValue\":\""));
        assertThat(new String(encoding.suffix, UTF_8), equalTo("\"}"));
        assertThat(text(encoder.runFinished(endEx(AguiFunctions.endEx().typeId(1).build()))),
            equalTo("{\"type\":\"REASONING_END\",\"messageId\":\"r1\"}" +
                "{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}"));
    }

    @Test
    public void shouldEncodeToolCall()
    {
        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .toolCall()
                .toolCallId("call-1")
                .toolCallName("get_weather")
                .parentMessageId("msg-2")
                .build()
            .build()), false);

        assertThat(encoding.mode, equalTo(AguiContentMode.DELTA_EVENTS));
        assertThat(text(encoding.open),
            equalTo("{\"type\":\"TOOL_CALL_START\",\"toolCallId\":\"call-1\",\"toolCallName\":\"get_weather\"," +
                "\"parentMessageId\":\"msg-2\"}"));
        assertThat(new String(encoding.prefix, UTF_8),
            equalTo("{\"type\":\"TOOL_CALL_ARGS\",\"toolCallId\":\"call-1\",\"delta\":\""));
        assertThat(text(encoding.close), equalTo("{\"type\":\"TOOL_CALL_END\",\"toolCallId\":\"call-1\"}"));
    }

    @Test
    public void shouldEncodeToolCallWithoutParentMessage()
    {
        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .toolCall()
                .toolCallId("call-1")
                .toolCallName("get_weather")
                .build()
            .build()), false);

        assertThat(text(encoding.open),
            equalTo("{\"type\":\"TOOL_CALL_START\",\"toolCallId\":\"call-1\",\"toolCallName\":\"get_weather\"}"));
    }

    @Test
    public void shouldEncodeToolResultText()
    {
        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .toolResult()
                .messageId("msg-3")
                .toolCallId("call-1")
                .build()
            .build()), false);

        assertThat(encoding.mode, equalTo(AguiContentMode.STRING_MEMBER));
        assertThat(new String(encoding.prefix, UTF_8),
            equalTo("{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"msg-3\",\"toolCallId\":\"call-1\",\"content\":\""));
        assertThat(new String(encoding.suffix, UTF_8), equalTo("\"}"));
    }

    @Test
    public void shouldEncodeToolResultParts()
    {
        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .toolResult()
                .messageId("msg-3")
                .toolCallId("call-1")
                .content("PARTS")
                .build()
            .build()), false);

        assertThat(encoding.mode, equalTo(AguiContentMode.JSON_MEMBER));
        assertThat(new String(encoding.prefix, UTF_8),
            equalTo("{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"msg-3\",\"toolCallId\":\"call-1\",\"content\":"));
        assertThat(new String(encoding.suffix, UTF_8), equalTo("}"));
    }

    @Test
    public void shouldEncodeStateKinds()
    {
        assertThat(prefix(AguiFunctions.dataEx().typeId(1).state().type("SNAPSHOT").build().build()),
            equalTo("{\"type\":\"STATE_SNAPSHOT\",\"snapshot\":"));
        assertThat(prefix(AguiFunctions.dataEx().typeId(1).state().type("DELTA").build().build()),
            equalTo("{\"type\":\"STATE_DELTA\",\"delta\":"));
        assertThat(prefix(AguiFunctions.dataEx().typeId(1).state().type("MESSAGES_SNAPSHOT").build().build()),
            equalTo("{\"type\":\"MESSAGES_SNAPSHOT\",\"messages\":"));
    }

    @Test
    public void shouldEncodeActivity()
    {
        final AguiMessageEncoding snapshot = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .activity()
                .type("SNAPSHOT")
                .messageId("act-1")
                .activityType("PLAN")
                .build()
            .build()), false);

        assertThat(new String(snapshot.prefix, UTF_8),
            equalTo("{\"type\":\"ACTIVITY_SNAPSHOT\",\"messageId\":\"act-1\",\"activityType\":\"PLAN\",\"content\":"));
        assertThat(new String(snapshot.suffix, UTF_8), equalTo("}"));

        final AguiMessageEncoding appended = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .activity()
                .type("SNAPSHOT")
                .messageId("act-2")
                .activityType("PLAN")
                .replace(0)
                .build()
            .build()), false);

        assertThat(new String(appended.suffix, UTF_8), equalTo(",\"replace\":false}"));

        final AguiMessageEncoding delta = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .activity()
                .type("DELTA")
                .messageId("act-1")
                .activityType("PLAN")
                .replace(0)
                .build()
            .build()), false);

        assertThat(new String(delta.prefix, UTF_8),
            equalTo("{\"type\":\"ACTIVITY_DELTA\",\"messageId\":\"act-1\",\"activityType\":\"PLAN\",\"patch\":"));
        assertThat(new String(delta.suffix, UTF_8), equalTo("}"));
    }

    @Test
    public void shouldEncodeSteps()
    {
        final AguiMessageEncoding started = encoder.message(step("STARTED", "search"), false);
        final AguiMessageEncoding finished = encoder.message(step("FINISHED", "search"), false);

        assertThat(started.mode, equalTo(AguiContentMode.NONE));
        assertThat(text(started.open), equalTo("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}"));
        assertThat(text(finished.open), equalTo("{\"type\":\"STEP_FINISHED\",\"stepName\":\"search\"}"));
    }

    @Test(expected = AguiSequenceException.class)
    public void shouldRejectStepStartedTwice()
    {
        encoder.message(step("STARTED", "search"), false);
        encoder.message(step("STARTED", "search"), false);
    }

    @Test(expected = AguiSequenceException.class)
    public void shouldRejectStepFinishedWithoutStart()
    {
        encoder.message(step("FINISHED", "search"), false);
    }

    @Test(expected = AguiSequenceException.class)
    public void shouldRejectRunFinishedWithOpenStep()
    {
        encoder.message(step("STARTED", "search"), false);
        encoder.runFinished(endEx(AguiFunctions.endEx().typeId(1).build()));
    }

    @Test
    public void shouldEncodeSubagentStarted()
    {
        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .subagent()
                .type("STARTED")
                .subagentRunId("sub-1")
                .name("researcher")
                .description("Finds sources")
                .parentSubagentRunId("sub-0")
                .parentToolCallId("call-1")
                .parentMessageId("msg-2")
                .build()
            .build()), false);

        assertThat(text(encoding.open),
            equalTo("{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-1\",\"name\":\"researcher\"," +
                "\"description\":\"Finds sources\",\"parentSubagentRunId\":\"sub-0\",\"parentToolCallId\":\"call-1\"," +
                "\"parentMessageId\":\"msg-2\"}"));
    }

    @Test
    public void shouldEncodeSubagentFinishedWithResult()
    {
        encoder.message(subagent("STARTED", "sub-1", "NONE"), false);

        final AguiMessageEncoding encoding = encoder.message(subagent("FINISHED", "sub-1", "SUCCESS"), false);

        assertThat(encoding.mode, equalTo(AguiContentMode.JSON_MEMBER));
        assertThat(new String(encoding.prefix, UTF_8),
            equalTo("{\"type\":\"SUBAGENT_FINISHED\",\"subagentRunId\":\"sub-1\",\"result\":"));
        assertThat(new String(encoding.suffix, UTF_8), equalTo(",\"outcome\":{\"type\":\"success\"}}"));
    }

    @Test
    public void shouldEncodeSubagentFinishedWithoutResult()
    {
        encoder.message(subagent("STARTED", "sub-1", "NONE"), false);

        final AguiMessageEncoding encoding = encoder.message(subagent("FINISHED", "sub-1", "SUSPENDED"), true);

        assertThat(encoding.mode, equalTo(AguiContentMode.NONE));
        assertThat(text(encoding.open),
            equalTo("{\"type\":\"SUBAGENT_FINISHED\",\"subagentRunId\":\"sub-1\",\"outcome\":{\"type\":\"suspended\"}}"));
    }

    @Test
    public void shouldEncodeSubagentFinishedWithoutOutcome()
    {
        encoder.message(subagent("STARTED", "sub-1", "NONE"), false);

        final AguiMessageEncoding encoding = encoder.message(subagent("FINISHED", "sub-1", "NONE"), true);

        assertThat(text(encoding.open), equalTo("{\"type\":\"SUBAGENT_FINISHED\",\"subagentRunId\":\"sub-1\"}"));
    }

    @Test
    public void shouldEncodeSubagentError()
    {
        encoder.message(subagent("STARTED", "sub-2", "NONE"), false);

        final AguiMessageEncoding encoding = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .subagent()
                .type("ERROR")
                .subagentRunId("sub-2")
                .code("failed")
                .message("boom")
                .build()
            .build()), true);

        assertThat(text(encoding.open),
            equalTo("{\"type\":\"SUBAGENT_ERROR\",\"subagentRunId\":\"sub-2\",\"message\":\"boom\",\"code\":\"failed\"}"));
    }

    @Test(expected = AguiSequenceException.class)
    public void shouldRejectSubagentStartedTwice()
    {
        encoder.message(subagent("STARTED", "sub-1", "NONE"), false);
        encoder.message(subagent("STARTED", "sub-1", "NONE"), false);
    }

    @Test(expected = AguiSequenceException.class)
    public void shouldRejectSubagentFinishedWithoutStart()
    {
        encoder.message(subagent("FINISHED", "sub-1", "NONE"), true);
    }

    @Test(expected = AguiSequenceException.class)
    public void shouldRejectSubagentErrorWithoutStart()
    {
        encoder.message(subagent("ERROR", "sub-1", "NONE"), true);
    }

    @Test(expected = AguiSequenceException.class)
    public void shouldRejectRunFinishedWithOpenSubagent()
    {
        encoder.message(subagent("STARTED", "sub-1", "NONE"), false);
        encoder.runFinished(endEx(AguiFunctions.endEx().typeId(1).build()));
    }

    @Test
    public void shouldEncodeRawAndCustom()
    {
        final AguiMessageEncoding raw = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .raw()
                .source("provider")
                .build()
            .build()), false);

        assertThat(new String(raw.prefix, UTF_8), equalTo("{\"type\":\"RAW\",\"event\":"));
        assertThat(new String(raw.suffix, UTF_8), equalTo(",\"source\":\"provider\"}"));

        final AguiMessageEncoding rawWithoutSource = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .raw()
                .build()
            .build()), false);

        assertThat(new String(rawWithoutSource.suffix, UTF_8), equalTo("}"));

        final AguiMessageEncoding custom = encoder.message(dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .custom()
                .name("approval")
                .build()
            .build()), false);

        assertThat(new String(custom.prefix, UTF_8), equalTo("{\"type\":\"CUSTOM\",\"name\":\"approval\",\"value\":"));
        assertThat(new String(custom.suffix, UTF_8), equalTo("}"));
    }

    private AguiDataExFW reasoning(
        String messageId,
        String groupId)
    {
        final var builder = AguiFunctions.dataEx().typeId(1).reasoning().messageId(messageId);

        return dataEx((groupId != null ? builder.groupId(groupId) : builder).build().build());
    }

    private AguiDataExFW step(
        String type,
        String stepName)
    {
        return dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .step()
                .type(type)
                .stepName(stepName)
                .build()
            .build());
    }

    private AguiDataExFW subagent(
        String type,
        String subagentRunId,
        String outcome)
    {
        return dataEx(AguiFunctions.dataEx()
            .typeId(1)
            .subagent()
                .type(type)
                .subagentRunId(subagentRunId)
                .outcome(outcome)
                .build()
            .build());
    }

    private AguiDataExFW dataEx(
        byte[] bytes)
    {
        return new AguiDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private AguiEndExFW endEx(
        byte[] bytes)
    {
        return new AguiEndExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private String prefix(
        byte[] dataExBytes)
    {
        return new String(encoder.message(dataEx(dataExBytes), false).prefix, UTF_8);
    }

    private static String text(
        byte[][] events)
    {
        final StringBuilder text = new StringBuilder();

        for (byte[] event : events)
        {
            text.append(new String(event, UTF_8));
        }

        return text.toString();
    }
}
