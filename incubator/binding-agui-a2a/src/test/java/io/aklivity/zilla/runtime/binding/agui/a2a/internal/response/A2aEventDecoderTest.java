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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.response;

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

public class A2aEventDecoderTest
{
    private static final String FRAGMENT = "abc \\\"q\\\" é 😀 \\u00e9 \\ud83d\\ude00 \\\\ \\n";
    private static final String DECODED = "abc \"q\" é 😀 é 😀 \\ \n";

    @Test
    public void shouldDecodeMembersInAnyOrder()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);

        decode(decoder, "{\"artifactUpdate\":{\"artifact\":{\"artifactId\":\"a-1\",\"parts\":[{\"text\":\"hi\"}]}," +
            "\"append\":true,\"lastChunk\":true,\"taskId\":\"t-1\",\"contextId\":\"c-1\"}}", 4096);

        assertEquals(List.of(
            "started ARTIFACT_UPDATE t-1 c-1 UNSPECIFIED true",
            "artifact a-1",
            "text 3 hi",
            "artifactEnded",
            "ended true"), recorder.log);
    }

    @Test
    public void shouldTakeHeadFromDataEx()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);
        final A2aExSpec spec = new A2aExSpec(A2aDataExFW.KIND_ARTIFACT_UPDATE, "t-7", "c-7", null, null, "a-7",
            Boolean.TRUE, Boolean.TRUE);

        decode(decoder, "{\"artifactUpdate\":{\"taskId\":\"t-9\",\"contextId\":\"c-9\",\"artifact\":{\"artifactId\":\"a-9\"," +
            "\"parts\":[{\"text\":\"hi\"}]},\"append\":false,\"lastChunk\":false}}", spec, 17);

        assertEquals("started ARTIFACT_UPDATE t-7 c-7 UNSPECIFIED true", recorder.log.get(0));
        assertEquals("artifact a-7", recorder.log.get(1));
        assertEquals("ended true", recorder.log.get(recorder.log.size() - 1));
    }

    @Test
    public void shouldTreatUnspecifiedFlagsAsFalse()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);
        final A2aExSpec spec = new A2aExSpec(A2aDataExFW.KIND_ARTIFACT_UPDATE, "t-1", "c-1", null, null, "a-1", null, null);

        decode(decoder, "{\"artifactUpdate\":{\"artifact\":{\"artifactId\":\"a-1\",\"parts\":[]},\"append\":true," +
            "\"lastChunk\":true}}", spec, 4096);

        assertEquals("started ARTIFACT_UPDATE t-1 c-1 UNSPECIFIED false", recorder.log.get(0));
        assertEquals("ended false", recorder.log.get(recorder.log.size() - 1));
    }

    @Test
    public void shouldPassNoIdentifierWhenDataExHasNone()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);
        final A2aExSpec spec = new A2aExSpec(A2aDataExFW.KIND_MESSAGE, null, null, null, null, null, null, null);

        decode(decoder, "{\"message\":{\"messageId\":\"m-1\",\"parts\":[]}}", spec, 4096);

        assertEquals("message REPLY null", recorder.log.get(1));
    }

    @Test
    public void shouldDecodeBareTask()
    {
        for (int window : new int[] {4096, 7, 1})
        {
            final Recorder recorder = new Recorder();
            final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024, A2aEventRoot.TASK);

            decode(decoder, "{\"id\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_CANCELED\"}," +
                "\"artifacts\":[]}", window);

            assertEquals("window " + window, List.of(
                "started TASK t-1 c-1 CANCELED false",
                "ended false"), recorder.log);
        }
    }

    @Test
    public void shouldDecodeStatusAndMessage()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);

        decode(decoder, "{\"statusUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\"," +
            "\"status\":{\"state\":\"TASK_STATE_WORKING\"," +
            "\"message\":{\"messageId\":\"m-1\",\"role\":\"ROLE_AGENT\",\"parts\":[{\"text\":\"a\"},{\"text\":\"b\"}]}}}}", 5);

        assertEquals("started STATUS_UPDATE t-1 c-1 WORKING false", recorder.log.get(0));
        assertEquals("message STATUS m-1", recorder.log.get(1));
        assertEquals("messageEnded STATUS", recorder.log.get(recorder.log.size() - 2));
        assertEquals("ended false", recorder.log.get(recorder.log.size() - 1));
        assertEquals("ab", recorder.text());
    }

    @Test
    public void shouldPreserveDataValueVerbatim()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);

        decode(decoder, "{\"message\":{\"messageId\":\"m-1\",\"parts\":[{\"data\":{\"a\": [1, 2.50, \"x}\"]}}]}}", 4096);

        assertTrue(recorder.log.toString(), recorder.log.contains("data 3 {\"a\": [1, 2.50, \"x}\"]}"));
    }

    @Test
    public void shouldDecodeFilePart()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);

        decode(decoder, "{\"message\":{\"messageId\":\"m-1\",\"parts\":[{\"url\":\"https://x/y.png\"," +
            "\"mediaType\":\"image/png\"," +
            "\"filename\":\"y.png\"}]}}", 4096);

        assertTrue(recorder.log.toString(), recorder.log.contains("file image/png y.png https://x/y.png null"));
    }

    @Test
    public void shouldStreamTextLargerThanSlotAcrossWindows() throws Exception
    {
        final StringBuilder source = new StringBuilder();
        final StringBuilder expected = new StringBuilder();

        while (source.length() < 100_000)
        {
            source.append(FRAGMENT);
            expected.append(DECODED);
        }

        final String json = "{\"artifactUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\"," +
            "\"artifact\":{\"artifactId\":\"a-1\"," +
            "\"parts\":[{\"text\":\"" + source + "\"}]},\"append\":false,\"lastChunk\":true}}";

        for (int window : new int[] {1, 3, 700, 1024, 4000, Integer.MAX_VALUE})
        {
            final ResponseHarness harness = new ResponseHarness(1024);

            harness.event(json, window);

            final StringBuilder actual = new StringBuilder();

            for (ResponseHarness.Frame frame : harness.frames)
            {
                assertTrue("window " + window, frame.payload().length <= 1024);
                actual.append(utf8(frame.payload()));
            }

            assertEquals("window " + window, expected.toString(), actual.toString());
            assertEquals("window " + window, INIT, harness.frames.get(0).flags());
            assertEquals("window " + window, "text(a-1,ASSISTANT)", harness.frames.get(0).ex());
            assertEquals("window " + window, FIN, harness.frames.get(harness.frames.size() - 1).flags());
            assertEquals("window " + window, 0, harness.frames.get(harness.frames.size() - 1).payload().length);

            for (int index = 1; index < harness.frames.size() - 1; index++)
            {
                assertEquals("window " + window, 0, harness.frames.get(index).flags());
            }
        }
    }

    @Test
    public void shouldDecodeEventLargerThanSlotWithMembersAfterText()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 64);

        decode(decoder, "{\"artifactUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\",\"artifact\":{\"artifactId\":\"a-1\"," +
            "\"parts\":[{\"text\":\"" + "x".repeat(500) + "\"}]},\"append\":true,\"lastChunk\":true}}", 40);

        assertEquals("started ARTIFACT_UPDATE t-1 c-1 UNSPECIFIED true", recorder.log.get(0));
        assertEquals("x".repeat(500), recorder.text());
        assertEquals("ended true", recorder.log.get(recorder.log.size() - 1));
    }

    @Test
    public void shouldStreamDataValueLargerThanSlot()
    {
        final ResponseHarness harness = new ResponseHarness(64);
        final String data = "{\"k\":\"" + "d".repeat(500) + "\"}";

        harness.event("{\"message\":{\"messageId\":\"m-1\",\"parts\":[{\"data\":" + data + "}]}}", 23);

        final StringBuilder actual = new StringBuilder();

        for (ResponseHarness.Frame frame : harness.frames)
        {
            actual.append(frame.payloadText());
        }

        assertEquals(data, actual.toString());
        assertEquals(INIT, harness.frames.get(0).flags());
        assertEquals("custom(agent.data)", harness.frames.get(0).ex());
        assertEquals(FIN, harness.frames.get(harness.frames.size() - 1).flags() & FIN);
    }

    @Test
    public void shouldDecodeIdentifierAfterParts()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);

        decode(decoder, "{\"artifactUpdate\":{\"artifact\":{\"parts\":[{\"text\":\"hi\"}],\"artifactId\":\"a-9\"}}}", 11);

        assertEquals("artifact a-9", recorder.log.get(1));
    }

    @Test
    public void shouldFailWhenEventMalformed()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);

        decode(decoder, "{\"message\" 1}", 4096);

        assertEquals(List.of("failed invalid_message Malformed event"), recorder.log);
    }

    @Test
    public void shouldFailWhenEventTruncated()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);

        decode(decoder, "{\"message\":{\"messageId\":\"m-1\"}", 4096);

        assertEquals("failed invalid_message Truncated event", recorder.log.get(recorder.log.size() - 1));
    }

    @Test
    public void shouldFailWhenFrameDoesNotStartEvent()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);

        decoder.decode(FIN, null, new io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx(new byte[1]), 0, 1);

        assertEquals(List.of("failed invalid_message Malformed event"), recorder.log);
    }

    @Test
    public void shouldFailWhenStreamEndsMidEvent()
    {
        final Recorder recorder = new Recorder();
        final A2aEventDecoder decoder = new A2aEventDecoder(recorder, 1024);
        final byte[] bytes = "{\"message\":{".getBytes(UTF_8);

        decoder.decode(INIT, null, new io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx(bytes), 0, bytes.length);
        decoder.finish();

        assertEquals("failed invalid_message Truncated event", recorder.log.get(recorder.log.size() - 1));
    }

    private static void decode(
        A2aEventDecoder decoder,
        String json,
        int window)
    {
        decode(decoder, json, A2aExSpec.of(json), window);
    }

    private static void decode(
        A2aEventDecoder decoder,
        String json,
        A2aExSpec spec,
        int window)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final DirectBufferEx buffer = new io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx(bytes);
        final A2aDataExFW dataEx = spec.build();

        int progress = 0;

        do
        {
            final int limit = Math.min(progress + window, bytes.length);

            decoder.decode((progress == 0 ? INIT : 0) | (limit == bytes.length ? FIN : 0), progress == 0 ? dataEx : null,
                buffer, progress, limit);
            progress = limit;
        }
        while (progress < bytes.length);
    }

    private static String utf8(
        byte[] bytes) throws CharacterCodingException
    {
        return UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
    }

    private static final class Recorder implements A2aEventSink
    {
        private final List<String> log = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();

        String text()
        {
            return text.toString();
        }

        @Override
        public void eventStarted(
            A2aEventKind kind,
            String taskId,
            String contextId,
            A2aTaskState state,
            Boolean append)
        {
            log.add(String.format("started %s %s %s %s %s", kind, taskId, contextId, state, append));
        }

        @Override
        public void messageStarted(
            A2aMessageScope scope,
            String messageId)
        {
            log.add(String.format("message %s %s", scope, messageId));
        }

        @Override
        public void artifactStarted(
            String artifactId)
        {
            log.add("artifact " + artifactId);
        }

        @Override
        public void text(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final byte[] bytes = new byte[limit - offset];
            buffer.getBytes(offset, bytes);
            text.append(new String(bytes, UTF_8));

            if (text.length() < 100)
            {
                log.add(String.format("text %d %s", flags, new String(bytes, UTF_8)));
            }
        }

        @Override
        public void data(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final byte[] bytes = new byte[limit - offset];
            buffer.getBytes(offset, bytes);
            log.add("data " + flags + " " + new String(bytes, UTF_8));
        }

        @Override
        public void file(
            String mediaType,
            String name,
            String url,
            String raw)
        {
            log.add(String.format("file %s %s %s %s", mediaType, name, url, raw));
        }

        @Override
        public void tagStarted(
            A2aPartTag tag)
        {
            log.add("tag " + tag.event);
        }

        @Override
        public void tagEnded()
        {
            log.add("tagEnded");
        }

        @Override
        public void tagDropped(
            String event)
        {
            log.add("tagDropped " + event);
        }

        @Override
        public void tagMalformed(
            String event,
            String detail)
        {
            log.add("tagMalformed " + event);
        }

        @Override
        public void artifactEnded()
        {
            log.add("artifactEnded");
        }

        @Override
        public void messageEnded(
            A2aMessageScope scope)
        {
            log.add("messageEnded " + scope);
        }

        @Override
        public void eventEnded(
            boolean lastChunk)
        {
            log.add("ended " + lastChunk);
        }

        @Override
        public void failed(
            String code,
            String message)
        {
            log.add(String.format("failed %s %s", code, message));
        }
    }
}
