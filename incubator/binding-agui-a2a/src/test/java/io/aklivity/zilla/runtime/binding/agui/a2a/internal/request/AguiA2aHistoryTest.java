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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.card.AguiA2aExtension;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRequestDecoder.Status;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.internal.buffer.DefaultBufferPool;

public class AguiA2aHistoryTest
{
    private static final int[] CHUNKS = {1, 7, 16, 37, 64, 1000, 1 << 20};
    private static final int SLOT = 4096;
    private static final String HEAD = "{\"threadId\":\"t\",\"runId\":\"r\",\"messages\":[";
    private static final String U1 = "{\"id\":\"u1\",\"role\":\"user\",\"content\":\"one\"}";
    private static final String A1 = "{\"id\":\"a1\",\"role\":\"assistant\",\"content\":\"two\"}";
    private static final String U2 = "{\"id\":\"u2\",\"role\":\"user\",\"content\":\"three\"}";
    private static final String A2 = "{\"id\":\"a2\",\"role\":\"assistant\",\"content\":\"four\"}";
    private static final String U3 = "{\"id\":\"u3\",\"role\":\"user\",\"content\":\"five\"}";
    private static final String SYSTEM = "{\"id\":\"s1\",\"role\":\"system\",\"content\":\"rules\"}";
    private static final String TOOL = "{\"id\":\"t1\",\"role\":\"tool\",\"toolCallId\":\"h-1\",\"content\":\"done\"}";
    private static final String TAIL = ",\"configuration\":{\"historyLength\":0,\"acceptedOutputModes\":[\"text/plain\"]}}";

    @Test
    public void shouldArchiveEarlierMessages()
    {
        for (int chunk : CHUNKS)
        {
            final Fixture fixture = fixture(2);
            fixture.feed(request(U1, A1, U2, A2, U3), chunk);

            assertThat(fixture.decoder.status(), equalTo(Status.COMPLETE));
            assertThat(messages(fixture.decoder.archive()), contains(U1, A1, U2, A2));
            assertThat(fixture.decoder.archiveOmitted(), nullValue());
        }
    }

    @Test
    public void shouldDropSystemMessagesFromHistory()
    {
        for (int chunk : CHUNKS)
        {
            final Fixture fixture = fixture(2);
            fixture.feed(request(U1, SYSTEM, A1, U2), chunk);

            assertThat(messages(fixture.decoder.archive()), contains(U1, A1));
        }
    }

    @Test
    public void shouldArchiveToolMessageBeforeTrailingUser()
    {
        for (int chunk : CHUNKS)
        {
            final Fixture fixture = fixture(2);
            fixture.feed(request(U1, A1, TOOL, U2), chunk);

            assertThat(messages(fixture.decoder.archive()), contains(U1, A1, TOOL));
        }
    }

    @Test
    public void shouldNotTakeSecondSlotWithoutEarlierTurns()
    {
        for (int chunk : CHUNKS)
        {
            final Fixture fixture = fixture(2);
            fixture.feed(request(U1), chunk);

            assertThat(messages(fixture.decoder.archive()), is(empty()));
            assertThat(fixture.archiveRetention.acquired(), is(false));
        }
    }

    @Test
    public void shouldReleaseArchiveSlot()
    {
        final Fixture fixture = fixture(2);
        fixture.feed(request(U1, A1, U2), 16);

        assertThat(fixture.archiveRetention.acquired(), is(true));

        fixture.decoder.releaseArchive();

        assertThat(fixture.archiveRetention.acquired(), is(false));
        assertThat(fixture.decoder.archive(), nullValue());
    }

