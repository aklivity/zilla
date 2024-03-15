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

public enum Int32Format
{

    TEXT
    {
        @Override
        public int decode(
            MutableInteger decoded,
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
                            decoded.value = Integer.MIN_VALUE;
                            processed.value++;
                            continue;
                        case '+':
                            decoded.value = Integer.MAX_VALUE;
                            processed.value++;
                            continue;
                        default:
                            break;
                        }
                    }

                    progress = INVALID_INDEX;
                    break;
                }

                int multipler = 10;

                if (processed.value == 1)
                {
                    switch (decoded.value)
                    {
                    case Integer.MIN_VALUE:
                        decoded.value = -1;
                        multipler = 1;
                        break;
                    case Integer.MAX_VALUE:
                        decoded.value = 1;
                        multipler = 1;
                        break;
                    default:
                        break;
                    }
                }

                decoded.value = decoded.value * multipler + (digit - '0');
                processed.value++;
            }

            return progress;
        }

        @Override
        public boolean valid(
            MutableInteger decoded,
            MutableInteger processed)
        {
            return processed.value > 1 || decoded.value != Integer.MIN_VALUE || decoded.value != Integer.MAX_VALUE;
        }
    },

    BINARY
    {
        private static final int INT32_SIZE = 4;

        @Override
        public int decode(
            MutableInteger decoded,
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

                if (processed.value >= INT32_SIZE)
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
            MutableInteger decoded,
            MutableInteger processed)
        {
            return processed.value == INT32_SIZE;
        }
    };

    public static final int INVALID_INDEX = -1;

    public abstract int decode(
        MutableInteger decoded,
        MutableInteger processed,
        DirectBuffer data,
        int index,
        int length);

    public abstract boolean valid(
        MutableInteger decoded,
        MutableInteger processed);

    public static Int32Format of(
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
