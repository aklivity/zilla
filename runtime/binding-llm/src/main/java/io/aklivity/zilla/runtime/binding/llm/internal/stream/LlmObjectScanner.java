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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

final class LlmObjectScanner
{
    static final int PREFIX = 0;
    static final int VALUE = 1;
    static final int SUFFIX = 2;
    static final int TAIL = 3;

    private static final int STATE_START = 0;
    private static final int STATE_KEY_OPEN = 1;
    private static final int STATE_KEY = 2;
    private static final int STATE_COLON = 3;
    private static final int STATE_VALUE = 4;
    private static final int STATE_SKIP = 5;
    private static final int STATE_AFTER = 6;
    private static final int STATE_END = 7;

    private final byte[] key;
    private final boolean quoted;

    private int region;
    private int state;
    private int keyLength;
    private boolean keyMatches;
    private int depth;
    private boolean inString;
    private boolean escaped;
    private boolean valueStarted;
    private boolean scalar;

    static LlmObjectScanner text()
    {
        return string("text");
    }

    static LlmObjectScanner string(
        String key)
    {
        return new LlmObjectScanner(key.getBytes(UTF_8), true);
    }

    static LlmObjectScanner member(
        String key)
    {
        return new LlmObjectScanner(key.getBytes(UTF_8), false);
    }

    private LlmObjectScanner(
        byte[] key,
        boolean quoted)
    {
        this.key = key;
        this.quoted = quoted;
    }

    int region()
    {
        return region;
    }

    void copy(
        LlmObjectScanner from)
    {
        region = from.region;
        state = from.state;
        keyLength = from.keyLength;
        keyMatches = from.keyMatches;
        depth = from.depth;
        inString = from.inString;
        escaped = from.escaped;
        valueStarted = from.valueStarted;
        scalar = from.scalar;
    }

    void settle()
    {
        region = TAIL;
    }

    void begin()
    {
        region = PREFIX;
        state = STATE_START;
        keyLength = 0;
        keyMatches = false;
        depth = 0;
        inString = false;
        escaped = false;
        valueStarted = false;
        scalar = false;
    }

    int scan(
        DirectBufferEx buffer,
        int from,
        int limit)
    {
        int index = from;

        switch (region)
        {
        case PREFIX:
            while (index < limit && region == PREFIX)
            {
                prefix(buffer.getByte(index++));
            }

            if (region == VALUE && !quoted)
            {
                index--;
            }
            break;
        case VALUE:
            if (quoted)
            {
                index = scanText(buffer, index, limit);
            }
            else
            {
                index = scanMember(buffer, index, limit);
            }
            break;
        default:
            index = limit;
            break;
        }

        return index;
    }

    private int scanText(
        DirectBufferEx buffer,
        int from,
        int limit)
    {
        int index = from;

        while (index < limit && region == VALUE)
        {
            final byte value = buffer.getByte(index);

            if (escaped)
            {
                escaped = false;
                index++;
            }
            else if (value == '\\')
            {
                escaped = true;
                index++;
            }
            else if (value == '"')
            {
                region = SUFFIX;
            }
            else
            {
                index++;
            }
        }

        return index;
    }

    private int scanMember(
        DirectBufferEx buffer,
        int from,
        int limit)
    {
        int index = from;

        while (index < limit && region == VALUE)
        {
            final byte value = buffer.getByte(index);

            if (!valueStarted)
            {
                valueStarted = true;
                inString = value == '"';
                scalar = !inString && value != '{' && value != '[';
                depth = value == '{' || value == '[' ? 1 : 0;
                escaped = false;
                index++;
            }
            else if (inString)
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
                    inString = false;
                    region = depth == 0 ? SUFFIX : region;
                }

                index++;
            }
            else if (scalar)
            {
                if (value == ',' || value == '}' || value == ']' || isWhitespace(value))
                {
                    region = SUFFIX;
                }
                else
                {
                    index++;
                }
            }
            else
            {
                if (value == '"')
                {
                    inString = true;
                }
                else if (value == '{' || value == '[')
                {
                    depth++;
                }
                else if (value == '}' || value == ']')
                {
                    depth--;
                    region = depth == 0 ? SUFFIX : region;
                }

                index++;
            }
        }

        return index;
    }

    private void prefix(
        byte value)
    {
        switch (state)
        {
        case STATE_START:
            state = value == '{' ? STATE_KEY_OPEN : state;
            break;
        case STATE_KEY_OPEN:
            keyOpen(value);
            break;
        case STATE_KEY:
            key(value);
            break;
        case STATE_COLON:
            state = value == ':' ? STATE_VALUE : state;
            break;
        case STATE_VALUE:
            valueOpen(value);
            break;
        case STATE_SKIP:
            skip(value);
            break;
        case STATE_AFTER:
            state = value == ',' ? STATE_KEY_OPEN : value == '}' ? STATE_END : state;
            break;
        default:
            break;
        }
    }

    private void keyOpen(
        byte value)
    {
        if (value == '"')
        {
            state = STATE_KEY;
            keyLength = 0;
            keyMatches = true;
            escaped = false;
        }
        else if (value == '}')
        {
            state = STATE_END;
        }
    }

    private void key(
        byte value)
    {
        if (escaped)
        {
            keyMatches = false;
            escaped = false;
        }
        else if (value == '\\')
        {
            escaped = true;
        }
        else if (value == '"')
        {
            keyMatches &= keyLength == key.length;
            state = STATE_COLON;
        }
        else
        {
            keyMatches &= keyLength < key.length && value == key[keyLength];
            keyLength++;
        }
    }

    private void valueOpen(
        byte value)
    {
        if (keyMatches && (quoted ? value == '"' : !isWhitespace(value)))
        {
            region = VALUE;
            escaped = false;
            valueStarted = false;
        }
        else if (value == '"' || value == '{' || value == '[')
        {
            inString = value == '"';
            depth = inString ? 0 : 1;
            escaped = false;
            state = STATE_SKIP;
        }
        else if (!isWhitespace(value))
        {
            inString = false;
            depth = 0;
            state = STATE_SKIP;
        }
    }

    private void skip(
        byte value)
    {
        if (inString)
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
                inString = false;
                state = depth == 0 ? STATE_AFTER : STATE_SKIP;
            }
        }
        else if (value == '"')
        {
            inString = true;
        }
        else if (value == '{' || value == '[')
        {
            depth++;
        }
        else if (depth > 0 && (value == '}' || value == ']'))
        {
            depth--;
            state = depth == 0 ? STATE_AFTER : STATE_SKIP;
        }
        else if (depth == 0 && value == ',')
        {
            state = STATE_KEY_OPEN;
        }
        else if (depth == 0 && value == '}')
        {
            state = STATE_END;
        }
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }
}
