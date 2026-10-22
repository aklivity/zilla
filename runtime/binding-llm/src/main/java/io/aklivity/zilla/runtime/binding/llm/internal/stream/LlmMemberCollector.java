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

import java.util.function.Supplier;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class LlmMemberCollector implements LlmMembersOutput.Members
{
    private static final DirectBufferEx COMMA = new UnsafeBufferEx(",".getBytes(UTF_8));

    private static final int KEY_WAIT = 0;
    private static final int KEY = 1;
    private static final int COLON = 2;
    private static final int VALUE_WAIT = 3;
    private static final int PASS = 4;
    private static final int HOLD = 5;
    private static final int TAIL = 6;
    private static final int OVERFLOWED = 7;

    private static final int NONE = -1;
    private static final int HEAD_CAPACITY = 64;
    private static final int RECORD_LENGTH_OFFSET = Byte.BYTES;
    private static final int RECORD_HEADER = Byte.BYTES + Integer.BYTES;

    private final String[] containers;
    private final LlmMembersOutput.Members members;
    private final Supplier<MutableDirectBufferEx> store;
    private final byte[] head;
    private final DirectBufferEx headBuffer;

    private int state;
    private int headLength;
    private int keyLength;
    private int depth;
    private boolean inString;
    private boolean escaped;
    private boolean emitted;
    private int container;
    private int recordOffset;
    private int heldLength;
    private String overflow;

    LlmMemberCollector(
        String[] containers,
        LlmMembersOutput.Members members,
        Supplier<MutableDirectBufferEx> store)
    {
        this.containers = containers;
        this.members = members;
        this.store = store;
        this.head = new byte[HEAD_CAPACITY];
        this.headBuffer = new UnsafeBufferEx(head);
        this.state = KEY_WAIT;
    }

    void begin()
    {
        if (state == HOLD)
        {
            closeRecord();
        }

        if (state != OVERFLOWED)
        {
            state = KEY_WAIT;
        }

        headLength = 0;
        emitted = false;
    }

    void reset()
    {
        state = KEY_WAIT;
        headLength = 0;
        emitted = false;
        heldLength = 0;
        overflow = null;
    }

    boolean overflowed()
    {
        return state == OVERFLOWED;
    }

    String overflow()
    {
        return overflow;
    }

    int held()
    {
        return heldLength;
    }

    boolean holds(
        int index)
    {
        boolean holds = false;

        if (heldLength > 0)
        {
            final DirectBufferEx held = store.get();
            int offset = 0;

            while (!holds && offset < heldLength)
            {
                holds = held.getByte(offset) == index;
                offset += RECORD_HEADER + held.getInt(offset + RECORD_LENGTH_OFFSET);
            }
        }

        return holds;
    }

    void drain(
        int index,
        LlmMembersOutput.Members sink)
    {
        if (heldLength > 0)
        {
            final DirectBufferEx held = store.get();
            int offset = 0;

            while (offset < heldLength)
            {
                final int length = held.getInt(offset + RECORD_LENGTH_OFFSET);

                if (held.getByte(offset) == index && length > 0)
                {
                    sink.put(held, offset + RECORD_HEADER, length);
                }

                offset += RECORD_HEADER + length;
            }
        }
    }

    @Override
    public void put(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final int limit = offset + length;
        int runStart = offset;

        for (int index = offset; index < limit; index++)
        {
            final byte value = buffer.getByte(index);

            switch (state)
            {
            case KEY_WAIT:
                if (value == '"')
                {
                    headLength = 0;
                    escaped = false;
                    head[headLength++] = value;
                    state = KEY;
                }
                break;
            case KEY:
                if (headLength == HEAD_CAPACITY)
                {
                    spill();
                    runStart = index;
                    index--;
                }
                else
                {
                    head[headLength++] = value;

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
                        keyLength = headLength;
                        state = COLON;
                    }
                }
                break;
            case COLON:
                if (headLength == HEAD_CAPACITY || !isWhitespace(value) && value != ':')
                {
                    spill();
                    runStart = index;
                    index--;
                }
                else
                {
                    head[headLength++] = value;
                    state = value == ':' ? VALUE_WAIT : COLON;
                }
                break;
            case VALUE_WAIT:
                if (isWhitespace(value) && headLength < HEAD_CAPACITY)
                {
                    head[headLength++] = value;
                }
                else
                {
                    container = value == '{' ? containerOf() : NONE;

                    if (container != NONE)
                    {
                        openRecord();
                        runStart = index + 1;
                    }
                    else
                    {
                        spill();
                        runStart = index;
                        index--;
                    }
                }
                break;
            case PASS:
                if (onValueByte(value) && depth == 0)
                {
                    if (index > runStart)
                    {
                        members.put(buffer, runStart, index - runStart);
                    }

                    state = KEY_WAIT;
                }
                break;
            case HOLD:
                onValueByte(value);

                if (depth == 0)
                {
                    hold(buffer, runStart, index);
                    closeRecord();
                    state = state == OVERFLOWED ? OVERFLOWED : TAIL;
                }
                break;
            case TAIL:
                if (value == ',')
                {
                    state = KEY_WAIT;
                }
                break;
            default:
                break;
            }
        }

        if (state == PASS && runStart < limit)
        {
            members.put(buffer, runStart, limit - runStart);
        }
        else if (state == HOLD && runStart < limit)
        {
            hold(buffer, runStart, limit);
        }
    }

    private boolean onValueByte(
        byte value)
    {
        boolean separator = false;

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
            }
        }
        else
        {
            switch (value)
            {
            case '"':
                inString = true;
                break;
            case '{':
            case '[':
                depth++;
                break;
            case '}':
            case ']':
                depth--;
                break;
            case ',':
                separator = true;
                break;
            default:
                break;
            }
        }

        return separator;
    }

    private void spill()
    {
        if (emitted)
        {
            members.put(COMMA, 0, 1);
        }

        members.put(headBuffer, 0, headLength);
        emitted = true;
        depth = 0;
        inString = state == KEY;
        state = PASS;
    }

    private int containerOf()
    {
        int found = NONE;

        for (int index = 0; found == NONE && index < containers.length; index++)
        {
            final String name = containers[index];
            boolean matches = keyLength - 2 == name.length();

            for (int position = 0; matches && position < name.length(); position++)
            {
                matches = head[1 + position] == name.charAt(position);
            }

            found = matches ? index : NONE;
        }

        return found;
    }

    private void openRecord()
    {
        final MutableDirectBufferEx held = store.get();

        if (held != null && heldLength + RECORD_HEADER <= held.capacity())
        {
            held.putByte(heldLength, (byte) container);
            held.putInt(heldLength + RECORD_LENGTH_OFFSET, 0);
            recordOffset = heldLength;
            heldLength += RECORD_HEADER;
            depth = 1;
            inString = false;
            escaped = false;
            state = HOLD;
        }
        else
        {
            exhaust();
        }
    }

    private void hold(
        DirectBufferEx buffer,
        int from,
        int to)
    {
        final int length = to - from;

        if (length > 0)
        {
            final MutableDirectBufferEx held = store.get();

            if (heldLength + length <= held.capacity())
            {
                held.putBytes(heldLength, buffer, from, length);
                heldLength += length;
            }
            else
            {
                heldLength = recordOffset;
                exhaust();
            }
        }
    }

    private void closeRecord()
    {
        if (state != OVERFLOWED)
        {
            store.get().putInt(recordOffset + RECORD_LENGTH_OFFSET, heldLength - recordOffset - RECORD_HEADER);
        }
    }

    private void exhaust()
    {
        overflow = containers[container];
        state = OVERFLOWED;
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }
}
