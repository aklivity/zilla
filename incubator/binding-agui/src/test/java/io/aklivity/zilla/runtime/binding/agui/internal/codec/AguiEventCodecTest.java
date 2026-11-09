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
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.agui.internal.AguiFunctions;

public class AguiEventCodecTest
{
    private static final int TYPE_ID = 1;

    private static final String STARTED = "{\"type\":\"RUN_STARTED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}";
    private static final String FINISHED = "{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\"}";

    @Test
    public void shouldRoundTripTextMessage()
    {
        assertRoundTrip(
            "{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}",
            "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"Hello\"}",
            "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\" world\"}",
            "{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");
    }

    @Test
    public void shouldRoundTripTextMessageMeta()
    {
        assertRoundTrip(
            "{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"user\",\"timestamp\":1730130000123," +
                "\"subagentRunId\":\"sub-1\",\"metadata\":{\"source\":\"test\"}}",
            "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"Hello\",\"subagentRunId\":\"sub-1\"}",
            "{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\",\"subagentRunId\":\"sub-1\"}");
    }

    @Test
    public void shouldRoundTripTextMessageEscapes()
    {
        assertRoundTrip(
            "{\"type\":\"TEXT_MESSAGE_START\",\"messageId\":\"msg-2\",\"role\":\"assistant\"}",
            "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"msg-2\",\"delta\":\"a\\n\\\"b\\\"\\\\c\\t\\u0001é😀\"}",
            "{\"type\":\"TEXT_MESSAGE_END\",\"messageId\":\"msg-2\"}");
    }

    @Test
    public void shouldRoundTripReasoning()
    {
        assertRoundTrip(
            "{\"type\":\"REASONING_START\",\"messageId\":\"g1\"}",
            "{\"type\":\"REASONING_MESSAGE_START\",\"messageId\":\"m1\",\"role\":\"reasoning\"}",
            "{\"type\":\"REASONING_MESSAGE_CONTENT\",\"messageId\":\"m1\",\"delta\":\"First\"}",
            "{\"type\":\"REASONING_MESSAGE_END\",\"messageId\":\"m1\"}",
            "{\"type\":\"REASONING_MESSAGE_START\",\"messageId\":\"m2\",\"role\":\"reasoning\"}",
            "{\"type\":\"REASONING_MESSAGE_CONTENT\",\"messageId\":\"m2\",\"delta\":\"Second\"}",
            "{\"type\":\"REASONING_MESSAGE_END\",\"messageId\":\"m2\"}",
            "{\"type\":\"REASONING_END\",\"messageId\":\"g1\"}");
    }

    @Test
    public void shouldRoundTripReasoningEncrypted()
    {
        assertRoundTrip(
            "{\"type\":\"REASONING_START\",\"messageId\":\"r1\"}",
            "{\"type\":\"REASONING_ENCRYPTED_VALUE\",\"subtype\":\"message\",\"entityId\":\"r1\"," +
                "\"encryptedValue\":\"c2VjcmV0\"}",
            "{\"type\":\"REASONING_END\",\"messageId\":\"r1\"}");
    }

    @Test
    public void shouldRoundTripToolCall()
    {
        assertRoundTrip(
            "{\"type\":\"TOOL_CALL_START\",\"toolCallId\":\"call-1\",\"toolCallName\":\"get_weather\"," +
                "\"parentMessageId\":\"msg-2\"}",
            "{\"type\":\"TOOL_CALL_ARGS\",\"toolCallId\":\"call-1\",\"delta\":\"{\\\"city\\\":\"}",
            "{\"type\":\"TOOL_CALL_ARGS\",\"toolCallId\":\"call-1\",\"delta\":\"\\\"Paris\\\"}\"}",
            "{\"type\":\"TOOL_CALL_END\",\"toolCallId\":\"call-1\"}",
            "{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"msg-3\",\"toolCallId\":\"call-1\",\"content\":\"Sunny\"}");
    }

    @Test
    public void shouldRoundTripToolResultParts()
    {
        assertRoundTrip(
            "{\"type\":\"TOOL_CALL_RESULT\",\"messageId\":\"msg-3\",\"toolCallId\":\"call-1\"," +
                "\"content\":[{\"type\":\"text\",\"text\":\"Sunny\"}]}");
    }

    @Test
    public void shouldRoundTripState()
    {
        assertRoundTrip(
            "{\"type\":\"STATE_SNAPSHOT\",\"snapshot\":{\"count\":1},\"timestamp\":7}",
            "{\"type\":\"STATE_DELTA\",\"delta\":[{\"op\":\"replace\",\"path\":\"/count\",\"value\":2}]}",
            "{\"type\":\"MESSAGES_SNAPSHOT\",\"messages\":[{\"id\":\"msg-1\",\"role\":\"user\",\"content\":\"Hello\"}]}");
    }

