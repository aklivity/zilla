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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aMessageAssembler.ResumeStatus;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRequestDecoder.Status;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Kind;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.internal.buffer.DefaultBufferPool;

public class AguiA2aRequestDecoderTest
{
    private static final int[] CHUNKS = {1, 7, 16, 37, 64, 1000, 1 << 20};
    private static final int SLOT = 4096;
    private static final String TRAILER = ",\"configuration\":{\"historyLength\":0,\"acceptedOutputModes\":[\"text/plain\"]}}";

    @Test
    public void shouldRecordPartsAsDecoded()
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[" +
            "{\"id\":\"m-1\",\"role\":\"user\",\"content\":\"one\"}," +
            "{\"role\":\"user\",\"id\":\"m-2\",\"content\":[" +
            "{\"type\":\"text\",\"text\":\"two\",\"metadata\":{\"k\":1}}," +
            "{\"source\":{\"mimeType\":\"image/png\",\"type\":\"data\",\"value\":\"AAAA\"},\"type\":\"image\"}," +
            "{\"type\":\"file\",\"text\":\"dropped\"}]}]}";

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk);
            final AguiA2aRetained retained = decoder.retained();

            assertThat("chunk " + chunk, decoder.status(), equalTo(Status.COMPLETE));
            assertThat("chunk " + chunk, kinds(retained), contains(
                Kind.PART_TEXT, Kind.MESSAGE_ID, Kind.MESSAGE,
                Kind.PART_TEXT, Kind.PART_METADATA, Kind.PART_RAW, Kind.PART_MIME, Kind.MESSAGE_ID, Kind.MESSAGE));
            assertThat("chunk " + chunk, text(retained, 0), equalTo("\"one\""));
            assertThat("chunk " + chunk, text(retained, 1), equalTo("\"m-1\""));
            assertThat("chunk " + chunk, text(retained, 3), equalTo("\"two\""));
            assertThat("chunk " + chunk, text(retained, 4), equalTo("{\"k\":1}"));
            assertThat("chunk " + chunk, text(retained, 5), equalTo("\"AAAA\""));
            assertThat("chunk " + chunk, text(retained, 6), equalTo("\"image/png\""));
            assertThat("chunk " + chunk, text(retained, 7), equalTo("\"m-2\""));
            decoder.retained().release();
        }
    }

    @Test
    public void shouldDiscardPartsOfMessageWithNonUserRole()
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[" +
            "{\"role\":\"user\",\"content\":\"keep\"}," +
            "{\"content\":[{\"type\":\"text\",\"text\":\"drop\"}],\"role\":\"system\"}," +
            "{\"content\":\"drop\",\"role\":\"developer\"}]}";

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk);
            final AguiA2aRetained retained = decoder.retained();

            assertThat("chunk " + chunk, decoder.status(), equalTo(Status.COMPLETE));
            assertThat("chunk " + chunk, kinds(retained), contains(Kind.PART_TEXT, Kind.MESSAGE));
            assertThat("chunk " + chunk, text(retained, 0), equalTo("\"keep\""));
            decoder.retained().release();
        }
    }

    @Test
    public void shouldDiscardLargeContentWhenRoleFollows()
    {
        final String large = "x".repeat(SLOT);
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[" +
            "{\"role\":\"user\",\"content\":\"keep\"}," +
            "{\"content\":\"" + large + "\",\"role\":\"system\"}]}";

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk);
            final AguiA2aRetained retained = decoder.retained();

            assertThat("chunk " + chunk, decoder.status(), equalTo(Status.COMPLETE));
            assertThat("chunk " + chunk, kinds(retained), contains(Kind.PART_TEXT, Kind.MESSAGE));
            decoder.retained().release();
        }
    }

    @Test
    public void shouldRejectLargeUserContentWhenRoleFollows()
    {
        final String large = "x".repeat(SLOT);
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[" +
            "{\"content\":\"" + large + "\",\"role\":\"user\"}]}";

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk);

            assertThat("chunk " + chunk, decoder.status(), equalTo(Status.REJECTED));
            assertThat("chunk " + chunk, decoder.rejection(), equalTo(AguiA2aRejection.TOO_LARGE));
        }
    }

    @Test
    public void shouldRecordResumeEntries()
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[],\"resume\":[" +
            "{\"interruptId\":\"h-1\",\"status\":\"cancelled\"}," +
            "{\"payload\":{\"a\":[1,2]},\"status\":\"resolved\",\"interruptId\":\"h-2\",\"metadata\":{\"m\":true}}," +
            "{\"interruptId\":\"h-3\",\"payload\":\"text\"}]}";

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk);
            final AguiA2aRetained retained = decoder.retained();

            assertThat("chunk " + chunk, decoder.status(), equalTo(Status.COMPLETE));
            assertThat("chunk " + chunk, retained.resumeCount(), equalTo(3));
            assertThat("chunk " + chunk, retained.resumeInterruptId(0), equalTo("h-1"));
            assertThat("chunk " + chunk, retained.resumeInterruptId(1), equalTo("h-2"));
            assertThat("chunk " + chunk, retained.resumeInterruptId(2), equalTo("h-3"));
            assertThat("chunk " + chunk, retained.resumeStatus(0), equalTo(ResumeStatus.CANCELLED));
            assertThat("chunk " + chunk, retained.resumeStatus(1), equalTo(ResumeStatus.RESOLVED));
            assertThat("chunk " + chunk, retained.resumeStatus(2), equalTo(ResumeStatus.RESOLVED));
            assertThat("chunk " + chunk, assemble(decoder, "r", "t-1", "h-2"),
                equalTo("{\"message\":{\"messageId\":\"r\",\"role\":\"ROLE_USER\",\"taskId\":\"t-1\"," +
                    "\"parts\":[{\"data\":{\"a\":[1,2]}}],\"metadata\":{\"agui\":{\"resume\":{\"m\":true}}}}" + TRAILER));
            assertThat("chunk " + chunk, assemble(decoder, "r", "t-1", "h-3"),
                equalTo("{\"message\":{\"messageId\":\"r\",\"role\":\"ROLE_USER\",\"taskId\":\"t-1\"," +
                    "\"parts\":[{\"text\":\"text\"}]}" + TRAILER));
            decoder.retained().release();
        }
    }

    @Test
    public void shouldReleaseResumeEntries()
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[],\"resume\":[" +
            "{\"interruptId\":\"h-1\",\"status\":\"cancelled\"}]}";
        final AguiA2aRequestDecoder decoder = decode(input, 1000);

        decoder.retained().release();

        assertThat(decoder.retained().resumeCount(), equalTo(0));
    }

    @Test
    public void shouldAssembleFromDescriptorsAlone()
    {
        final String input = "{\"threadId\":\"t\",\"runId\":\"r\",\"state\":{\"s\":1},\"forwardedProps\":{\"p\":2}," +
            "\"messages\":[{\"id\":\"m-1\",\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"a b\"}," +
            "{\"type\":\"image\",\"source\":{\"type\":\"url\",\"value\":\"http://x\",\"mimeType\":\"image/png\"}}]}," +
            "{\"role\":\"assistant\",\"content\":\"noise\"}," +
            "{\"role\":\"user\",\"id\":\"m-3\",\"content\":\"last\"}]}";

        for (int chunk : CHUNKS)
        {
            final AguiA2aRequestDecoder decoder = decode(input, chunk);
            final AguiA2aRetained retained = decoder.retained();
            final String expected = assemble(decoder, "r", null, null);

            final boolean[] described = new boolean[retained.mark()];
            for (int index = 0; index < retained.count(); index++)
            {
                if (retained.kind(index) != Kind.MESSAGE)
                {
                    for (int at = retained.offset(index); at < retained.offset(index) + retained.length(index); at++)
                    {
                        described[at] = true;
                    }
                }
            }

            final MutableDirectBufferEx slot = (MutableDirectBufferEx) retained.buffer();
            for (int at = 0; at < described.length; at++)
            {
                if (!described[at])
                {
                    slot.putByte(at, (byte) 0);
                }
            }

            assertThat("chunk " + chunk, assemble(decoder, "r", null, null), equalTo(expected));
            assertThat("chunk " + chunk, expected.contains("\"messageId\":\"m-3\""), is(true));
            decoder.retained().release();
        }
    }

    private static List<Kind> kinds(
        AguiA2aRetained retained)
    {
        final List<Kind> kinds = new ArrayList<>();
        for (int index = 0; index < retained.count(); index++)
        {
            kinds.add(retained.kind(index));
        }
        return kinds;
    }

    private static String text(
        AguiA2aRetained retained,
        int index)
    {
        return retained.buffer().getStringWithoutLengthUtf8(retained.offset(index), retained.length(index));
    }

    private static String assemble(
        AguiA2aRequestDecoder decoder,
        String runId,
        String taskId,
        String interruptId)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        new AguiA2aMessageAssembler()
            .wrap(decoder.retained(), runId, null, taskId, interruptId, true)
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
        int chunk)
    {
        final DefaultBufferPool pool = new DefaultBufferPool(SLOT, SLOT);
        final AguiA2aRequestDecoder decoder = new AguiA2aRequestDecoder(1L, "t", "r", null, new AguiA2aRetention(pool));
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

        return decoder;
    }
}
