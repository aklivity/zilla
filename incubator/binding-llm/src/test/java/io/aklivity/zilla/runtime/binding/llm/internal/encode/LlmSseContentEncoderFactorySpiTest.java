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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class LlmSseContentEncoderFactorySpiTest
{
    private final LlmSseContentEncoderFactorySpi spi = new LlmSseContentEncoderFactorySpi();

    @Test
    public void shouldReportSseContentType()
    {
        assertThat(spi.contentType(), equalTo("text/event-stream"));
    }

    @Test
    public void shouldSupplyWorkingEncoder()
    {
        LlmContentEncoder encoder = spi.supply();
        assertThat(encoder, not(nullValue()));

        byte[] bytes = "hello".getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        MutableDirectBuffer encoded = new UnsafeBuffer(new byte[32]);

        int written = encoder.encodeData(buffer, 0, bytes.length, encoded, 0, encoded.capacity());

        assertThat(written, equalTo("data: hello\n".length()));
        assertThat(encoded.getStringWithoutLengthUtf8(0, written), equalTo("data: hello\n"));
    }
}
