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
package io.aklivity.zilla.runtime.binding.llm.internal.decode;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

public class LlmSseContentDecoderTest
{
    private final LlmSseContentDecoder decoder = new LlmSseContentDecoder();
    private final StringBuilder data = new StringBuilder();
    private final List<Flushed> flushes = new ArrayList<>();

    private final LlmContentDecoderOutput output = new LlmContentDecoderOutput()
    {
        @Override
        public void data(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            data.append(buffer.getStringWithoutLengthUtf8(offset, length));
        }

        @Override
        public void flush(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            flushes.add(new Flushed(event, buffer.getStringWithoutLengthUtf8(offset, length), data.toString()));
            data.setLength(0);
        }
    };

    @Before
    public void reset()
    {
        data.setLength(0);
        flushes.clear();
    }

    @Test
    public void shouldDecodeSingleLineDataEvent()
    {
        int progress = decode("data: hello\n\n");

        assertThat(progress, equalTo(byteLength("data: hello\n\n")));
        assertThat(flushes.size(), equalTo(1));
        assertThat(flushes.get(0).event, nullValue());
        assertThat(flushes.get(0).data, equalTo("hello"));
        assertThat(flushes.get(0).id, equalTo(""));
    }

    @Test
    public void shouldDecodeEventWithExplicitEventName()
    {
        decode("event: message\ndata: {\"x\":1}\n\n");

        assertThat(flushes.get(0).event, equalTo("message"));
        assertThat(flushes.get(0).data, equalTo("{\"x\":1}"));
    }

    @Test
    public void shouldJoinMultipleDataLinesWithNewline()
    {
        decode("data: line1\ndata: line2\n\n");

        assertThat(flushes.get(0).data, equalTo("line1\nline2"));
    }

    @Test
    public void shouldIgnoreCommentLines()
    {
        decode(": this is a comment\ndata: hello\n\n");

        assertThat(flushes.get(0).data, equalTo("hello"));
    }

    @Test
    public void shouldNotDispatchWhenNoDataFieldSeen()
    {
        decode("event: ping\n\n");

        assertThat(flushes.size(), equalTo(0));
    }

    @Test
    public void shouldResetEventNameAfterBoundaryEvenWithoutDispatch()
    {
        decode("event: ping\n\ndata: hi\n\n");

        assertThat(flushes.size(), equalTo(1));
        assertThat(flushes.get(0).event, nullValue());
        assertThat(flushes.get(0).data, equalTo("hi"));
    }

    @Test
    public void shouldTreatCrlfAsLineTerminator()
    {
        decode("data: hello\r\n\r\n");

        assertThat(flushes.get(0).data, equalTo("hello"));
    }

    @Test
    public void shouldTreatBareCrAsLineTerminator()
    {
        // trailing "X" makes the final CR unambiguous (not a possible CRLF pair awaiting more bytes)
        decode("data: hello\r\rX");

        assertThat(flushes.get(0).data, equalTo("hello"));
    }

    @Test
    public void shouldNotConsumeIncompleteTrailingLine()
    {
        int progress = decode("data: hel");

        assertThat(progress, equalTo(0));
        assertThat(flushes.size(), equalTo(0));
    }

    @Test
    public void shouldNotConsumeAmbiguousTrailingCr()
    {
        byte[] bytes = "data: a\n\ndata: hello\r".getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);

        int progress = decoder.decode(buffer, 0, bytes.length, output);

        int firstEventLength = byteLength("data: a\n\n");
        assertThat(progress, equalTo(firstEventLength));
        assertThat(flushes.size(), equalTo(1));
        assertThat(flushes.get(0).data, equalTo("a"));
    }

    @Test
    public void shouldStripSingleLeadingSpaceAfterColon()
    {
        decode("data:no-space\n\n");
        assertThat(flushes.get(0).data, equalTo("no-space"));

        reset();

        decode("data:  two-spaces\n\n");
        assertThat(flushes.get(0).data, equalTo(" two-spaces"));
    }

    @Test
    public void shouldTreatFieldWithoutColonAsEmptyValue()
    {
        decode("data\n\n");

        assertThat(flushes.get(0).data, equalTo(""));
    }

    @Test
    public void shouldPersistLastEventIdAcrossDispatchesUntilOverwritten()
    {
        decode("id: 1\ndata: a\n\ndata: b\n\n");

        assertThat(flushes.get(0).id, equalTo("1"));
        assertThat(flushes.get(1).id, equalTo("1"));
    }

    @Test
    public void shouldGrowStoredEventIdBufferForLargerValues()
    {
        decode("id: 1\ndata: a\n\nid: 1234567890\ndata: b\n\n");

        assertThat(flushes.get(0).id, equalTo("1"));
        assertThat(flushes.get(1).id, equalTo("1234567890"));
    }

    @Test
    public void shouldReuseStoredEventIdBufferForSmallerValues()
    {
        decode("id: 1234567890\ndata: a\n\nid: 1\ndata: b\n\n");

        assertThat(flushes.get(0).id, equalTo("1234567890"));
        assertThat(flushes.get(1).id, equalTo("1"));
    }

    @Test
    public void shouldIgnoreEventIdFieldContainingNulByte()
    {
        byte[] bytes = "id: a\0b\ndata: x\n\n".getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);

        decoder.decode(buffer, 0, bytes.length, output);

        assertThat(flushes.get(0).id, equalTo(""));
    }

    @Test
    public void shouldIgnoreUnrecognizedField()
    {
        decode("retry: 3000\ndata: x\n\n");

        assertThat(flushes.get(0).data, equalTo("x"));
    }

    private int decode(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        return decoder.decode(buffer, 0, bytes.length, output);
    }

    private static int byteLength(
        String text)
    {
        return text.getBytes(UTF_8).length;
    }

    private static final class Flushed
    {
        private final String event;
        private final String id;
        private final String data;

        private Flushed(
            String event,
            String id,
            String data)
        {
            this.event = event;
            this.id = id;
            this.data = data;
        }
    }
}
