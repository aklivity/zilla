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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Finds where a top-level JSON object or array ends in a byte stream delivered across arbitrary fragments.
 */
final class A2aJsonScanner
{
    private int depth;
    private boolean string;
    private boolean escaped;

    boolean inMessage()
    {
        return depth > 0;
    }

    /**
     * Scans the fragment and returns the offset just past the end of the current message,
     * or {@code -1} if the message continues beyond the fragment.
     */
    int scan(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int end = -1;

        for (int index = offset; end == -1 && index < limit; index++)
        {
            final byte value = buffer.getByte(index);

            if (string)
            {
                if (escaped)
                {
                    escaped = false;
                }
                else if (value == '\\')
                {
                    escaped = true;
                }
                else if (value == '"')
                {
                    string = false;
                }
            }
            else if (value == '"')
            {
                string = true;
            }
            else if (value == '{' || value == '[')
            {
                depth++;
            }
            else if ((value == '}' || value == ']') && depth > 0)
            {
                depth--;
                if (depth == 0)
                {
                    end = index + 1;
                }
            }
        }

        return end;
    }
}
