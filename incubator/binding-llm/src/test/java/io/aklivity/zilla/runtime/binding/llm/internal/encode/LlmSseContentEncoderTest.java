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
package io.aklivity.zilla.runtime.binding.llm.internal.encode;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class LlmSseContentEncoderTest
{
    private final LlmSseContentEncoder encoder = new LlmSseContentEncoder();
    private final MutableDirectBuffer encoded = new UnsafeBuffer(new byte[64]);

    @Test
    public void shouldEncodeEventName()
    {
        int written = encoder.encodeEvent("message", encoded, 0, encoded.capacity());

        assertThat(text(written), equalTo("event: message\n"));
    }

    @Test
    public void shouldEncodeNoEventNameWhenNull()
    {
        int written = encoder.encodeEvent(null, encoded, 0, encoded.capacity());

        assertThat(written, equalTo(0));
    }

    @Test
    public void shouldEncodeDataLine()
    {
        int written = encodeData("hello there");

        assertThat(text(written), equalTo("data: hello there\n"));
    }

    @Test
    public void shouldEncodeFlushWithNoId()
    {
        int written = encodeFlush("");

        assertThat(text(written), equalTo("\n"));
    }

    @Test
    public void shouldEncodeFlushWithId()
    {
        int written = encodeFlush("42");

        assertThat(text(written), equalTo("id: 42\n\n"));
    }

    @Test
    public void shouldEncodeFullEventInOrder()
    {
        int position = encoder.encodeEvent("message", encoded, 0, encoded.capacity());
        position += encodeDataAt(position, "hello there");
        position += encodeFlushAt(position, "42");

        assertThat(text(position), equalTo("event: message\ndata: hello there\nid: 42\n\n"));
    }

    @Test
    public void shouldReturnZeroWhenDestinationTooSmallForEventName()
    {
        int written = encoder.encodeEvent("message", encoded, 0, 3);

        assertThat(written, equalTo(0));
    }

    @Test
    public void shouldReturnZeroWhenDestinationTooSmallForData()
    {
        byte[] bytes = "hello".getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);

        int written = encoder.encodeData(buffer, 0, bytes.length, true, true, encoded, 0, 3);

        assertThat(written, equalTo(0));
    }

    @Test
    public void shouldEncodeDataAcrossFragmentsWithoutRepeatingFraming()
    {
        int position = encodeDataFragmentAt(0, "hello ", true, false);
        position += encodeDataFragmentAt(position, "there ", false, false);
        position += encodeDataFragmentAt(position, "world", false, true);

        assertThat(text(position), equalTo("data: hello there world\n"));
    }

    @Test
    public void shouldReturnZeroWhenDestinationTooSmallForDataFragment()
    {
        byte[] bytes = "hello".getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);

        int written = encoder.encodeData(buffer, 0, bytes.length, false, false, encoded, 0, 3);

        assertThat(written, equalTo(0));
    }

    @Test
    public void shouldReturnZeroWhenDestinationTooSmallForFlush()
    {
        byte[] id = "42".getBytes(UTF_8);
        DirectBuffer idBuffer = new UnsafeBuffer(id);

        int written = encoder.encodeFlush(idBuffer, 0, id.length, encoded, 0, 3);

        assertThat(written, equalTo(0));
    }

    private int encodeData(
        String data)
    {
        byte[] bytes = data.getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        return encoder.encodeData(buffer, 0, bytes.length, true, true, encoded, 0, encoded.capacity());
    }

    private int encodeDataAt(
        int position,
        String data)
    {
        byte[] bytes = data.getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        return encoder.encodeData(buffer, 0, bytes.length, true, true, encoded, position, encoded.capacity());
    }

    private int encodeDataFragmentAt(
        int position,
        String data,
        boolean first,
        boolean last)
    {
        byte[] bytes = data.getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        return encoder.encodeData(buffer, 0, bytes.length, first, last, encoded, position, encoded.capacity());
    }

    private int encodeFlush(
        String id)
    {
        byte[] idBytes = id.getBytes(UTF_8);
        DirectBuffer idBuffer = new UnsafeBuffer(idBytes);
        return encoder.encodeFlush(idBuffer, 0, idBytes.length, encoded, 0, encoded.capacity());
    }

    private int encodeFlushAt(
        int position,
        String id)
    {
        byte[] idBytes = id.getBytes(UTF_8);
        DirectBuffer idBuffer = new UnsafeBuffer(idBytes);
        return encoder.encodeFlush(idBuffer, 0, idBytes.length, encoded, position, encoded.capacity());
    }

    private String text(
        int length)
    {
        return encoded.getStringWithoutLengthUtf8(0, length);
    }
}
