/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.core.internal;

import org.agrona.DirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.collections.MutableLong;

public enum Int64Format
{

    TEXT
    {
        @Override
        public int decode(
            MutableLong decoded,
            MutableInteger processed,
            DirectBuffer data,
            int index,
            int length)
        {
            int progress = index;
            int limit = index + length;

            for (; progress < limit; progress++)
            {
                int digit = data.getByte(progress);

                if (digit < '0' || '9' < digit)
                {
                    if (processed.value == 0)
                    {
                        switch (digit)
                        {
                        case '-':
                            decoded.value = Long.MIN_VALUE;
                            processed.value++;
                            continue;
                        case '+':
                            decoded.value = Long.MAX_VALUE;
                            processed.value++;
                            continue;
                        default:
                            break;
                        }
                    }

                    progress = INVALID_INDEX;
                    break;
                }

                long multipler = 10L;

                if (processed.value == 1)
                {
                    if (decoded.value == Long.MIN_VALUE)
                    {
                        decoded.value = -1L;
                        multipler = 1L;
                    }
                    else if (decoded.value == Long.MAX_VALUE)
                    {
                        decoded.value = 1L;
                        multipler = 1L;
                    }
                }

                decoded.value = decoded.value * multipler + (digit - '0');
                processed.value++;
            }

            return progress;
        }

        @Override
        public boolean valid(
            MutableLong decoded,
            MutableInteger processed)
        {
            return processed.value > 1 || decoded.value != Long.MIN_VALUE || decoded.value != Long.MAX_VALUE;
        }
    },

    BINARY
    {
        private static final int INT64_SIZE = 8;

        @Override
        public int decode(
            MutableLong decoded,
            MutableInteger processed,
            DirectBuffer data,
            int index,
            int length)
        {
            int progress = index;
            int limit = index + length;

            for (; progress < limit; progress++)
            {
                int digit = data.getByte(progress);

                if (processed.value >= INT64_SIZE)
                {
                    progress = INVALID_INDEX;
                    break;
                }

                decoded.value <<= 8;
                decoded.value |= digit & 0xFF;
                processed.value++;
            }

            return progress;
        }

        @Override
        public boolean valid(
            MutableLong decoded,
            MutableInteger processed)
        {
            return processed.value == INT64_SIZE;
        }
    };

    public static final int INVALID_INDEX = -1;

    public abstract int decode(
        MutableLong decoded,
        MutableInteger processed,
        DirectBuffer data,
        int index,
        int length);

    public abstract boolean valid(
        MutableLong decoded,
        MutableInteger processed);

    public static Int64Format of(
        String format)
    {
        switch (format)
        {
        case "text":
            return TEXT;
        default:
            return BINARY;
        }
    }
}
