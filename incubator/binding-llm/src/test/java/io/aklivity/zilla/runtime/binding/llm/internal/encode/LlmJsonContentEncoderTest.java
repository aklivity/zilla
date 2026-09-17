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

public class LlmJsonContentEncoderTest
{
    private final LlmJsonContentEncoder encoder = new LlmJsonContentEncoder();
    private final MutableDirectBuffer encoded = new UnsafeBuffer(new byte[64]);

    @Test
    public void shouldCopyDataThroughUnchanged()
    {
        int written = encodeData("{\"ok\":true}");

        assertThat(text(written), equalTo("{\"ok\":true}"));
    }

    @Test
    public void shouldReturnZeroWhenDestinationTooSmallForData()
    {
        byte[] bytes = "{\"ok\":true}".getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);

        int written = encoder.encodeData(buffer, 0, bytes.length, encoded, 0, 3);

        assertThat(written, equalTo(0));
    }

    @Test
    public void shouldEncodeEventNameAsNoBytes()
    {
        int written = encoder.encodeEventName("message", encoded, 0, encoded.capacity());

        assertThat(written, equalTo(0));
    }

    @Test
    public void shouldEncodeFlushAsNoBytes()
    {
        DirectBuffer idBuffer = new UnsafeBuffer(new byte[0]);

        int written = encoder.encodeFlush(idBuffer, 0, 0, encoded, 0, encoded.capacity());

        assertThat(written, equalTo(0));
    }

    private int encodeData(
        String data)
    {
        byte[] bytes = data.getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        return encoder.encodeData(buffer, 0, bytes.length, encoded, 0, encoded.capacity());
    }

    private String text(
        int length)
    {
        return encoded.getStringWithoutLengthUtf8(0, length);
    }
}
