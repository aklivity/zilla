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

import static io.aklivity.zilla.runtime.engine.model.ValidatorHandler.FLAGS_FIN;

import org.agrona.DirectBuffer;

public enum Int64Format
{

    TEXT
    {
        private static final long MULTIPLER = 10L;

        @Override
        public int decode(
            Int64State state,
            int flags,
            DirectBuffer data,
            int index,
            int length)
        {
            int progress = index;
            int limit = index + length;

            for (; progress < limit; progress++)
            {
                int digit = data.getByte(progress);

                if ((flags & FLAGS_FIN) != 0x00 && progress == limit - 1 && (digit == 'L' || digit == 'l') && state.processed > 0)
                {
                    state.processed++;
                    break;
                }

                if (digit < '0' || '9' < digit)
                {
                    if (state.processed == 0)
                    {
                        switch (digit)
                        {
                        case '-':
                            state.decoded = Long.MIN_VALUE;
                            state.processed++;
                            continue;
                        case '+':
                            state.decoded = Long.MAX_VALUE;
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
                    if (state.decoded == Long.MIN_VALUE)
                    {
                        state.decoded = -1L * (digit - '0');
                    }
                    else if (state.decoded == Long.MAX_VALUE)
                    {
                        state.decoded = digit - '0';
                    }
                    else
                    {
                        state.decoded = state.decoded * MULTIPLER + (digit - '0');
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
            Int64State state)
        {
            return state.processed > 1 || state.decoded != Long.MIN_VALUE || state.decoded != Long.MAX_VALUE;
        }
    },

    BINARY
    {
        private static final int INT64_SIZE = 8;

        @Override
        public int decode(
            Int64State state,
            int flags,
            DirectBuffer data,
            int index,
            int length)
        {
            int progress = index;
            int limit = index + length;

            for (; progress < limit; progress++)
            {
                int digit = data.getByte(progress);

                if (state.processed >= INT64_SIZE)
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
            Int64State state)
        {
            return state.processed == INT64_SIZE;
        }
    };

    public static final int INVALID_INDEX = -1;

    public abstract int decode(
        Int64State state,
        int flags,
        DirectBuffer data,
        int index,
        int length);

    public abstract boolean valid(
        Int64State state);

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