    @Test
    public void shouldRoundTripActivity()
    {
        assertRoundTrip(
            "{\"type\":\"ACTIVITY_SNAPSHOT\",\"messageId\":\"act-1\",\"activityType\":\"PLAN\",\"content\":{\"steps\":[\"a\"]}}",
            "{\"type\":\"ACTIVITY_SNAPSHOT\",\"messageId\":\"act-2\",\"activityType\":\"PLAN\",\"content\":{\"steps\":[\"x\"]}," +
                "\"replace\":false}",
            "{\"type\":\"ACTIVITY_DELTA\",\"messageId\":\"act-1\",\"activityType\":\"PLAN\",\"patch\":[{\"op\":\"add\"}]}");
    }

    @Test
    public void shouldRoundTripStep()
    {
        assertRoundTrip(
            "{\"type\":\"STEP_STARTED\",\"stepName\":\"search\"}",
            "{\"type\":\"STEP_FINISHED\",\"stepName\":\"search\"}");
    }

    @Test
    public void shouldRoundTripSubagent()
    {
        assertRoundTrip(
            "{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-1\",\"name\":\"researcher\"," +
                "\"description\":\"Finds sources\",\"parentSubagentRunId\":\"sub-0\",\"parentToolCallId\":\"call-1\"," +
                "\"parentMessageId\":\"msg-1\"}",
            "{\"type\":\"SUBAGENT_FINISHED\",\"subagentRunId\":\"sub-1\",\"result\":{\"found\":3}," +
                "\"outcome\":{\"type\":\"success\"}}",
            "{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-2\"}",
            "{\"type\":\"SUBAGENT_FINISHED\",\"subagentRunId\":\"sub-2\"," +
                "\"outcome\":{\"type\":\"suspended\",\"interruptIds\":[\"int-1\",\"int-2\"]}}",
            "{\"type\":\"SUBAGENT_STARTED\",\"subagentRunId\":\"sub-3\"}",
            "{\"type\":\"SUBAGENT_ERROR\",\"subagentRunId\":\"sub-3\",\"message\":\"boom\",\"code\":\"failed\"}");
    }

    @Test
    public void shouldRoundTripRawAndCustom()
    {
        assertRoundTrip(
            "{\"type\":\"RAW\",\"event\":{\"id\":\"evt-1\"},\"source\":\"provider\"}",
            "{\"type\":\"CUSTOM\",\"name\":\"approval\",\"value\":{\"approved\":true}}");
    }

    @Test
    public void shouldRoundTripRunFinishedDetail()
    {
        final Recorder recorder = new Recorder();
        final AguiEventDecoder decoder = new AguiEventDecoder(recorder, TYPE_ID, 4096);

        decode(decoder, STARTED);
        decode(decoder, "{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"result\":{\"answer\":42}," +
            "\"outcome\":{\"type\":\"success\",\"pendingToolCallIds\":[\"call-1\"]}}");

        final AguiEventEncoder encoder = new AguiEventEncoder("thread-1", "run-1", null);
        final byte[] endEx = AguiFunctions.endEx()
            .typeId(TYPE_ID)
            .outcome(recorder.outcome.name())
            .detail(recorder.detail)
            .build();

        assertThat(text(encoder.runFinished(new AguiEndExFW()
            .wrap(new UnsafeBufferEx(endEx), 0, endEx.length))),
            equalTo("{\"type\":\"RUN_FINISHED\",\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"result\":{\"answer\":42}," +
                "\"outcome\":{\"type\":\"success\",\"pendingToolCallIds\":[\"call-1\"]}}"));
    }

    @Test
    public void shouldRoundTripFramesIntoEvents()
    {
        assertFramesRoundTrip(
            frame(INIT | FIN, AguiFunctions.dataEx().typeId(TYPE_ID).text().messageId("msg-2").role("ASSISTANT").build().build(),
                "Hello"),
            frame(INIT, AguiFunctions.dataEx().typeId(TYPE_ID).text().messageId("msg-3").role("USER").build().build(), "a"),
            frame(0, null, "b"),
            frame(FIN, null, "c"),
            frame(INIT | FIN, AguiFunctions.dataEx().typeId(TYPE_ID).toolCall().toolCallId("call-1").toolCallName("tool").build()
                .build(), "{}"),
            frame(INIT | FIN, AguiFunctions.dataEx().typeId(TYPE_ID).state().type("SNAPSHOT").build().build(), "{\"a\":1}"),
            frame(INIT | FIN, AguiFunctions.dataEx().typeId(TYPE_ID).state().type("DELTA").build().build(),
                "[{\"op\":\"add\"}]"),
            frame(INIT | FIN, AguiFunctions.dataEx().typeId(TYPE_ID).step().type("STARTED").stepName("s").build().build(), ""),
            frame(INIT | FIN, AguiFunctions.dataEx().typeId(TYPE_ID).step().type("FINISHED").stepName("s").build().build(), ""));
    }

    private void assertRoundTrip(
        String... events)
    {
        final List<String> expected = new ArrayList<>();
        expected.add(STARTED);
        expected.addAll(List.of(events));
        expected.add(FINISHED);

        final Recorder recorder = new Recorder();
        final AguiEventDecoder decoder = new AguiEventDecoder(recorder, TYPE_ID, 4096);

        expected.forEach(event -> decode(decoder, event));
        decoder.finish();

        assertThat(recorder.failure, equalTo(null));
        assertThat(encode(recorder.frames), equalTo(expected));
    }

