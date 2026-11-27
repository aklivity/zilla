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

import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.agui.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEventMetaFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class AguiEventDecoderTest
{
    private static final int TYPE_ID = 1;
    private static final String STARTED = "{\"type\":\"RUN_STARTED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}";

    private final List<String> items = new ArrayList<>();
    private final AguiEventDecoder decoder = new AguiEventDecoder(new Recorder(), TYPE_ID, 4096);

    @Test
    public void shouldDecodeRunStarted()
    {
        event(STARTED);

        assertThat(items, contains("runStarted thread-1 run-1 null"));
    }

    @Test
    public void shouldDecodeRunStartedWithParentRunId()
    {
        event("{\"type\":\"RUN_STARTED\",\"threadId\":\"thread-1\",\"runId\":\"run-2\",\"parentRunId\":\"run-0\"}");

        assertThat(items, contains("runStarted thread-1 run-2 run-0"));
    }

    @Test
    public void shouldDecodeRunStartedWithTypeNotFirst()
    {
        event("{\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"type\":\"RUN_STARTED\"}");

        assertThat(items, contains("runStarted thread-1 run-1 null"));
    }

    @Test
    public void shouldDecodeRunFinished()
    {
        started();
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}");

        assertThat(items, contains("runFinished NONE -1 null"));
    }

    @Test
    public void shouldDecodeRunFinishedWithTimestamp()
    {
        started();
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"timestamp\":1730130000123}");

        assertThat(items, contains("runFinished NONE 1730130000123 null"));
    }

    @Test
    public void shouldDecodeRunFinishedWithSuccessDetail()
    {
        started();
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"result\":{\"answer\":42}," +
            "\"outcome\":{\"type\":\"success\",\"pendingToolCallIds\":[\"call-1\"]}}");

        assertThat(items, contains("runFinished SUCCESS -1 " +
            "{\"result\":{\"answer\":42},\"outcome\":{\"pendingToolCallIds\":[\"call-1\"]}}"));
    }

    @Test
    public void shouldDecodeRunFinishedWithInterruptDetail()
    {
        started();
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"," +
            "\"outcome\":{\"type\":\"interrupt\",\"interrupts\":[{\"id\":\"int-1\",\"reason\":\"confirmation\"}]}}");

        assertThat(items, contains("runFinished INTERRUPT -1 " +
            "{\"outcome\":{\"interrupts\":[{\"id\":\"int-1\",\"reason\":\"confirmation\"}]}}"));
    }

    @Test
    public void shouldDecodeRunFinishedWithCancelledOutcome()
    {
        started();
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"outcome\":{\"type\":\"cancelled\"}}");

        assertThat(items, contains("runFinished CANCELLED -1 null"));
    }

    @Test
    public void shouldDecodeRunFinishedWithDetailWithoutOutcome()
    {
        started();
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"usage\":[{\"inputTokens\":3}]}");

        assertThat(items, contains("runFinished NONE -1 {\"usage\":[{\"inputTokens\":3}]}"));
    }

    @Test
    public void shouldRejectRunFinishedWithUnknownOutcome()
    {
        started();
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"outcome\":{\"type\":\"pending\"}}");

        assertThat(items, contains("failed invalid_message Unsupported outcome type: pending"));
    }

    @Test
    public void shouldDecodeRunError()
    {
        started();
        event("{\"type\":\"RUN_ERROR\",\"message\":\"boom\",\"code\":\"agent_failure\"}");

        assertThat(items, contains("runError boom agent_failure"));
    }

    @Test
    public void shouldDecodeRunErrorWithoutCode()
    {
        started();
        event("{\"type\":\"RUN_ERROR\",\"message\":\"boom\"}");

        assertThat(items, contains("runError boom null"));
    }

    @Test
    public void shouldDecodeRunErrorBeforeRunStarted()
    {
        event("{\"type\":\"RUN_ERROR\",\"message\":\"boom\",\"code\":\"agent_failure\"}");

        assertThat(items, contains("runError boom agent_failure"));
    }

    @Test
    public void shouldDecodeTextMessage()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"Hello\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");

        assertThat(items, contains("message init|fin text[msg-2,ASSISTANT] 'Hello'"));
    }

    @Test
    public void shouldDecodeTextMessageChunks()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"Hello\"}");
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\" big\"}");
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\" world\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");

        assertThat(items, contains(
            "message init text[msg-2,ASSISTANT] 'Hello'",
            "message none - ' big'",
            "message fin - ' world'"));
    }

    @Test
    public void shouldDecodeTextMessageWithoutContent()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"user\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");

        assertThat(items, contains("message init|fin text[msg-2,USER] ''"));
    }

    @Test
    public void shouldDecodeTextMessageRoles()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"m1\",\"role\":\"developer\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"m1\"}");
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"m2\",\"role\":\"system\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"m2\"}");
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"m3\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"m3\"}");

        assertThat(items, contains(
            "message init|fin text[m1,DEVELOPER] ''",
            "message init|fin text[m2,SYSTEM] ''",
            "message init|fin text[m3,ASSISTANT] ''"));
    }

    @Test
    public void shouldDecodeTextMessageMeta()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\",\"timestamp\":1730130000123," +
            "\"subagentRunId\":\"sub-1\",\"metadata\":{\"source\":\"test\"}}");
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"Hello\",\"subagentRunId\":\"sub-1\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\",\"subagentRunId\":\"sub-1\"}");

        assertThat(items, contains(
            "message init|fin text[msg-2,ASSISTANT]{ts=1730130000123,sub=sub-1,meta={\"source\":\"test\"}} 'Hello'"));
    }

    @Test
    public void shouldDecodeTextMessageEscapes()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"a\\n\\\"b\\\"\\u00e9\\ud83d\\ude00\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");

        assertThat(items, contains("message init|fin text[msg-2,ASSISTANT] 'a\n\"b\"é😀'"));
    }

    @Test
    public void shouldDecodeTextMessageContentSplitAcrossFrames()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        frames("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"a\\u00", "e9b\\", "nc\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");

        assertThat(items, contains("message init|fin text[msg-2,ASSISTANT] 'aéb\nc'"));
    }

    @Test
    public void shouldDecodeTextMessageContentSplitInsideMultibyte()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        final byte[] bytes = "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"€\"}".getBytes(UTF_8);
        final int split = new String(bytes, UTF_8).indexOf('€') + 1;
        decode(INIT, bytes, 0, split);
        decode(FIN, bytes, split, bytes.length - split);
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");

        assertThat(items, contains("message init|fin text[msg-2,ASSISTANT] '€'"));
    }

    @Test
    public void shouldIgnoreEmptyTextDelta()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"\"}");
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");

        assertThat(items, contains("message init|fin text[msg-2,ASSISTANT] ''"));
    }

    @Test
    public void shouldDecodeReasoning()
    {
        started();
        event("{\"type\":\"REASONING_START\",\"messageId\":\"r1\"}");
        event("{\"type\":\"REASONING_MESSAGE_START\",\"messageId\":\"r1\",\"role\":\"reasoning\"}");
        event("{\"type\":\"REASONING_MESSAGE_CONTENT\",\"messageId\":\"r1\",\"delta\":\"Let me\"}");
        event("{\"type\":\"REASONING_MESSAGE_CONTENT\",\"messageId\":\"r1\",\"delta\":\" think\"}");
        event("{\"type\":\"REASONING_MESSAGE_END\",\"messageId\":\"r1\"}");
        event("{\"type\":\"REASONING_END\",\"messageId\":\"r1\"}");

        assertThat(items, contains(
            "message init reasoning[r1,null] 'Let me'",
            "message fin - ' think'"));
    }

    @Test
    public void shouldDecodeReasoningGroup()
    {
        started();
        event("{\"type\":\"REASONING_START\",\"messageId\":\"g1\"}");
        event("{\"type\":\"REASONING_MESSAGE_START\",\"messageId\":\"m1\",\"role\":\"reasoning\"}");
        event("{\"type\":\"REASONING_MESSAGE_CONTENT\",\"messageId\":\"m1\",\"delta\":\"First\"}");
        event("{\"type\":\"REASONING_MESSAGE_END\",\"messageId\":\"m1\"}");
        event("{\"type\":\"REASONING_MESSAGE_START\",\"messageId\":\"m2\",\"role\":\"reasoning\"}");
        event("{\"type\":\"REASONING_MESSAGE_CONTENT\",\"messageId\":\"m2\",\"delta\":\"Second\"}");
        event("{\"type\":\"REASONING_MESSAGE_END\",\"messageId\":\"m2\"}");
        event("{\"type\":\"REASONING_END\",\"messageId\":\"g1\"}");

        assertThat(items, contains(
            "message init|fin reasoning[m1,g1] 'First'",
            "message init|fin reasoning[m2,g1] 'Second'"));
    }

    @Test
    public void shouldDecodeReasoningEncrypted()
    {
        started();
        event("{\"type\":\"REASONING_START\",\"messageId\":\"r1\"}");
        event("{\"type\":\"REASONING_ENCRYPTED_VALUE\",\"subtype\":\"message\",\"entityId\":\"r1\"," +
            "\"encryptedValue\":\"c2VjcmV0\"}");
        event("{\"type\":\"REASONING_END\",\"messageId\":\"r1\"}");

        assertThat(items, contains(
            "message init|fin reasoning[r1,null] ''",
            "message init|fin reasoningEncrypted[MESSAGE,r1] 'c2VjcmV0'"));
    }

    @Test
    public void shouldDecodeReasoningEncryptedToolCall()
    {
        started();
        event("{\"type\":\"REASONING_START\",\"messageId\":\"r1\"}");
        event("{\"type\":\"REASONING_ENCRYPTED_VALUE\",\"subtype\":\"tool-call\",\"entityId\":\"call-1\"," +
            "\"encryptedValue\":\"c2VjcmV0\"}");
        event("{\"type\":\"REASONING_END\",\"messageId\":\"r1\"}");

        assertThat(items, contains(
            "message init|fin reasoning[r1,null] ''",
            "message init|fin reasoningEncrypted[TOOL_CALL,call-1] 'c2VjcmV0'"));
    }

    @Test
    public void shouldDecodeReasoningGroupWithoutMessage()
    {
        started();
        event("{\"type\":\"REASONING_START\",\"messageId\":\"r1\"}");
        event("{\"type\":\"REASONING_END\",\"messageId\":\"r1\"}");

        assertThat(items, contains("message init|fin reasoning[r1,null] ''"));
    }

    @Test
    public void shouldDecodeReasoningEncryptedAfterMessage()
    {
        started();
        event("{\"type\":\"REASONING_START\",\"messageId\":\"g1\"}");
        event("{\"type\":\"REASONING_MESSAGE_START\",\"messageId\":\"m1\",\"role\":\"reasoning\"}");
        event("{\"type\":\"REASONING_MESSAGE_CONTENT\",\"messageId\":\"m1\",\"delta\":\"First\"}");
        event("{\"type\":\"REASONING_MESSAGE_END\",\"messageId\":\"m1\"}");
        event("{\"type\":\"REASONING_ENCRYPTED_VALUE\",\"subtype\":\"message\",\"entityId\":\"m1\",\"encryptedValue\":\"AA\"}");
        event("{\"type\":\"REASONING_END\",\"messageId\":\"g1\"}");

        assertThat(items, contains(
            "message init|fin reasoning[m1,g1] 'First'",
            "message init|fin reasoningEncrypted[MESSAGE,m1] 'AA'"));
    }

    @Test
    public void shouldDecodeToolCall()
    {
        started();
        event("{\"type\":\"TOOL_CALL_START\",\"toolCallId\":\"call-1\",\"toolCallName\":\"get_weather\"," +
            "\"parentMessageId\":\"msg-2\"}");
        event("{\"type\":\"TOOL_CALL_ARGS\",\"toolCallId\":\"call-1\",\"delta\":\"{\\\"city\\\":\"}");
        event("{\"type\":\"TOOL_CALL_ARGS\",\"toolCallId\":\"call-1\",\"delta\":\"\\\"Paris\\\"}\"}");
        event("{\"type\":\"TOOL_CALL_END\",\"toolCallId\":\"call-1\"}");
        event("{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"msg-3\",\"toolCallId\":\"call-1\",\"content\":\"Sunny\"}");

        assertThat(items, contains(
            "message init toolCall[call-1,get_weather,msg-2] '{\"city\":'",
            "message fin - '\"Paris\"}'",
            "message init|fin toolResult[msg-3,call-1,TEXT] 'Sunny'"));
    }

    @Test
    public void shouldDecodeToolCallWithoutParentMessage()
    {
        started();
        event("{\"type\":\"TOOL_CALL_START\",\"toolCallId\":\"call-1\",\"toolCallName\":\"get_weather\"}");
        event("{\"type\":\"TOOL_CALL_ARGS\",\"toolCallId\":\"call-1\",\"delta\":\"{}\"}");
        event("{\"type\":\"TOOL_CALL_END\",\"toolCallId\":\"call-1\"}");

        assertThat(items, contains("message init|fin toolCall[call-1,get_weather,null] '{}'"));
    }

    @Test
    public void shouldDecodeToolResultParts()
    {
        started();
        event("{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"msg-3\",\"toolCallId\":\"call-1\"," +
            "\"content\":[{\"type\":\"text\",\"text\":\"Sunny\"}]}");

        assertThat(items, contains("message init|fin toolResult[msg-3,call-1,PARTS] '[{\"type\":\"text\",\"text\":\"Sunny\"}]'"));
    }

    @Test
    public void shouldDecodeToolResultPartsSplitAcrossFrames()
    {
        started();
        frames("{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"msg-3\",\"toolCallId\":\"call-1\",\"content\":" +
                "[{\"type\":\"text\",\"text\":\"Sunny\"},",
            "{\"type\":\"image\",\"source\":{\"type\":\"url\"}}]}");

        assertThat(items, contains(
            "message init toolResult[msg-3,call-1,PARTS] '[{\"type\":\"text\",\"text\":\"Sunny\"},'",
            "message fin - '{\"type\":\"image\",\"source\":{\"type\":\"url\"}}]'"));
    }

    @Test
    public void shouldDecodeToolResultTextEscapes()
    {
        started();
        event("{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"msg-3\",\"toolCallId\":\"call-1\",\"content\":\"a\\nb\"}");

        assertThat(items, contains("message init|fin toolResult[msg-3,call-1,TEXT] 'a\nb'"));
    }

    @Test
    public void shouldRejectToolResultContentThatIsNeitherTextNorParts()
    {
        started();
        event("{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"msg-3\",\"toolCallId\":\"call-1\",\"content\":{}}");

        assertThat(items, contains("failed invalid_message Unsupported tool result content"));
    }

    @Test
    public void shouldDecodeStateSnapshot()
    {
        started();
        event("{\"type\":\"STATE_SNAPSHOT\",\"snapshot\":{\"count\":1}}");
        event("{\"type\":\"STATE_DELTA\",\"delta\":[{\"op\":\"replace\",\"path\":\"/count\",\"value\":2}]}");
        event("{\"type\":\"MESSAGES_SNAPSHOT\",\"messages\":[{\"id\":\"msg-1\",\"role\":\"user\",\"content\":\"Hello\"}]}");

        assertThat(items, contains(
            "message init|fin state[SNAPSHOT] '{\"count\":1}'",
            "message init|fin state[DELTA] '[{\"op\":\"replace\",\"path\":\"/count\",\"value\":2}]'",
            "message init|fin state[MESSAGES_SNAPSHOT] '[{\"id\":\"msg-1\",\"role\":\"user\",\"content\":\"Hello\"}]'"));
    }

    @Test
    public void shouldDecodeStateSnapshotWithMetaAfterValue()
    {
        started();
        event("{\"type\":\"STATE_SNAPSHOT\",\"snapshot\":{\"count\":1},\"timestamp\":7,\"subagentRunId\":\"sub-1\"}");

        assertThat(items, contains("message init|fin state[SNAPSHOT]{ts=7,sub=sub-1} '{\"count\":1}'"));
    }

    @Test
    public void shouldDecodeStateSnapshotWithValueWhitespace()
    {
        started();
        event("{ \"type\" : \"STATE_SNAPSHOT\" , \"snapshot\" : { \"count\" : 1 } }");

        assertThat(items, contains("message init|fin state[SNAPSHOT] '{ \"count\" : 1 }'"));
    }

    @Test
    public void shouldDecodeStateSnapshotStreamedAcrossFrames()
    {
        final AguiEventDecoder small = new AguiEventDecoder(new Recorder(), TYPE_ID, 64);
        final String value = "\"" + "x".repeat(200) + "\"";

        decode(small, COMPLETE, STARTED);
        decode(small, INIT, "{\"type\":\"STATE_SNAPSHOT\",\"snapshot\":" + value.substring(0, 80));
        decode(small, 0, value.substring(80, 160));
        decode(small, FIN, value.substring(160) + "}");

        assertThat(items, contains(
            "runStarted thread-1 run-1 null",
            "message init state[SNAPSHOT] '\"" + "x".repeat(79) + "'",
            "message none - '" + "x".repeat(80) + "'",
            "message fin - '" + "x".repeat(41) + "\"'"));
    }

    @Test
    public void shouldDecodeStateSnapshotStreamedWithStringValueEscapes()
    {
        final AguiEventDecoder small = new AguiEventDecoder(new Recorder(), TYPE_ID, 64);

        decode(small, COMPLETE, STARTED);
        decode(small, INIT, "{\"type\":\"STATE_SNAPSHOT\",\"snapshot\":\"a\\");
        decode(small, FIN, "\"b\"}");

        assertThat(items, contains(
            "runStarted thread-1 run-1 null",
            "message init state[SNAPSHOT] '\"a\\'",
            "message fin - '\"b\"'"));
    }

    @Test
    public void shouldDecodeToolResultTextStreamedAcrossFrames()
    {
        final AguiEventDecoder small = new AguiEventDecoder(new Recorder(), TYPE_ID, 96);

        decode(small, COMPLETE, STARTED);
        decode(small, INIT, "{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"m\",\"toolCallId\":\"c\",\"content\":\"ab\\u00");
        decode(small, 0, "e9cd\\");
        decode(small, FIN, "nef\"}");

        assertThat(items, contains(
            "runStarted thread-1 run-1 null",
            "message init toolResult[m,c,TEXT] 'ab'",
            "message none - 'écd'",
            "message fin - '\nef'"));
    }

    @Test
    public void shouldDecodeActivity()
    {
        started();
        event("{\"type\":\"ACTIVITY_SNAPSHOT\",\"messageId\":\"act-1\",\"activityType\":\"PLAN\"," +
            "\"content\":{\"steps\":[\"a\"]}}");
        event("{\"type\":\"ACTIVITY_SNAPSHOT\",\"messageId\":\"act-2\",\"activityType\":\"PLAN\"," +
            "\"content\":{\"steps\":[\"x\"]},\"replace\":false}");
        event("{\"type\":\"ACTIVITY_DELTA\",\"messageId\":\"act-1\",\"activityType\":\"PLAN\",\"patch\":[{\"op\":\"add\"}]}");

        assertThat(items, contains(
            "message init|fin activity[SNAPSHOT,act-1,PLAN,1] '{\"steps\":[\"a\"]}'",
            "message init|fin activity[SNAPSHOT,act-2,PLAN,0] '{\"steps\":[\"x\"]}'",
            "message init|fin activity[DELTA,act-1,PLAN,1] '[{\"op\":\"add\"}]'"));
    }

    @Test
    public void shouldDecodeSteps()
    {
        started();
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}");
        event("{\"type\":\"STEP_FINISHED\",\"stepName\":\"search\"}");

        assertThat(items, contains(
            "message init|fin step[STARTED,search] ''",
            "message init|fin step[FINISHED,search] ''"));
    }

    @Test
    public void shouldDecodeSubagent()
    {
        started();
        event("{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-1\",\"name\":\"researcher\"," +
            "\"description\":\"Finds sources\",\"parentSubagentRunId\":\"sub-0\",\"parentToolCallId\":\"call-1\"," +
            "\"parentMessageId\":\"msg-1\"}");
        event("{\"type\":\"SUBAGENT_FINISHED\",\"subagentRunId\":\"sub-1\",\"result\":{\"found\":3}," +
            "\"outcome\":{\"type\":\"success\"}}");
        event("{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-2\"}");
        event("{\"type\":\"SUBAGENT_ERROR\",\"subagentRunId\":\"sub-2\",\"message\":\"boom\",\"code\":\"failed\"}");

        assertThat(items, contains(
            "message init|fin subagent[STARTED,sub-1,researcher,Finds sources,sub-0,call-1,msg-1,null,null,NONE,[]] ''",
            "message init|fin subagent[FINISHED,sub-1,null,null,null,null,null,null,null,SUCCESS,[]] '{\"found\":3}'",
            "message init|fin subagent[STARTED,sub-2,null,null,null,null,null,null,null,NONE,[]] ''",
            "message init|fin subagent[ERROR,sub-2,null,null,null,null,null,failed,boom,NONE,[]] ''"));
    }

    @Test
    public void shouldDecodeSubagentSuspended()
    {
        started();
        event("{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-1\"}");
        event("{\"type\":\"SUBAGENT_FINISHED\",\"subagentRunId\":\"sub-1\"," +
            "\"outcome\":{\"type\":\"suspended\",\"interruptIds\":[\"int-1\",\"int-2\"]}}");

        assertThat(items, contains(
            "message init|fin subagent[STARTED,sub-1,null,null,null,null,null,null,null,NONE,[]] ''",
            "message init|fin subagent[FINISHED,sub-1,null,null,null,null,null,null,null,SUSPENDED,[int-1,int-2]] ''"));
    }

    @Test
    public void shouldDecodeRawAndCustom()
    {
        started();
        event("{\"type\":\"RAW\",\"event\":{\"id\":\"evt-1\"},\"source\":\"provider\"}");
        event("{\"type\":\"CUSTOM\",\"name\":\"approval\",\"value\":{\"approved\":true}}");

        assertThat(items, contains(
            "message init|fin raw[provider] '{\"id\":\"evt-1\"}'",
            "message init|fin custom[approval] '{\"approved\":true}'"));
    }

    @Test
    public void shouldIgnoreUnknownMembers()
    {
        started();
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\",\"rawEvent\":{\"a\":[1,2,{\"b\":\"}\"}]},\"extra\":true}");

        assertThat(items, contains("message init|fin step[STARTED,search] ''"));
    }

    @Test
    public void shouldRejectEventBeforeRunStarted()
    {
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}");

        assertThat(items, contains("failed invalid_sequence Event before run started"));
    }

    @Test
    public void shouldRejectRunStartedTwice()
    {
        started();
        event(STARTED);

        assertThat(items, contains("failed invalid_sequence Run already started"));
    }

    @Test
    public void shouldRejectTextContentWithoutStart()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"Hello\"}");

        assertThat(items, contains("failed invalid_sequence Text message not started: msg-2"));
    }

    @Test
    public void shouldRejectTextContentForOtherMessage()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-3\",\"delta\":\"Hello\"}");

        assertThat(items, contains("failed invalid_sequence Text message not started: msg-3"));
    }

    @Test
    public void shouldRejectTextEndWithoutStart()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");

        assertThat(items, contains("failed invalid_sequence Text message not started: msg-2"));
    }

    @Test
    public void shouldRejectMessageStartedWhileOpen()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        event("{\"type\":\"TOOL_CALL_START\",\"toolCallId\":\"call-1\",\"toolCallName\":\"get_weather\"}");

        assertThat(items, contains("failed invalid_sequence Message already open: msg-2"));
    }

    @Test
    public void shouldRejectStateWhileMessageOpen()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        event("{\"type\":\"STATE_SNAPSHOT\",\"snapshot\":{}}");

        assertThat(items, contains("failed invalid_sequence Message already open: msg-2"));
    }

    @Test
    public void shouldRejectToolArgsWithoutStart()
    {
        started();
        event("{\"type\":\"TOOL_CALL_ARGS\",\"toolCallId\":\"call-1\",\"delta\":\"{}\"}");

        assertThat(items, contains("failed invalid_sequence Tool call not started: call-1"));
    }

    @Test
    public void shouldRejectReasoningMessageWithoutGroup()
    {
        started();
        event("{\"type\":\"REASONING_MESSAGE_START\",\"messageId\":\"r1\",\"role\":\"reasoning\"}");

        assertThat(items, contains("failed invalid_sequence Reasoning not started"));
    }

    @Test
    public void shouldRejectReasoningEndWithoutStart()
    {
        started();
        event("{\"type\":\"REASONING_END\",\"messageId\":\"r1\"}");

        assertThat(items, contains("failed invalid_sequence Reasoning not started"));
    }

    @Test
    public void shouldRejectReasoningStartedTwice()
    {
        started();
        event("{\"type\":\"REASONING_START\",\"messageId\":\"r1\"}");
        event("{\"type\":\"REASONING_START\",\"messageId\":\"r2\"}");

        assertThat(items, contains("failed invalid_sequence Reasoning already started: r1"));
    }

    @Test
    public void shouldRejectStepFinishedWithoutStart()
    {
        started();
        event("{\"type\":\"STEP_FINISHED\",\"stepName\":\"search\"}");

        assertThat(items, contains("failed invalid_sequence Step not started: search"));
    }

    @Test
    public void shouldRejectStepStartedTwice()
    {
        started();
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}");
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}");

        assertThat(items, contains(
            "message init|fin step[STARTED,search] ''",
            "failed invalid_sequence Step already started: search"));
    }

    @Test
    public void shouldRejectSubagentFinishedWithoutStart()
    {
        started();
        event("{\"type\":\"SUBAGENT_FINISHED\",\"subagentRunId\":\"sub-1\"}");

        assertThat(items, contains("failed invalid_sequence Subagent not started: sub-1"));
    }

    @Test
    public void shouldRejectSubagentStartedTwice()
    {
        started();
        event("{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-1\"}");
        event("{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-1\"}");

        assertThat(items, contains(
            "message init|fin subagent[STARTED,sub-1,null,null,null,null,null,null,null,NONE,[]] ''",
            "failed invalid_sequence Subagent already started: sub-1"));
    }

    @Test
    public void shouldRejectRunFinishedWithOpenMessage()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}");

        assertThat(items, contains("failed invalid_sequence Run finished with open message: msg-2"));
    }

    @Test
    public void shouldRejectRunFinishedWithOpenReasoning()
    {
        started();
        event("{\"type\":\"REASONING_START\",\"messageId\":\"r1\"}");
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}");

        assertThat(items, contains("failed invalid_sequence Run finished with open reasoning: r1"));
    }

    @Test
    public void shouldRejectRunFinishedWithUnfinishedStep()
    {
        started();
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}");
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}");

        assertThat(items, contains(
            "message init|fin step[STARTED,search] ''",
            "failed invalid_sequence Run finished with unfinished steps"));
    }

    @Test
    public void shouldRejectRunFinishedWithUnfinishedSubagent()
    {
        started();
        event("{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-1\"}");
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}");

        assertThat(items, contains(
            "message init|fin subagent[STARTED,sub-1,null,null,null,null,null,null,null,NONE,[]] ''",
            "failed invalid_sequence Run finished with unfinished subagents"));
    }

    @Test
    public void shouldRejectEventAfterRunFinished()
    {
        started();
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}");
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}");

        assertThat(items, contains("runFinished NONE -1 null", "failed invalid_sequence Event after run finished"));
    }

    @Test
    public void shouldRejectEventAfterRunError()
    {
        started();
        event("{\"type\":\"RUN_ERROR\",\"message\":\"boom\"}");
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}");

        assertThat(items, contains("runError boom null", "failed invalid_sequence Event after run finished"));
    }

    @Test
    public void shouldRejectUnknownEventType()
    {
        started();
        event("{\"type\":\"NOT_AN_EVENT\"}");

        assertThat(items, contains("failed invalid_message Unsupported event type: NOT_AN_EVENT"));
    }

    @Test
    public void shouldRejectMissingEventType()
    {
        started();
        event("{\"stepName\":\"search\"}");

        assertThat(items, contains("failed invalid_message Missing event type"));
    }

    @Test
    public void shouldRejectEventTypeThatIsNotString()
    {
        started();
        event("{\"type\":1}");

        assertThat(items, contains("failed invalid_message Malformed event"));
    }

    @Test
    public void shouldRejectMissingRequiredMember()
    {
        started();
        event("{\"type\":\"STEP_STARTED\"}");

        assertThat(items, contains("failed invalid_message Missing member: stepName"));
    }

    @Test
    public void shouldRejectMemberOfWrongType()
    {
        started();
        event("{\"type\":\"STEP_STARTED\",\"stepName\":1}");

        assertThat(items, contains("failed invalid_message Malformed event"));
    }

    @Test
    public void shouldRejectMalformedJson()
    {
        started();
        event("{\"type\":");

        assertThat(items, contains("failed invalid_message Malformed event"));
    }

    @Test
    public void shouldRejectEventThatIsNotObject()
    {
        started();
        event("[1,2]");

        assertThat(items, contains("failed invalid_message Malformed event"));
    }

    @Test
    public void shouldRejectTrailingContent()
    {
        started();
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"} x");

        assertThat(items, contains("failed invalid_message Malformed event"));
    }

    @Test
    public void shouldRejectInvalidEscape()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}");
        event("{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"a\\xb\"}");

        assertThat(items, contains("failed invalid_message Malformed event"));
    }

    @Test
    public void shouldRejectUnknownRole()
    {
        started();
        event("{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"robot\"}");

        assertThat(items, contains("failed invalid_message Unsupported role: robot"));
    }

    @Test
    public void shouldRejectOversizedEvent()
    {
        final AguiEventDecoder small = new AguiEventDecoder(new Recorder(), TYPE_ID, 64);

        decode(small, COMPLETE, STARTED);
        decode(small, INIT, "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"" + "x".repeat(40));
        decode(small, FIN, "x".repeat(40) + "\"}");

        assertThat(items, contains("runStarted thread-1 run-1 null", "failed invalid_message Event too large"));
    }

    @Test
    public void shouldRejectOversizedDelta()
    {
        final AguiEventDecoder small = new AguiEventDecoder(new Recorder(), TYPE_ID, 100);

        decode(small, COMPLETE, STARTED);
        decode(small, COMPLETE, "{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"m\",\"role\":\"assistant\"}");
        decode(small, COMPLETE, "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"m\",\"delta\":\"" + "x".repeat(60) + "\"}");
        decode(small, COMPLETE, "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"m\",\"delta\":\"" + "x".repeat(60) + "\"}");

        assertThat(items.get(items.size() - 1), startsWith("failed invalid_message"));
    }

    @Test
    public void shouldIgnoreInputAfterFailure()
    {
        started();
        event("{\"type\":\"NOT_AN_EVENT\"}");
        event("{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}");

        assertThat(items, contains("failed invalid_message Unsupported event type: NOT_AN_EVENT"));
    }

    @Test
    public void shouldFinishAfterRunFinished()
    {
        started();
        event("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}");
        decoder.finish();

        assertThat(items, contains("runFinished NONE -1 null"));
    }

    @Test
    public void shouldFinishAfterRunError()
    {
        started();
        event("{\"type\":\"RUN_ERROR\",\"message\":\"boom\"}");
        decoder.finish();

        assertThat(items, contains("runError boom null"));
    }

    @Test
    public void shouldRejectFinishWithoutRunFinished()
    {
        started();
        decoder.finish();

        assertThat(items, contains("failed invalid_sequence Run not finished"));
    }

    @Test
    public void shouldRejectFinishBeforeRunStarted()
    {
        decoder.finish();

        assertThat(items, contains("failed invalid_sequence Run not finished"));
    }

    @Test
    public void shouldRejectFinishInsideEvent()
    {
        started();
        decode(INIT, "{\"type\":\"TEXT_MESSAGE_START\",\"messageI");
        decoder.finish();

        assertThat(items, contains("failed invalid_message Truncated event"));
    }

    @Test
    public void shouldRejectFinishInsideStreamedEvent()
    {
        final AguiEventDecoder small = new AguiEventDecoder(new Recorder(), TYPE_ID, 64);

        decode(small, COMPLETE, STARTED);
        decode(small, INIT, "{\"type\":\"STATE_SNAPSHOT\",\"snapshot\":[1,2,");
        small.finish();

        assertThat(items, contains(
            "runStarted thread-1 run-1 null",
            "message init state[SNAPSHOT] '[1,2,'",
            "failed invalid_message Truncated event"));
    }

    @Test
    public void shouldRejectFrameWithoutInitInsideStreamedEventOfOtherKind()
    {
        started();
        decode(0, "{\"type\":\"STEP_STARTED\"");

        assertThat(items, contains("failed invalid_message Malformed event"));
    }

    @Test
    public void shouldDecodeFramesOfCompleteSplitEvent()
    {
        started();
        frames("{\"type\":\"STEP_ST", "ARTED\",\"step", "Name\":\"search\"}");

        assertThat(items, contains("message init|fin step[STARTED,search] ''"));
    }

    private void started()
    {
        decode(COMPLETE, STARTED);
        items.clear();
    }

    private void event(
        String json)
    {
        decode(COMPLETE, json);
    }

    private void frames(
        String... parts)
    {
        for (int index = 0; index < parts.length; index++)
        {
            decode((index == 0 ? INIT : 0) | (index == parts.length - 1 ? FIN : 0), parts[index]);
        }
    }

    private void decode(
        int flags,
        String json)
    {
        decode(decoder, flags, json);
    }

    private void decode(
        AguiEventDecoder target,
        int flags,
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);

        target.decode(flags, new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private void decode(
        int flags,
        byte[] bytes,
        int offset,
        int length)
    {
        decoder.decode(flags, new UnsafeBufferEx(bytes), offset, offset + length);
    }

    private final class Recorder implements AguiEventSink
    {
        @Override
        public void runStarted(
            String threadId,
            String runId,
            String parentRunId)
        {
            items.add(String.format("runStarted %s %s %s", threadId, runId, parentRunId));
        }

        @Override
        public void runFinished(
            AguiOutcomeType outcome,
            long timestamp,
            String detail)
        {
            items.add(String.format("runFinished %s %d %s", outcome, timestamp, detail));
        }

        @Override
        public void runError(
            String message,
            String code)
        {
            items.add(String.format("runError %s %s", message, code));
        }

        @Override
        public void message(
            int flags,
            AguiDataExFW dataEx,
            DirectBufferEx payload,
            int offset,
            int limit)
        {
            items.add(String.format("message %s %s '%s'", flags(flags), dataEx != null ? render(dataEx) : "-",
                payload.getStringWithoutLengthUtf8(offset, limit - offset)));
        }

        @Override
        public void failed(
            String code,
            String message)
        {
            items.add(String.format("failed %s %s", code, message));
        }
    }

    private static String flags(
        int flags)
    {
        final String text;

        if ((flags & INIT) != 0 && (flags & FIN) != 0)
        {
            text = "init|fin";
        }
        else if ((flags & INIT) != 0)
        {
            text = "init";
        }
        else if ((flags & FIN) != 0)
        {
            text = "fin";
        }
        else
        {
            text = "none";
        }

        return text;
    }

    private static String render(
        AguiDataExFW dataEx)
    {
        final String text;

        switch (dataEx.kind())
        {
        case AguiDataExFW.KIND_TEXT:
            text = String.format("text[%s,%s]%s", dataEx.text().messageId().asString(), dataEx.text().role().get(),
                meta(dataEx.text().meta()));
            break;
        case AguiDataExFW.KIND_REASONING:
            text = String.format("reasoning[%s,%s]%s", dataEx.reasoning().messageId().asString(),
                dataEx.reasoning().groupId().asString(), meta(dataEx.reasoning().meta()));
            break;
        case AguiDataExFW.KIND_REASONING_ENCRYPTED:
            text = String.format("reasoningEncrypted[%s,%s]%s", dataEx.reasoningEncrypted().subtype().get(),
                dataEx.reasoningEncrypted().entityId().asString(), meta(dataEx.reasoningEncrypted().meta()));
            break;
        case AguiDataExFW.KIND_TOOL_CALL:
            text = String.format("toolCall[%s,%s,%s]%s", dataEx.toolCall().toolCallId().asString(),
                dataEx.toolCall().toolCallName().asString(), dataEx.toolCall().parentMessageId().asString(),
                meta(dataEx.toolCall().meta()));
            break;
        case AguiDataExFW.KIND_TOOL_RESULT:
            text = String.format("toolResult[%s,%s,%s]%s", dataEx.toolResult().messageId().asString(),
                dataEx.toolResult().toolCallId().asString(), dataEx.toolResult().content().get(),
                meta(dataEx.toolResult().meta()));
            break;
        case AguiDataExFW.KIND_STATE:
            text = String.format("state[%s]%s", dataEx.state().type().get(), meta(dataEx.state().meta()));
            break;
        case AguiDataExFW.KIND_ACTIVITY:
            text = String.format("activity[%s,%s,%s,%d]%s", dataEx.activity().type().get(),
                dataEx.activity().messageId().asString(), dataEx.activity().activityType().asString(),
                dataEx.activity().replace(), meta(dataEx.activity().meta()));
            break;
        case AguiDataExFW.KIND_STEP:
            text = String.format("step[%s,%s]%s", dataEx.step().type().get(), dataEx.step().stepName().asString(),
                meta(dataEx.step().meta()));
            break;
        case AguiDataExFW.KIND_SUBAGENT:
            final List<String> interruptIds = new ArrayList<>();
            dataEx.subagent().interruptIds().forEach(id -> interruptIds.add(id.asString()));
            text = String.format("subagent[%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,[%s]]%s", dataEx.subagent().type().get(),
                dataEx.subagent().subagentRunId().asString(), dataEx.subagent().name().asString(),
                dataEx.subagent().description().asString(), dataEx.subagent().parentSubagentRunId().asString(),
                dataEx.subagent().parentToolCallId().asString(), dataEx.subagent().parentMessageId().asString(),
                dataEx.subagent().code().asString(), dataEx.subagent().message().asString(),
                dataEx.subagent().outcome().get(), String.join(",", interruptIds), meta(dataEx.subagent().meta()));
            break;
        case AguiDataExFW.KIND_RAW:
            text = String.format("raw[%s]%s", dataEx.raw().source().asString(), meta(dataEx.raw().meta()));
            break;
        default:
            text = String.format("custom[%s]%s", dataEx.custom().name().asString(), meta(dataEx.custom().meta()));
            break;
        }

        return text;
    }

    private static String meta(
        AguiEventMetaFW meta)
    {
        final List<String> members = new ArrayList<>();
        final OctetsFW metadata = meta.metadata();

        if (meta.timestamp() >= 0)
        {
            members.add("ts=" + meta.timestamp());
        }

        if (meta.subagentRunId().asString() != null)
        {
            members.add("sub=" + meta.subagentRunId().asString());
        }

        if (metadata != null && metadata.sizeof() > 0)
        {
            members.add("meta=" + metadata.get(
                (buffer, offset, limit) -> buffer.getStringWithoutLengthUtf8(offset, limit - offset)));
        }

        return members.isEmpty() ? "" : "{" + String.join(",", members) + "}";
    }
}
