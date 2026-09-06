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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class LlmContentDecoderFactoryTest
{
    private final LlmContentDecoderFactory factory = new LlmContentDecoderFactory();

    @Test
    public void shouldResolveRegisteredContentType()
    {
        assertThat(factory.contentTypes(), hasItem("test/echo"));
    }

    @Test
    public void shouldDispatchDecoderForRegisteredContentType()
    {
        LlmContentDecoder decoder = factory.create("test/echo");
        assertThat(decoder, not(nullValue()));

        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[8]);
        int[] decodedLength = new int[1];
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

        int progress = decoder.decode(buffer, 0, buffer.capacity(), output);

        assertThat(progress, not(0));
        assertThat(decodedLength[0], not(0));
    }

    @Test
    public void shouldReturnNullForUnrecognizedContentType()
    {
        assertThat(factory.create("application/unrecognized"), nullValue());
    }
}
