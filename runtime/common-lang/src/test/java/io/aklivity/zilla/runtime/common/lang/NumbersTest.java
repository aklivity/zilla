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
package io.aklivity.zilla.runtime.common.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

public class NumbersTest
{
    private void assertDoubleBits(
        String value)
    {
        long expected = Double.doubleToLongBits(Double.parseDouble(value));
        long actual = Double.doubleToLongBits(Numbers.parseDouble(value));
        assertEquals(expected, actual, () -> "double mismatch for \"" + value + "\"");
    }

    private void assertFloatBits(
        String value)
    {
        int expected = Float.floatToIntBits(Float.parseFloat(value));
        int actual = Float.floatToIntBits(Numbers.parseFloat(value));
        assertEquals(expected, actual, () -> "float mismatch for \"" + value + "\"");
    }

    @Test
    public void shouldParseDoubleLiterals()
    {
        String[] values =
        {
            "0", "-0", "0.0", "-0.0", "1", "-1", "1.5", "-1.5", "1.0",
            "3.141592653589793", "2.718281828459045",
            "100", "1000000", "1234567890", "123456789012345",
            "0.1", "0.2", "0.3", "0.5", "0.25", "0.125",
            "10", "100", "1000", "0.001", "0.0001",
            "1e0", "1e1", "1e-1", "1e10", "1e-10", "1e22", "1e-22",
            "1e23", "1e-23", "1e100", "1e-100", "1e308", "1e-308",
            "1.7976931348623157e308", "4.9e-324",
            "9007199254740991", "9007199254740992", "9007199254740993",
            "12345678901234567890", "0.123456789012345678",
            "+1", "+1.5", "+0", "+1e5", "1E10", "1E-10", "1.5E3", "1.5e3",
            "00.5", "0.50", "000123", "1.500", "0010",
            "Infinity", "-Infinity", "NaN",
            String.valueOf(Double.MIN_VALUE),
            String.valueOf(Double.MAX_VALUE),
            String.valueOf(Double.MIN_NORMAL),
            String.valueOf(-Double.MAX_VALUE),
        };
        for (String value : values)
        {
            assertDoubleBits(value);
        }
    }

    @Test
    public void shouldParseFloatLiterals()
    {
        String[] values =
        {
            "0", "-0", "0.0", "-0.0", "1", "-1", "1.5", "-1.5", "1.0",
            "3.14159", "2.71828",
            "100", "1000000", "16777215", "16777216", "16777217",
            "0.1", "0.2", "0.3", "0.5", "0.25", "0.125",
            "1e0", "1e1", "1e-1", "1e10", "1e-10", "1e7", "1e-7", "1e8", "1e-8",
            "1e38", "1e-38", "1e39", "1e-45",
            "3.4028235e38", "1.4e-45",
            "+1", "+1.5", "+0", "1E10", "1.5E3", "1.5e3",
            "00.5", "0.50", "000123", "1.500", "0010",
            "Infinity", "-Infinity", "NaN",
            String.valueOf(Float.MIN_VALUE),
            String.valueOf(Float.MAX_VALUE),
            String.valueOf(Float.MIN_NORMAL),
            String.valueOf(-Float.MAX_VALUE),
        };
        for (String value : values)
        {
            assertFloatBits(value);
        }
    }

    @Test
    public void shouldParseRandomDoublesBitExact()
    {
        Random random = new Random(20260619L);
        for (int i = 0; i < 100_000; i++)
        {
            double d = Double.longBitsToDouble(random.nextLong());
            if (Double.isFinite(d))
            {
                assertDoubleBits(Double.toString(d));
            }
        }
    }

    @Test
    public void shouldParseRandomFloatsBitExact()
    {
        Random random = new Random(20260620L);
        for (int i = 0; i < 100_000; i++)
        {
            float f = Float.intBitsToFloat(random.nextInt());
            if (Float.isFinite(f))
            {
                assertFloatBits(Float.toString(f));
            }
        }
    }

    @Test
    public void shouldParseRandomDigitStringsBitExact()
    {
        Random random = new Random(20260621L);
        for (int i = 0; i < 50_000; i++)
        {
            StringBuilder builder = new StringBuilder();
            if (random.nextBoolean())
            {
                builder.append(random.nextBoolean() ? '-' : '+');
            }
            int intDigits = random.nextInt(20);
            int fracDigits = random.nextInt(20);
            if (intDigits == 0 && fracDigits == 0)
            {
                intDigits = 1;
            }
            for (int d = 0; d < intDigits; d++)
            {
                builder.append((char) ('0' + random.nextInt(10)));
            }
            if (fracDigits > 0)
            {
                builder.append('.');
                for (int d = 0; d < fracDigits; d++)
                {
                    builder.append((char) ('0' + random.nextInt(10)));
                }
            }
            if (random.nextBoolean())
            {
                builder.append(random.nextBoolean() ? 'e' : 'E');
                if (random.nextBoolean())
                {
                    builder.append(random.nextBoolean() ? '-' : '+');
                }
                builder.append(random.nextInt(330));
            }
            assertDoubleBits(builder.toString());
        }
    }

    @Test
    public void shouldParseRandomDigitStringsBitExactFloat()
    {
        Random random = new Random(20260622L);
        for (int i = 0; i < 50_000; i++)
        {
            StringBuilder builder = new StringBuilder();
            if (random.nextBoolean())
            {
                builder.append(random.nextBoolean() ? '-' : '+');
            }
            int intDigits = random.nextInt(12);
            int fracDigits = random.nextInt(12);
            if (intDigits == 0 && fracDigits == 0)
            {
                intDigits = 1;
            }
            for (int d = 0; d < intDigits; d++)
            {
                builder.append((char) ('0' + random.nextInt(10)));
            }
            if (fracDigits > 0)
            {
                builder.append('.');
                for (int d = 0; d < fracDigits; d++)
                {
                    builder.append((char) ('0' + random.nextInt(10)));
                }
            }
            if (random.nextBoolean())
            {
                builder.append(random.nextBoolean() ? 'e' : 'E');
                if (random.nextBoolean())
                {
                    builder.append(random.nextBoolean() ? '-' : '+');
                }
                builder.append(random.nextInt(50));
            }
            assertFloatBits(builder.toString());
        }
    }

    @Test
    public void shouldParseExponentBoundaries()
    {
        for (int exp = -30; exp <= 30; exp++)
        {
            assertDoubleBits("1e" + exp);
            assertDoubleBits("1.5e" + exp);
            assertDoubleBits("9007199254740991e" + exp);
            assertFloatBits("1e" + exp);
            assertFloatBits("1.5e" + exp);
        }
    }

    @Test
    public void shouldParseCharSequenceSubviews()
    {
        assertEquals(Double.doubleToLongBits(1.5),
            Double.doubleToLongBits(Numbers.parseDouble(new StringBuilder("1.5"))));
        assertEquals(Float.floatToIntBits(1.5f),
            Float.floatToIntBits(Numbers.parseFloat(new StringBuilder("1.5"))));
    }
}
