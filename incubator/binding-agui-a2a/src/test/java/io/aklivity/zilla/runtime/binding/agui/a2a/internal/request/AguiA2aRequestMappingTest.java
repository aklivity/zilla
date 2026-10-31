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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.request;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aMessageAssembler.ResumeStatus;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRequestDecoder.Status;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.internal.buffer.DefaultBufferPool;

public class AguiA2aRequestMappingTest
{
    private static final String SCRIPTS = "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/";
    private static final int[] CHUNKS = {1, 7, 16, 37, 64, 1000, 1 << 20};
    private static final int SLOT = 4096;
    private static final String TRAILER = ",\"configuration\":{\"historyLength\":0,\"acceptedOutputModes\":[\"text/plain\"]}}";

    @Test
    public void shouldMapForwardedProps() throws Exception
    {
        assertScenario("request.forwarded.props", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldMapForwardedPropsValue() throws Exception
    {
        assertScenario("request.forwarded.props.value", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldMapBinaryPart() throws Exception
    {
        assertScenario("request.part.binary", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldDropFilePart() throws Exception
    {
        assertScenario("request.part.file.dropped", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldMapUrlPart() throws Exception
    {
        assertScenario("request.part.url", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldMapStateAndContext() throws Exception
    {
        assertScenario("request.state.context", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldMapStateAndContextWildcard() throws Exception
    {
        assertScenario("request.state.context.wildcard", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldOmitStateAndContextWhenCardRejectsJson() throws Exception
    {
        assertScenario("request.state.context.omitted", 0, 0, null, null, null, false);
    }

    @Test
    public void shouldDropSystemAndDeveloper() throws Exception
    {
        assertScenario("request.system.developer.dropped", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldMergeTrailingUserMessages() throws Exception
    {
        assertScenario("request.trailing.user", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldMapInterruptInputRequired() throws Exception
    {
        assertScenario("interrupt.input.required", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldMapInterruptAuthRequired() throws Exception
    {
        assertScenario("interrupt.auth.required", 0, 0, null, null, null, true);
    }

    @Test
    public void shouldMapResumePayload() throws Exception
    {
        assertScenario("interrupt.resume", 1, 1, null, "t-1", "h-1", true);
    }

    @Test
    public void shouldMapResumeText() throws Exception
    {
        assertScenario("interrupt.resume.text", 1, 1, null, "t-1", "h-1", true);
    }

    @Test
    public void shouldMapResumeEmpty() throws Exception
    {
        assertScenario("interrupt.resume.empty", 1, 1, null, "t-1", "h-1", true);
    }

    @Test
    public void shouldMapRememberedContext() throws Exception
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"state\":{},\"messages\":[" +
            "{\"id\":\"msg-1\",\"role\":\"user\",\"content\":\"Hello\"},{\"id\":\"msg-2\",\"role\":\"assistant\"," +
            "\"content\":\"Hi\"},{\"id\":\"msg-3\",\"role\":\"user\",\"content\":\"Hello again\"}]," +
            "\"tools\":[],\"context\":[],\"forwardedProps\":{}}";
        final String expected = "{\"message\":{\"messageId\":\"msg-3\",\"role\":\"ROLE_USER\",\"contextId\":\"c-1\"," +
            "\"parts\":[{\"text\":\"Hello again\"}]}" + TRAILER;

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk, "t", "r", null);
            assertThat(decoder.status(), equalTo(Status.COMPLETE));
            assertThat(decoder.priorTurn(), is(true));
            assertThat(assemble(decoder, "r", "c-1", null, null, true), equalTo(expected));
            decoder.retained().release();
        }
    }

    @Test
    public void shouldEscapeIdentifiers() throws Exception
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}";
        final AguiA2aRequestDecoder decoder = decode(input, 1000, "t", "r", null);

        assertThat(assemble(decoder, "r\"1", "c\\1", null, null, true),
            equalTo("{\"message\":{\"messageId\":\"r\\\"1\",\"role\":\"ROLE_USER\",\"contextId\":\"c\\\\1\"," +
                "\"parts\":[{\"text\":\"x\"}]}" + TRAILER));
    }

    @Test
    public void shouldListResumeEntries() throws Exception
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[],\"resume\":[" +
            "{\"interruptId\":\"h-1\",\"status\":\"cancelled\"},{\"interruptId\":\"h-2\",\"status\":\"resolved\"}]}";
        final AguiA2aRequestDecoder decoder = decode(input, 16, "t", "r", null);
        final AguiA2aMessageAssembler assembler = new AguiA2aMessageAssembler();

        assertThat(decoder.status(), equalTo(Status.COMPLETE));
        assertThat(assembler.resumeCount(decoder.retained()), equalTo(2));
        assertThat(assembler.resumeInterruptId(decoder.retained(), 0), equalTo("h-1"));
        assertThat(assembler.resumeInterruptId(decoder.retained(), 1), equalTo("h-2"));
        assertThat(assembler.resumeStatus(decoder.retained(), 0), equalTo(ResumeStatus.CANCELLED));
        assertThat(assembler.resumeStatus(decoder.retained(), 1), equalTo(ResumeStatus.RESOLVED));
    }

    @Test
    public void shouldRejectEmptyResumeWithoutUserMessage() throws Exception
    {
        assertRejected("{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[],\"resume\":[]}",
            AguiA2aRejection.NO_USER_MESSAGE);
    }

    @Test
    public void shouldOmitEmptyAndNullMembers() throws Exception
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"state\":null,\"context\":[ ]," +
            "\"forwardedProps\":{ },\"messages\":[{\"id\":\"m\",\"role\":\"user\",\"content\":\"x\"}]}";

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk, "t", "r", null);
            assertThat(decoder.status(), equalTo(Status.COMPLETE));
            assertThat(assemble(decoder, "r", null, null, null, true),
                equalTo("{\"message\":{\"messageId\":\"m\",\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"x\"}]}" +
                    TRAILER));
            decoder.retained().release();
        }
    }

    @Test
    public void shouldRejectReservedKey() throws Exception
    {
        assertRejected("{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]," +
            "\"forwardedProps\":{\"a\":1,\"agui\":{}}}", AguiA2aRejection.RESERVED_KEY);
    }

    @Test
    public void shouldRejectNoUserMessage() throws Exception
    {
        assertRejected("{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"assistant\",\"content\":\"x\"}]}",
            AguiA2aRejection.NO_USER_MESSAGE);
    }

    @Test
    public void shouldRejectUserMessageBeforeToolMessage() throws Exception
    {
        assertRejected("{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}," +
            "{\"role\":\"tool\",\"content\":\"y\"}]}", AguiA2aRejection.NO_USER_MESSAGE);
    }

    @Test
    public void shouldRejectIdsMismatch() throws Exception
    {
        assertRejected("{\"threadId\":\"other\",\"runId\":\"r\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}",
            AguiA2aRejection.INVALID_RUN);
        assertRejected("{\"threadId\":\"t\",\"runId\":\"other\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}",
            AguiA2aRejection.INVALID_RUN);
        assertRejected("{\"threadId\":\"t\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}",
            AguiA2aRejection.INVALID_RUN);
    }

    @Test
    public void shouldRejectMalformed() throws Exception
    {
        assertRejected("{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}",
            AguiA2aRejection.MALFORMED);
        assertRejected("[1,2]", AguiA2aRejection.MALFORMED);
        assertRejected("{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":{}}", AguiA2aRejection.MALFORMED);
    }

    @Test
    public void shouldRejectOversizeState() throws Exception
    {
        assertRejected("{\"threadId\":\"t\",\"runId\":\"r\",\"state\":{\"blob\":\"" + "x".repeat(2 * SLOT) +
            "\"},\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}", AguiA2aRejection.TOO_LARGE);
    }

    @Test
    public void shouldRejectOversizeTrailingMessage() throws Exception
    {
        assertRejected("{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"assistant\",\"content\":\"a\"}," +
            "{\"role\":\"user\",\"content\":\"" + "x".repeat(2 * SLOT) + "\"}]}", AguiA2aRejection.TOO_LARGE);
    }

    @Test
    public void shouldAcceptOversizeMessageFollowedByAssistant() throws Exception
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"user\",\"content\":\"" +
            "x".repeat(2 * SLOT) + "\"},{\"role\":\"assistant\",\"content\":\"a\"}," +
            "{\"id\":\"last\",\"role\":\"user\",\"content\":\"y\"}]}";

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk, "t", "r", null);
            assertThat(decoder.status(), equalTo(Status.COMPLETE));
            decoder.retained().release();
        }
    }

    @Test
    public void shouldAcceptLongHistory() throws Exception
    {
        final StringBuilder input = new StringBuilder("{\"threadId\":\"t\",\"runId\":\"r\",\"tools\":[{\"name\":\"" +
            "y".repeat(3 * SLOT) + "\"}],\"messages\":[");
        input.append("{\"id\":\"a\",\"content\":\"").append("x".repeat(3 * SLOT)).append("\",\"role\":\"user\"},");
        for (int index = 0; index < 20; index++)
        {
            input.append("{\"id\":\"h").append(index).append("\",\"role\":\"system\",\"content\":\"")
                .append("s".repeat(SLOT / 2)).append("\"},");
            input.append("{\"id\":\"u").append(index).append("\",\"role\":\"user\",\"content\":\"")
                .append("u".repeat(SLOT / 2)).append("\"},");
            input.append("{\"id\":\"b").append(index).append("\",\"role\":\"assistant\",\"content\":\"")
                .append("b".repeat(SLOT)).append("\"},");
        }
        input.append("{\"id\":\"last\",\"role\":\"user\",\"content\":\"Hello\"}]}");

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input.toString(), chunk, "t", "r", null);
            assertThat(decoder.status(), equalTo(Status.COMPLETE));
            assertThat(assemble(decoder, "r", null, null, null, true),
                equalTo("{\"message\":{\"messageId\":\"last\",\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"Hello\"}]}" +
                    TRAILER));
            decoder.retained().release();
        }
    }

    @Test
    public void shouldRejectRetentionUnavailable() throws Exception
    {
        final DefaultBufferPool pool = new DefaultBufferPool(SLOT, SLOT);
        final int held = pool.acquire(99L);
        assertThat(pool.acquiredSlots(), equalTo(1));

        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}";
        final AguiA2aRequestDecoder decoder = new AguiA2aRequestDecoder(1L, "t", "r", null, new AguiA2aRetention(pool));
        feed(decoder, input, 1000);

        assertThat(decoder.status(), equalTo(Status.REJECTED));
        assertThat(decoder.rejection(), equalTo(AguiA2aRejection.RETENTION_UNAVAILABLE));
        assertThat(decoder.rejection().status(), equalTo(503));
        assertThat(decoder.rejection().code(), equalTo("retention_unavailable"));
        assertThat(pool.acquiredSlots(), equalTo(1));

        pool.release(held);
        assertThat(pool.acquiredSlots(), equalTo(0));
    }

    @Test
    public void shouldReleaseRetentionOnEveryErrorPath() throws Exception
    {
        final String[] inputs =
        {
            "{\"threadId\":\"t\",\"runId\":\"r\",\"state\":{\"a\":1},\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]," +
                "\"forwardedProps\":{\"agui\":1}}",
            "{\"threadId\":\"t\",\"runId\":\"r\",\"state\":{\"a\":1},\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]",
            "{\"threadId\":\"t\",\"runId\":\"r\",\"state\":{\"a\":1},\"messages\":[{\"role\":\"assistant\"}]}",
            "{\"threadId\":\"t\",\"runId\":\"x\",\"state\":{\"a\":1},\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}",
            "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"user\",\"content\":\"" + "x".repeat(2 * SLOT) +
                "\"}]}"
        };

        for (String input : inputs)
        {
            final DefaultBufferPool pool = new DefaultBufferPool(SLOT, SLOT);
            final AguiA2aRequestDecoder decoder = new AguiA2aRequestDecoder(1L, "t", "r", null, new AguiA2aRetention(pool));
            feed(decoder, input, 16);

            assertThat(input, decoder.status(), equalTo(Status.REJECTED));
            assertThat(input, pool.acquiredSlots(), equalTo(0));
        }
    }

    @Test
    public void shouldIgnoreAfterRejection() throws Exception
    {
        final DefaultBufferPool pool = new DefaultBufferPool(SLOT, SLOT);
        final AguiA2aRequestDecoder decoder = new AguiA2aRequestDecoder(1L, "t", "r", null, new AguiA2aRetention(pool));
        final byte[] bytes = "{\"threadId\":\"x\",\"runId\":\"r\"}".getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);

        assertThat(decoder.decode(buffer, 0, bytes.length, false), equalTo(bytes.length));
        assertThat(decoder.rejection(), equalTo(AguiA2aRejection.INVALID_RUN));
        assertThat(decoder.decode(buffer, 0, bytes.length, true), equalTo(bytes.length));
        assertThat(decoder.rejection(), equalTo(AguiA2aRejection.INVALID_RUN));
    }

    @Test
    public void shouldReportContentLengthOfEmittedBody() throws Exception
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"state\":{\"s\":1},\"context\":[{\"a\":\"b\"}]," +
            "\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"h\\u00e9llo\"}]}]," +
            "\"forwardedProps\":{\"k\":\"v\"}}";
        final AguiA2aRequestDecoder decoder = decode(input, 16, "t", "r", null);
        final AguiA2aMessageAssembler assembler = new AguiA2aMessageAssembler()
            .wrap(decoder.retained(), "r", "c", null, null, true);

        final String body = assemble(decoder, "r", "c", null, null, true);

        assertThat(assembler.contentLength(), equalTo(body.getBytes(UTF_8).length));
    }

    @Test
    public void shouldEmitBodyInWindows() throws Exception
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"state\":{\"s\":1},\"context\":[{\"a\":\"b\"}]," +
            "\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"h\\u00e9llo\"}]}]," +
            "\"forwardedProps\":{\"k\":\"v\"}}";
        final AguiA2aRequestDecoder decoder = decode(input, 16, "t", "r", null);
        final String body = assemble(decoder, "r", "c\\1", null, null, true);

        for (int window : new int[] {1, 3, 16, 100, 1 << 20})
        {
            final AguiA2aMessageAssembler assembler = new AguiA2aMessageAssembler()
                .wrap(decoder.retained(), "r", "c\\1", null, null, true);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();

            assertThat(assembler.contentLength(), equalTo(body.getBytes(UTF_8).length));

            while (!assembler.emitted())
            {
                assembler.emit((buffer, offset, length) ->
                {
                    final byte[] bytes = new byte[length];
                    buffer.getBytes(offset, bytes);
                    out.write(bytes, 0, length);
                }, window);
            }

            assertThat("window " + window, out.toString(UTF_8), equalTo(body));
        }
    }

    @Test
    public void shouldReportUnacceptedMediaType() throws Exception
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[{\"role\":\"user\",\"content\":[" +
            "{\"type\":\"text\",\"text\":\"d\"},{\"type\":\"image\",\"source\":{\"type\":\"data\"," +
            "\"value\":\"aGVsbG8=\",\"mimeType\":\"image/png\"}}]}]}";
        final AguiA2aRequestDecoder decoder = decode(input, 16, "t", "r", null);
        final AguiA2aMessageAssembler assembler = new AguiA2aMessageAssembler()
            .wrap(decoder.retained(), "r", null, null, null, true)
            .accepting("text/plain"::equals);

        assembler.contentLength();

        assertThat(assembler.unacceptedMediaType(), equalTo("image/png"));
        assertThat(assembler.accepting("image/png"::equals).wrap(decoder.retained(), "r", null, null, null, true)
            .contentLength() > 0, equalTo(true));
        assertThat(assembler.unacceptedMediaType(), equalTo(null));
    }

    private static void assertScenario(
        String name,
        int inputIndex,
        int bodyIndex,
        String contextId,
        String taskId,
        String interruptId,
        boolean acceptsJson) throws IOException
    {
        final String client = script(SCRIPTS + "agui/" + name + "/client.rpt");
        final String server = script(SCRIPTS + "a2a/" + name + "/server.rpt");

        final String input = matches(client, "^write \"(.*)\"$").get(inputIndex)
            .replace("\\\"", "\"").replace("\\\\", "\\");
        final String expected = matches(server, "^read '(\\{\"message\".*)'$").get(bodyIndex);
        final String length = matches(server, "\\.contentLength\\((\\d+)\\)").get(bodyIndex);
        final String runId = interruptId != null ? "run-2" : "run-1";
        final String parentRunId = interruptId != null ? "run-1" : null;

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk, "thread-1", runId, parentRunId);
            assertThat(name + " chunk " + chunk, decoder.status(), equalTo(Status.COMPLETE));

            final AguiA2aMessageAssembler assembler = new AguiA2aMessageAssembler()
                .wrap(decoder.retained(), runId, contextId, taskId, interruptId, acceptsJson);

            assertThat(name + " chunk " + chunk, assemble(decoder, runId, contextId, taskId, interruptId, acceptsJson),
                equalTo(expected));
            assertThat(name + " length", String.valueOf(assembler.contentLength()), equalTo(length));
            decoder.retained().release();
        }
    }

    private static void assertRejected(
        String input,
        AguiA2aRejection rejection)
    {
        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk, "t", "r", null);
            assertThat(input + " chunk " + chunk, decoder.status(), equalTo(Status.REJECTED));
            assertThat(input + " chunk " + chunk, decoder.rejection(), equalTo(rejection));
        }
    }

    private static String assemble(
        AguiA2aRequestDecoder decoder,
        String runId,
        String contextId,
        String taskId,
        String interruptId,
        boolean acceptsJson)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        new AguiA2aMessageAssembler()
            .wrap(decoder.retained(), runId, contextId, taskId, interruptId, acceptsJson)
            .emit((buffer, offset, length) ->
            {
                final byte[] bytes = new byte[length];
                buffer.getBytes(offset, bytes);
                out.write(bytes, 0, length);
            });
        return out.toString(UTF_8);
    }

    private static AguiA2aRequestDecoder decode(
        String input,
        int chunk,
        String threadId,
        String runId,
        String parentRunId)
    {
        final DefaultBufferPool pool = new DefaultBufferPool(SLOT, SLOT);
        final AguiA2aRequestDecoder decoder =
            new AguiA2aRequestDecoder(1L, threadId, runId, parentRunId, new AguiA2aRetention(pool));
        feed(decoder, input, chunk);
        return decoder;
    }

    private static void feed(
        AguiA2aRequestDecoder decoder,
        String input,
        int chunk)
    {
        final byte[] bytes = input.getBytes(UTF_8);
        byte[] window = new byte[0];
        int carried = 0;
        int index = 0;

        while (index < bytes.length)
        {
            final int length = Math.min(chunk, bytes.length - index);
            final boolean last = index + length == bytes.length;
            if (window.length < carried + length)
            {
                final byte[] resized = new byte[carried + length];
                System.arraycopy(window, 0, resized, 0, carried);
                window = resized;
            }
            System.arraycopy(bytes, index, window, carried, length);
            index += length;

            final int limit = carried + length;
            final int progress = decoder.decode(new UnsafeBufferEx(window), 0, limit, last);
            carried = limit - progress;
            System.arraycopy(window, progress, window, 0, carried);
        }
    }

    private static List<String> matches(
        String text,
        String regex)
    {
        final List<String> found = new ArrayList<>();
        final Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        while (matcher.find())
        {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String script(
        String resource) throws IOException
    {
        try (InputStream in = AguiA2aRequestMappingTest.class.getClassLoader().getResourceAsStream(resource))
        {
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
