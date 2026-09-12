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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class LlmSseContentDecoderFactorySpiTest
{
    private final LlmSseContentDecoderFactorySpi spi = new LlmSseContentDecoderFactorySpi();

    @Test
    public void shouldReportSseContentType()
    {
        assertThat(spi.contentType(), equalTo("text/event-stream"));
    }

    @Test
    public void shouldSupplyWorkingDecoder()
    {
        LlmContentDecoder decoder = spi.supply();
        assertThat(decoder, not(nullValue()));

        byte[] bytes = "data: hello\n\n".getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        int[] decodedLength = { 0 };
        LlmContentDecoderOutput output = new LlmContentDecoderOutput()
        {
            @Override
            public void data(
                DirectBuffer data,
                int offset,
                int length)
            {
                decodedLength[0] = length;
            }

            @Override
            public void flush(
                String event,
                DirectBuffer data,
                int offset,
                int length)
            {
            }
        };

        int progress = decoder.decode(buffer, 0, bytes.length, output);

        assertThat(progress, equalTo(bytes.length));
        assertThat(decodedLength[0], equalTo("hello".length()));
    }
}
