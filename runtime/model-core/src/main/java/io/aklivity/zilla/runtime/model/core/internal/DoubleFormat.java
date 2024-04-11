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

public enum DoubleFormat
{
    TEXT
    {
        private static final double MULTIPLER = 10.0;

        @Override
        public int decode(
            DoubleState state,
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

                if (digit == '.')
                {
                    if (state.divider == 0)
                    {
                        state.divider = MULTIPLER;
                        state.processed++;
                        continue;
                    }
                    else
                    {
                        progress = INVALID_INDEX;
                        break;
                    }
                }

                if (digit < '0' || '9' < digit)
                {
                    if (state.processed == 0)
                    {
                        switch (digit)
                        {
                        case '-':
                            state.value = Double.MIN_VALUE;
                            state.processed++;
                            continue;
                        case '+':
                            state.value = Double.MAX_VALUE;
                            state.processed++;
                            continue;
                        default:
                            break;
                        }
                    }
                    progress = INVALID_INDEX;
                    break;
                }

                if (state.value == Double.MIN_VALUE)
                {
                    state.value *= -1.0F;
                }

                if (state.value == Double.MIN_VALUE || state.value == Double.MAX_VALUE)
                {
                    state.value = state.divider != 0 ? (digit - '0') / state.divider : (digit - '0');
                    state.divider *= MULTIPLER;
                }
                else
                {
                    if (state.divider != 0)
                    {
                        state.value = state.value < 0
                            ? state.value - (digit - '0') / state.divider
                            : state.value + (digit - '0') / state.divider;
                        state.divider *= MULTIPLER;
                    }
                    else
                    {
                        state.value *= MULTIPLER;
                        state.value = state.value < 0
                            ? state.value - (digit - '0')
                            : state.value + (digit - '0');
                    }
                }
                state.processed++;
            }

            return progress;
        }

        @Override
        public boolean valid(
            DoubleState state)
        {
            return state.processed > 1 || state.value != Double.MIN_VALUE || state.value != Double.MAX_VALUE;
        }
    },

    BINARY
    {
        private static final int DOUBLE_SIZE = 8;

        @Override
        public int decode(
            DoubleState state,
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

                if (state.processed >= DOUBLE_SIZE)
                {
                    progress = INVALID_INDEX;
                    break;
                }

                state.decoded <<= 8;
                state.decoded |= digit & 0xFF;
                state.processed++;
            }

            if ((flags & FLAGS_FIN) != 0x00)
            {
                state.value = Double.longBitsToDouble(state.decoded);
            }

            return progress;
        }

        @Override
        public boolean valid(
            DoubleState state)
        {
            return state.processed == DOUBLE_SIZE;
        }
    };

    public static final int INVALID_INDEX = -1;

    public abstract int decode(
        DoubleState state,
        int flags,
        DirectBuffer data,
        int index,
        int length);

    public abstract boolean valid(
        DoubleState state);

    public static DoubleFormat of(
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
