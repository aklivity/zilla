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
package io.aklivity.zilla.runtime.binding.agui.internal.codec;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

final class AguiValueExtent
{
    static final int MALFORMED = -1;

    private enum Kind
    {
        UNSET,
        STRING,
        COMPOSITE,
        SCALAR
    }

    private Kind kind = Kind.UNSET;
    private int depth;
    private boolean inString;
    private boolean escaped;
    private boolean complete;
    private boolean malformed;

    void reset()
    {
        kind = Kind.UNSET;
        depth = 0;
        inString = false;
        escaped = false;
        complete = false;
        malformed = false;
    }

    boolean complete()
    {
        return complete;
    }

    boolean string()
    {
        return kind == Kind.STRING;
    }

    int advance(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        if (kind == Kind.UNSET && progress < limit)
        {
            final byte first = buffer.getByte(progress);

            switch (first)
            {
            case '"':
                kind = Kind.STRING;
                inString = true;
                progress++;
                break;
            case '{':
            case '[':
                kind = Kind.COMPOSITE;
                depth = 1;
                progress++;
                break;
            case '}':
            case ']':
            case ',':
            case ':':
                malformed = true;
                break;
            default:
                kind = Kind.SCALAR;
                break;
            }
        }

        while (!complete && !malformed && progress < limit)
        {
            final byte octet = buffer.getByte(progress);

            switch (kind)
            {
            case STRING:
                progress++;
                if (escaped)
                {
                    escaped = false;
                }
                else if (octet == '\\')
                {
                    escaped = true;
                }
                else if (octet == '"')
                {
                    complete = true;
                }
                break;
            case COMPOSITE:
                progress++;
                if (inString)
                {
                    if (escaped)
                    {
                        escaped = false;
                    }
                    else if (octet == '\\')
                    {
                        escaped = true;
                    }
                    else if (octet == '"')
                    {
                        inString = false;
                    }
                }
                else if (octet == '"')
                {
                    inString = true;
                }
                else if (octet == '{' || octet == '[')
                {
                    depth++;
                }
                else if (octet == '}' || octet == ']')
                {
                    depth--;
                    complete = depth == 0;
                }
                break;
            default:
                if (delimiter(octet))
                {
                    complete = true;
                }
                else
                {
                    progress++;
                }
                break;
            }
        }

        return malformed ? MALFORMED : progress - offset;
    }

    private static boolean delimiter(
        byte octet)
    {
        return octet == ',' || octet == '}' || octet == ']' || octet == ' ' || octet == '\n' || octet == '\r' || octet == '\t';
    }
}
