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

public enum IntegerFormat
{
    TEXT
    {
        @Override
        public int decode(
            int value,
            byte digit)
        {
            return value * 10 + (digit - '0');
        }

        @Override
        public boolean digit(
            byte digit)
        {
            return digit >= '0' && digit <= '9';
        }

        @Override
        public boolean negative(
            byte digit)
        {
            return digit == '-';
        }

        @Override
        public boolean validateFin(
            int pendingBytes,
            boolean number)
        {
            return number;
        }
    },

    BINARY
    {
        private static final int INT_32_SIZE = 4;

        @Override
        public int decode(
            int value,
            byte digit)
        {
            value <<= 8;
            value |= digit & 0xFF;

            return value;
        }

        @Override
        public boolean validateFin(
            int pendingBytes,
            boolean number)
        {
            return pendingBytes == INT_32_SIZE;
        }

        @Override
        public boolean validateContinue(
            int progress)
        {
            return progress <= INT_32_SIZE;
        }
    };

    public abstract int decode(
        int value,
        byte digit);

    public abstract boolean validateFin(
        int pendingBytes,
        boolean number);

    public boolean validateContinue(
        int progress)
    {
        return true;
    }

    public boolean negative(
        byte digit)
    {
        return false;
    }

    public boolean digit(
        byte digit)
    {
        return true;
    }

    public static IntegerFormat of(
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
