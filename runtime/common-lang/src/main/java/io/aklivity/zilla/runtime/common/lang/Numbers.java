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

/**
 * Allocation-free parsing of decimal numbers from a {@link CharSequence}, for streaming callers that hold a
 * non-owning char view of a lexeme and must not allocate the {@code String} (and JDK parser scratch buffer)
 * that {@code Double.parseDouble(view.toString())} / {@code Float.parseFloat(view.toString())} would.
 * <p>
 * {@link #parseDouble(CharSequence)} and {@link #parseFloat(CharSequence)} use a Clinger exactly-rounded fast
 * path — a single IEEE multiply or divide of an integer significand against a power-of-ten table — and fall
 * back to the JDK parser for anything outside the fast-path bounds or any non-numeric lexeme
 * ({@code Infinity}/{@code -Infinity}/{@code NaN}), so they remain correct and allocate only on the rare path.
 */
public final class Numbers
{
    // 10^n is exactly representable as a double for n in [0, 22].
    private static final double[] POW10_DOUBLE =
    {
        1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
        1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
    };

    // largest significand for which signif and signif * 10^k are exact: 2^53 - 1.
    private static final long MAX_DOUBLE_SIGNIF = 9_007_199_254_740_991L;

    // 10^n is exactly representable as a float for n in [0, 10].
    private static final float[] POW10_FLOAT =
    {
        1e0f, 1e1f, 1e2f, 1e3f, 1e4f, 1e5f, 1e6f, 1e7f, 1e8f, 1e9f, 1e10f
    };

    // largest significand for which signif and signif * 10^k are exact in float: 2^24.
    private static final long MAX_FLOAT_SIGNIF = 1L << 24;

    private Numbers()
    {
    }

    // Parse a finite decimal CharSequence allocation-free using a Clinger fast path
    // (exactly rounded single multiply/divide), falling back to Double.parseDouble for
    // anything outside the fast-path bounds or any non-numeric lexeme (Infinity/NaN).
    public static double parseDouble(
        CharSequence value)
    {
        int length = value.length();
        int index = 0;
        boolean negative = false;
        if (index < length)
        {
            char c = value.charAt(index);
            if (c == '-')
            {
                negative = true;
                index++;
            }
            else if (c == '+')
            {
                index++;
            }
        }

        long signif = 0L;
        int digits = 0;
        int fractionExponent = 0;
        boolean dotSeen = false;
        boolean fastPath = index < length;
        boolean overflow = false;
        while (index < length)
        {
            char c = value.charAt(index);
            if (c >= '0' && c <= '9')
            {
                if (signif > (MAX_DOUBLE_SIGNIF - 9) / 10)
                {
                    overflow = true;
                    break;
                }
                signif = signif * 10 + (c - '0');
                digits++;
                if (dotSeen)
                {
                    fractionExponent--;
                }
                index++;
            }
            else if (c == '.' && !dotSeen)
            {
                dotSeen = true;
                index++;
            }
            else
            {
                break;
            }
        }

        int explicitExponent = 0;
        if (!overflow && index < length && (value.charAt(index) == 'e' || value.charAt(index) == 'E'))
        {
            index++;
            boolean expNegative = false;
            if (index < length && (value.charAt(index) == '-' || value.charAt(index) == '+'))
            {
                expNegative = value.charAt(index) == '-';
                index++;
            }
            int expStart = index;
            int exp = 0;
            while (index < length && value.charAt(index) >= '0' && value.charAt(index) <= '9')
            {
                exp = exp * 10 + (value.charAt(index) - '0');
                if (exp > 1000)
                {
                    exp = 1000;
                }
                index++;
            }
            if (index == expStart)
            {
                fastPath = false;
            }
            explicitExponent = expNegative ? -exp : exp;
        }

        double result;
        int exponent = fractionExponent + explicitExponent;
        if (fastPath && !overflow && index == length && digits > 0 && digits <= 15 &&
            exponent >= -22 && exponent <= 22)
        {
            result = exponent >= 0
                ? signif * POW10_DOUBLE[exponent]
                : signif / POW10_DOUBLE[-exponent];
            result = negative ? -result : result;
        }
        else
        {
            result = Double.parseDouble(value.toString());
        }
        return result;
    }

    // Parse a finite decimal CharSequence to float allocation-free using a float-domain
    // Clinger fast path (no double narrowing, to avoid double rounding), falling back to
    // Float.parseFloat for anything outside the fast-path bounds or non-numeric lexemes.
    public static float parseFloat(
        CharSequence value)
    {
        int length = value.length();
        int index = 0;
        boolean negative = false;
        if (index < length)
        {
            char c = value.charAt(index);
            if (c == '-')
            {
                negative = true;
                index++;
            }
            else if (c == '+')
            {
                index++;
            }
        }

        long signif = 0L;
        int digits = 0;
        int fractionExponent = 0;
        boolean dotSeen = false;
        boolean fastPath = index < length;
        boolean overflow = false;
        while (index < length)
        {
            char c = value.charAt(index);
            if (c >= '0' && c <= '9')
            {
                if (signif > (MAX_FLOAT_SIGNIF - 9) / 10)
                {
                    overflow = true;
                    break;
                }
                signif = signif * 10 + (c - '0');
                digits++;
                if (dotSeen)
                {
                    fractionExponent--;
                }
                index++;
            }
            else if (c == '.' && !dotSeen)
            {
                dotSeen = true;
                index++;
            }
            else
            {
                break;
            }
        }

        int explicitExponent = 0;
        if (!overflow && index < length && (value.charAt(index) == 'e' || value.charAt(index) == 'E'))
        {
            index++;
            boolean expNegative = false;
            if (index < length && (value.charAt(index) == '-' || value.charAt(index) == '+'))
            {
                expNegative = value.charAt(index) == '-';
                index++;
            }
            int expStart = index;
            int exp = 0;
            while (index < length && value.charAt(index) >= '0' && value.charAt(index) <= '9')
            {
                exp = exp * 10 + (value.charAt(index) - '0');
                if (exp > 1000)
                {
                    exp = 1000;
                }
                index++;
            }
            if (index == expStart)
            {
                fastPath = false;
            }
            explicitExponent = expNegative ? -exp : exp;
        }

        float result;
        int exponent = fractionExponent + explicitExponent;
        if (fastPath && !overflow && index == length && digits > 0 && digits <= 7 &&
            exponent >= -10 && exponent <= 10)
        {
            result = exponent >= 0
                ? signif * POW10_FLOAT[exponent]
                : signif / POW10_FLOAT[-exponent];
            result = negative ? -result : result;
        }
        else
        {
            result = Float.parseFloat(value.toString());
        }
        return result;
    }
}
