/*
 * Copyright 2021-2024 Aklivity Inc
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

public enum Int32Format
{

    TEXT
    {
        private static final int MULTIPLER = 10;

        @Override
        public int decode(
            Int32State state,
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
                    if (state.processed == 0)
                    {
                        switch (digit)
                        {
                        case '-':
                            state.decoded = Integer.MIN_VALUE;
                            state.processed++;
                            continue;
                        case '+':
                            state.decoded = Integer.MAX_VALUE;
                            state.processed++;
                            continue;
                        default:
                            break;
                        }
                    }

                    progress = INVALID_INDEX;
                    break;
                }

                if (state.processed == 1)
                {
                    switch (state.decoded)
                    {
                    case Integer.MIN_VALUE:
                        state.decoded = -1 * (digit - '0');
                        break;
                    case Integer.MAX_VALUE:
                        state.decoded = digit - '0';
                        break;
                    default:
                        state.decoded = state.decoded * MULTIPLER + (digit - '0');
                        break;
                    }
                }
                else
                {
                    state.decoded *= MULTIPLER;
                    state.decoded = state.decoded < 0
                        ? state.decoded - (digit - '0')
                        : state.decoded + (digit - '0');
                }
                state.processed++;
            }

            return progress;
        }

        @Override
        public boolean valid(
            Int32State state)
        {
            return state.processed > 1 || state.decoded != Integer.MIN_VALUE || state.decoded != Integer.MAX_VALUE;
        }
    },

    BINARY
    {
        private static final int INT32_SIZE = 4;

        @Override
        public int decode(
            Int32State state,
            DirectBuffer data,
            int index,
            int length)
        {
            int progress = index;
            int limit = index + length;

            for (; progress < limit; progress++)
            {
                int digit = data.getByte(progress);

                if (state.processed >= INT32_SIZE)
                {
                    progress = INVALID_INDEX;
                    break;
                }

                state.decoded <<= 8;
                state.decoded |= digit & 0xFF;
                state.processed++;
            }

            return progress;
        }

        @Override
        public boolean valid(
            Int32State state)
        {
            return state.processed == INT32_SIZE;
        }
    };

    public static final int INVALID_INDEX = -1;

    public abstract int decode(
        Int32State state,
        DirectBuffer data,
        int index,
        int length);

    public abstract boolean valid(
        Int32State state);

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
