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
    public void shouldEncodeDataLine()
    {
        int written = encodeData("hello there");

        assertThat(text(written), equalTo("data: hello there\n"));
    }

    @Test
    public void shouldEncodeFlushWithEventNameAndNoId()
    {
        int written = encodeFlush("message", "");

        assertThat(text(written), equalTo("event: message\n\n"));
    }

    @Test
    public void shouldEncodeFlushWithoutEventName()
    {
        int written = encodeFlush(null, "");

        assertThat(text(written), equalTo("\n"));
    }

    @Test
    public void shouldEncodeFlushWithEventIdOmittingNullEventName()
    {
        int written = encodeFlush(null, "42");

        assertThat(text(written), equalTo("id: 42\n\n"));
    }

    @Test
    public void shouldEncodeFlushWithEventNameAndId()
    {
        int written = encodeFlush("message", "42");

        assertThat(text(written), equalTo("event: message\nid: 42\n\n"));
    }

    @Test
    public void shouldReturnZeroWhenDestinationTooSmallForData()
    {
        byte[] bytes = "hello".getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);

        int written = encoder.encodeData(buffer, 0, bytes.length, encoded, 0, 3);

        assertThat(written, equalTo(0));
    }

    @Test
    public void shouldReturnZeroWhenDestinationTooSmallForFlush()
    {
        byte[] id = "42".getBytes(UTF_8);
        DirectBuffer idBuffer = new UnsafeBuffer(id);

        int written = encoder.encodeFlush("message", idBuffer, 0, id.length, encoded, 0, 3);

        assertThat(written, equalTo(0));
    }

    private int encodeData(
        String data)
    {
        byte[] bytes = data.getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        return encoder.encodeData(buffer, 0, bytes.length, encoded, 0, encoded.capacity());
    }

    private int encodeFlush(
        String event,
        String id)
    {
        byte[] idBytes = id.getBytes(UTF_8);
        DirectBuffer idBuffer = new UnsafeBuffer(idBytes);
        return encoder.encodeFlush(event, idBuffer, 0, idBytes.length, encoded, 0, encoded.capacity());
    }

    private String text(
        int length)
    {
        return encoded.getStringWithoutLengthUtf8(0, length);
    }
}
