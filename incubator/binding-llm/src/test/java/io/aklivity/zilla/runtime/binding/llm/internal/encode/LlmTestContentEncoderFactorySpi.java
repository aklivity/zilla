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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class LlmTestContentEncoderFactorySpi implements LlmContentEncoderSpi
{
    @Override
    public String contentType()
    {
        return "test/echo";
    }

    @Override
    public LlmContentEncoder supply()
    {
        return new LlmContentEncoder()
        {
            @Override
            public int encodeData(
                DirectBuffer buffer,
                int offset,
                int length,
                MutableDirectBuffer encoded,
                int encodedOffset,
                int encodedLimit)
            {
                int written = 0;
                if (encodedOffset + length <= encodedLimit)
                {
                    encoded.putBytes(encodedOffset, buffer, offset, length);
                    written = length;
                }
                return written;
            }

            @Override
            public int encodeFlush(
                String event,
                DirectBuffer id,
                int idOffset,
                int idLength,
                MutableDirectBuffer encoded,
                int encodedOffset,
                int encodedLimit)
            {
                return 0;
            }
        };
    }
}