    private void assertFramesRoundTrip(
        Frame... frames)
    {
        final List<Frame> expected = List.of(frames);
        final List<String> events = encode(expected);

        final Recorder recorder = new Recorder();
        final AguiEventDecoder decoder = new AguiEventDecoder(recorder, TYPE_ID, 4096);

        events.forEach(event -> decode(decoder, event));
        decoder.finish();

        assertThat(recorder.failure, equalTo(null));
        assertThat(describe(recorder.frames), equalTo(describe(expected)));
    }

    private static List<String> describe(
        List<Frame> frames)
    {
        final List<String> described = new ArrayList<>();

        for (Frame frame : frames)
        {
            described.add(String.format("%d %s %s", frame.flags,
                frame.dataEx != null ? Base64.getEncoder().encodeToString(frame.dataEx) : "-",
                new String(frame.payload, UTF_8)));
        }

        return described;
    }

    private static Frame frame(
        int flags,
        byte[] dataEx,
        String payload)
    {
        return new Frame(flags, dataEx, payload.getBytes(UTF_8));
    }

    private static List<String> encode(
        List<Frame> frames)
    {
        final AguiEventEncoder encoder = new AguiEventEncoder("thread-1", "run-1", null);
        final List<String> events = new ArrayList<>();
        final StringBuilder member = new StringBuilder();

        events.add(text(encoder.runStarted()));

        AguiMessageEncoding encoding = null;
        boolean content = false;

        for (Frame frame : frames)
        {
            final boolean init = (frame.flags & INIT) != 0;
            final boolean fin = (frame.flags & FIN) != 0;

            if (init)
            {
                final AguiDataExFW dataEx = new AguiDataExFW().wrap(new UnsafeBufferEx(frame.dataEx), 0, frame.dataEx.length);

                encoding = encoder.message(dataEx, fin && frame.payload.length == 0);
                events.addAll(texts(encoding.open));
                content = false;
                member.setLength(0);
            }

            final String payload = new String(frame.payload, UTF_8);

            switch (encoding.mode)
            {
            case DELTA_EVENTS:
                if (!payload.isEmpty())
                {
                    if (!content)
                    {
                        events.addAll(texts(encoding.contentOpen));
                        content = true;
                    }

                    events.add(new String(encoding.prefix, UTF_8) + AguiJsonEscape.escape(payload) +
                        new String(encoding.suffix, UTF_8));
                }
                break;
            case STRING_MEMBER:
                member.append(payload);
                if (fin)
                {
                    events.add(new String(encoding.prefix, UTF_8) + AguiJsonEscape.escape(member.toString()) +
                        new String(encoding.suffix, UTF_8));
                }
                break;
            case JSON_MEMBER:
                member.append(payload);
                if (fin)
                {
                    events.add(new String(encoding.prefix, UTF_8) + member + new String(encoding.suffix, UTF_8));
                }
                break;
            default:
                break;
            }

            if (fin)
            {
                if (encoding.mode == AguiContentMode.DELTA_EVENTS && content)
                {
                    events.addAll(texts(encoding.contentClose));
                }

                events.addAll(texts(encoding.close));
            }
        }

        events.addAll(texts(encoder.runFinished(null)));

        return events;
    }

    private static List<String> texts(
        byte[][] events)
    {
        final List<String> texts = new ArrayList<>();

        for (byte[] event : events)
        {
            texts.add(new String(event, UTF_8));
        }

        return texts;
    }

    private static String text(
        byte[][] events)
    {
        return String.join("", texts(events));
    }

    private static void decode(
        AguiEventDecoder decoder,
        String event)
    {
        final byte[] bytes = event.getBytes(UTF_8);

        decoder.decode(COMPLETE, new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private record Frame(
        int flags,
        byte[] dataEx,
        byte[] payload)
    {
    }

    private static final class Recorder implements AguiEventSink
    {
        private final List<Frame> frames = new ArrayList<>();

        private AguiOutcomeType outcome;
        private String detail;
        private String failure;

        @Override
        public void runStarted(
            String threadId,
            String runId,
            String parentRunId)
        {
        }

        @Override
        public void runFinished(
            AguiOutcomeType outcome,
            long timestamp,
            String detail)
        {
            this.outcome = outcome;
            this.detail = detail;
        }

        @Override
        public void runError(
            String message,
            String code)
        {
        }

        @Override
        public void message(
            int flags,
            AguiDataExFW dataEx,
            DirectBufferEx payload,
            int offset,
            int limit)
        {
            final byte[] ex = dataEx != null ? new byte[dataEx.sizeof()] : null;

            if (dataEx != null)
            {
                dataEx.buffer().getBytes(dataEx.offset(), ex);
            }

            final byte[] bytes = new byte[limit - offset];
            payload.getBytes(offset, bytes);

            frames.add(new Frame(flags, ex, bytes));
        }

        @Override
        public void failed(
            String code,
            String message)
        {
            failure = code + " " + message;
        }
    }
}
