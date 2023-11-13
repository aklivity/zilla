/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.config;

import java.util.Comparator;

public class HttpQueryStringComparator implements Comparator<String>
{
    @Override
    public int compare(
        String input1,
        String input2)
    {
        int result = 0;
        int index1 = 0;
        int index2 = 0;

        while (hasNext(input1, index1) && hasNext(input2, index2))
        {
            boolean percent1 = percent(input1, index1);
            boolean percent2 = percent(input2, index2);
            char char1 = charAt(input1, index1, percent1);
            char char2 = charAt(input2, index2, percent2);

            if (char1 != char2)
            {
                result = Character.compare(char1, char2);
                break;
            }

            index1 = nextIndex(index1, percent1);
            index2 = nextIndex(index2, percent2);
        }

        return result == 0 ? Boolean.compare(hasNext(input1, index1), hasNext(input2, index2)) : result;
    }

    private boolean hasNext(
        String input,
        int index)
    {
        return index < input.length();
    }

    private boolean percent(
        String input,
        int index)
    {
        return input.charAt(index) == '%' && index + 3 <= input.length();
    }

    private char charAt(
        String input,
        int index,
        boolean percent)
    {
        char result;
        if (percent)
        {
            char hexDigit1 = input.charAt(index + 1);
            char hexDigit2 = input.charAt(index + 2);
            result = toChar(hexDigit1, hexDigit2);
        }
        else
        {
            result = input.charAt(index);
        }
        return result;
    }

    private char toChar(
        char hexDigit1,
        char hexDigit2)
    {
        int int1 = Character.digit(hexDigit1, 16);
        int int2 = Character.digit(hexDigit2, 16);
        return (char) ((int1 << 4) | int2);
    }

    private int nextIndex(
        int index,
        boolean percent)
    {
        return index + (percent ? 3 : 1);
    }
}