    @Test
    public void shouldMapHistoryPartFirst()
    {
        final Fixture fixture = fixture(2);
        fixture.feed(request(U1, A1, U2), 1000);

        final String history = "{\"data\":{\"messages\":[" + U1 + "," + A1 + "]},\"mediaType\":\"application/json\"," +
            "\"metadata\":{\"agui\":{\"part\":\"history\"}}}";
        final String expected = "{\"message\":{\"messageId\":\"u2\",\"role\":\"ROLE_USER\",\"parts\":[" + history +
            ",{\"text\":\"three\"}]}" + TAIL;

        assertThat(fixture.assemble(true), equalTo(expected));
    }

    @Test
    public void shouldKeepTier1ResultWithLargeHistoryAndLargeTrailingMessage()
    {
        final String large = "u".repeat(SLOT / 2);
        final String history = "{\"id\":\"h\",\"role\":\"assistant\",\"content\":\"" + "h".repeat(SLOT) + "\"}";
        final String trailing = "{\"id\":\"last\",\"role\":\"user\",\"content\":\"" + large + "\"}";

        for (int chunk : CHUNKS)
        {
            final Fixture with = fixture(2);
            with.feed(request(U1, history, trailing), chunk);

            final Fixture without = fixture(2);
            without.feed(request(trailing), chunk);

            assertThat(with.decoder.status(), equalTo(without.decoder.status()));
            assertThat(with.assemble(false), equalTo(without.assemble(false)));
            assertThat(with.decoder.archive(), nullValue());
            assertThat(with.decoder.archiveOmitted(), equalTo("too_large"));
        }
    }

    @Test
    public void shouldOmitHistoryWhenSecondSlotUnavailable()
    {
        for (int chunk : CHUNKS)
        {
            final Fixture with = fixture(1);
            with.feed(request(U1, A1, U2), chunk);

            final Fixture without = fixture(1);
            without.feed(request(U2), chunk);

            assertThat(with.decoder.status(), equalTo(Status.COMPLETE));
            assertThat(with.assemble(false), equalTo(without.assemble(false)));
            assertThat(with.decoder.archive(), nullValue());
            assertThat(with.decoder.archiveOmitted(), equalTo("unavailable"));
        }
    }

    private static String request(
        String... messages)
    {
        return HEAD + String.join(",", messages) + "]}";
    }

    private static List<String> messages(
        AguiA2aRetained archive)
    {
        final List<String> messages = new ArrayList<>();

        for (int index = 0; archive != null && index < archive.count(); index++)
        {
            if (archive.kind(index) == AguiA2aRetained.Kind.MESSAGE)
            {
                messages.add(archive.buffer().getStringWithoutLengthUtf8(archive.offset(index), archive.length(index)));
            }
        }

        return messages;
    }

    private static Fixture fixture(
        int slots)
    {
        return new Fixture(slots);
    }

    private static final class Fixture
    {
        private final AguiA2aRetention retention;
        private final AguiA2aRetention archiveRetention;
        private final AguiA2aRequestDecoder decoder;

        private Fixture(
            int slots)
        {
            final DefaultBufferPool pool = new DefaultBufferPool(SLOT, SLOT);
            final DefaultBufferPool archivePool = slots == 1 ? pool : new DefaultBufferPool(SLOT, SLOT);
            this.retention = new AguiA2aRetention(pool);
            this.archiveRetention = new AguiA2aRetention(archivePool);
            this.decoder = new AguiA2aRequestDecoder(1L, "t", "r", null, retention, archiveRetention);
        }

        private void feed(
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

        private String assemble(
            boolean withHistory)
        {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final AguiA2aMessageAssembler assembler = new AguiA2aMessageAssembler()
                .wrap(decoder.retained(), "r", null, null, null, true)
                .extension(new AguiA2aExtension(AguiA2aExtension.URI_V1, false, Set.of("history")));

            (withHistory || decoder.archive() == null ? assembler.history(decoder.archive()) : assembler)
                .emit((buffer, offset, length) ->
                {
                    final byte[] bytes = new byte[length];
                    buffer.getBytes(offset, bytes);
                    out.write(bytes, 0, length);
                });

            return out.toString(UTF_8);
        }
    }
}
