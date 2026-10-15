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
package io.aklivity.zilla.runtime.classifier.patterns.internal;

import java.util.Arrays;

final class PatternsEntropy
{
    private static final double LOG_2 = Math.log(2.0);

    private char[] buffer;

    PatternsEntropy()
    {
        this.buffer = new char[64];
    }

    double measure(
        CharSequence text,
        int start,
        int end)
    {
        final int length = end - start;

        if (length > buffer.length)
        {
            buffer = new char[Math.max(length, buffer.length << 1)];
        }

        for (int i = 0; i < length; i++)
        {
            buffer[i] = text.charAt(start + i);
        }
        Arrays.sort(buffer, 0, length);

        double entropy = 0.0;
        for (int run = 0, i = 1; i <= length; i++)
        {
            if (i == length || buffer[i] != buffer[run])
            {
                final double probability = (double) (i - run) / length;
                entropy -= probability * Math.log(probability) / LOG_2;
                run = i;
            }
        }

        return entropy;
    }
}
