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

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public final class AguiRunInputEncoder
{
    public enum Status
    {
        PENDING,
        VALID,
        MISMATCHED,
        MALFORMED
    }

    private enum State
    {
        START,
        FIRST_MEMBER,
        MEMBER,
        KEY,
        COLON,
        VALUE,
        VALUE_BODY,
        SEPARATOR,
        DONE,
        MISMATCHED,
        MALFORMED
    }

    private enum Identifier
    {
        NONE,
        THREAD_ID,
        RUN_ID,
        PARENT_RUN_ID
    }

    private static final int KEY_MAX = 16;
    private static final int CAPTURE_MAX = 256;
    private static final byte[] KEY_THREAD_ID = "threadId".getBytes(UTF_8);
    private static final byte[] KEY_RUN_ID = "runId".getBytes(UTF_8);
    private static final byte[] KEY_PARENT_RUN_ID = "parentRunId".getBytes(UTF_8);
    private static final byte[] NULL = "null".getBytes(UTF_8);

    private final String threadId;
    private final String runId;
    private final String parentRunId;
    private final AguiValueExtent extent = new AguiValueExtent();
    private final MutableDirectBufferEx keyBuffer = new UnsafeBufferEx(new byte[KEY_MAX]);
    private final MutableDirectBufferEx captureBuffer = new UnsafeBufferEx(new byte[CAPTURE_MAX]);
    private final MutableDirectBufferEx valueBuffer = new UnsafeBufferEx(new byte[CAPTURE_MAX]);

    private State state = State.START;
    private Identifier capture = Identifier.NONE;
    private int keyLength;
    private boolean keyEscaped;
    private int captureLength;
    private int captureTotal;
    private int seenThreadId;
    private int seenRunId;
    private int seenParentRunId;

    public AguiRunInputEncoder(
        String threadId,
        String runId,
        String parentRunId)
    {
        this.threadId = threadId;
        this.runId = runId;
        this.parentRunId = parentRunId;
    }

    public Status encode(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        while (progress < limit && state != State.MISMATCHED && state != State.MALFORMED)
        {
            if (state == State.VALUE_BODY)
            {
                progress += encodeValue(buffer, progress, limit);
            }
            else
            {
                progress += encodeStructure(buffer.getByte(progress));
            }
        }

        return status();
    }

    public Status finish()
    {
        if (state == State.DONE)
        {
            final boolean identified = seenThreadId == 1 && seenRunId == 1;
            final boolean parented = parentRunId == null || seenParentRunId == 1;

            state = identified && parented ? State.DONE : State.MISMATCHED;
        }
        else if (state != State.MISMATCHED)
        {
            state = State.MALFORMED;
        }

        return state == State.DONE ? Status.VALID : status();
    }

    private Status status()
    {
        final Status status;

        switch (state)
        {
        case MISMATCHED:
            status = Status.MISMATCHED;
            break;
        case MALFORMED:
            status = Status.MALFORMED;
            break;
        default:
            status = Status.PENDING;
            break;
        }

        return status;
    }

    private int encodeStructure(
        byte octet)
    {
        int consumed = 1;

        switch (state)
        {
        case START:
            if (octet == '{')
            {
                state = State.FIRST_MEMBER;
            }
            else if (!isWhitespace(octet))
            {
                state = State.MALFORMED;
            }
            break;
        case FIRST_MEMBER:
            if (octet == '}')
            {
                state = State.DONE;
            }
            else
            {
                encodeMember(octet);
            }
            break;
        case MEMBER:
            encodeMember(octet);
            break;
        case KEY:
            encodeKey(octet);
            break;
        case COLON:
            if (octet == ':')
            {
                state = State.VALUE;
            }
            else if (!isWhitespace(octet))
            {
                state = State.MALFORMED;
            }
            break;
        case VALUE:
            if (!isWhitespace(octet))
            {
                extent.reset();
                capture = identifier();
                captureLength = 0;
                captureTotal = 0;
                state = State.VALUE_BODY;
                consumed = 0;
            }
            break;
        case SEPARATOR:
            if (octet == ',')
            {
                state = State.MEMBER;
            }
            else if (octet == '}')
            {
                state = State.DONE;
            }
            else if (!isWhitespace(octet))
            {
                state = State.MALFORMED;
            }
            break;
        default:
            if (!isWhitespace(octet))
            {
                state = State.MALFORMED;
            }
            break;
        }

        return consumed;
    }

    private void encodeMember(
        byte octet)
    {
        if (octet == '"')
        {
            keyLength = 0;
            keyEscaped = false;
            state = State.KEY;
        }
        else if (!isWhitespace(octet))
        {
            state = State.MALFORMED;
        }
    }

    private void encodeKey(
        byte octet)
    {
        if (!keyEscaped && octet == '"')
        {
            state = State.COLON;
        }
        else
        {
            keyEscaped = !keyEscaped && octet == '\\';

            if (keyLength < KEY_MAX)
            {
                keyBuffer.putByte(keyLength, octet);
            }

            keyLength++;
        }
    }

    private int encodeValue(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int consumed = extent.advance(buffer, offset, limit);

        if (consumed == AguiValueExtent.MALFORMED)
        {
            state = State.MALFORMED;
        }
        else
        {
            if (capture != Identifier.NONE)
            {
                final int length = Math.min(consumed, CAPTURE_MAX - captureLength);
                captureBuffer.putBytes(captureLength, buffer, offset, length);
                captureLength += length;
                captureTotal += consumed;
            }

            if (extent.complete())
            {
                encodeIdentifier();
                state = state == State.VALUE_BODY ? State.SEPARATOR : state;
            }
        }

        return Math.max(consumed, 0);
    }

    private void encodeIdentifier()
    {
        if (capture != Identifier.NONE && captureTotal > CAPTURE_MAX)
        {
            state = State.MISMATCHED;
        }
        else if (capture != Identifier.NONE)
        {
            final String value = identified();

            if (state != State.MALFORMED)
            {
                switch (capture)
                {
                case THREAD_ID:
                    state = threadId.equals(value) ? state : State.MISMATCHED;
                    seenThreadId = 1;
                    break;
                case RUN_ID:
                    state = runId.equals(value) ? state : State.MISMATCHED;
                    seenRunId = 1;
                    break;
                default:
                    state = (parentRunId == null ? value == null : parentRunId.equals(value)) ? state : State.MISMATCHED;
                    seenParentRunId = value != null ? 1 : 0;
                    break;
                }
            }
        }
    }

    private String identified()
    {
        String value = null;

        if (extent.string())
        {
            final int length = AguiJsonUnescape.unescape(captureBuffer, 1, captureLength - 1, valueBuffer, 0);

            value = valueBuffer.getStringWithoutLengthUtf8(0, length);
        }
        else if (capture != Identifier.PARENT_RUN_ID || !isNull())
        {
            state = State.MALFORMED;
        }

        return value;
    }

    private boolean isNull()
    {
        boolean matches = captureLength == NULL.length;

        for (int index = 0; matches && index < NULL.length; index++)
        {
            matches = captureBuffer.getByte(index) == NULL[index];
        }

        return matches;
    }

    private Identifier identifier()
    {
        final Identifier identifier;

        if (keyLength == KEY_THREAD_ID.length && keyEquals(KEY_THREAD_ID))
        {
            identifier = Identifier.THREAD_ID;
        }
        else if (keyLength == KEY_RUN_ID.length && keyEquals(KEY_RUN_ID))
        {
            identifier = Identifier.RUN_ID;
        }
        else if (keyLength == KEY_PARENT_RUN_ID.length && keyEquals(KEY_PARENT_RUN_ID))
        {
            identifier = Identifier.PARENT_RUN_ID;
        }
        else
        {
            identifier = Identifier.NONE;
        }

        return identifier;
    }

    private boolean keyEquals(
        byte[] name)
    {
        boolean equal = true;

        for (int index = 0; equal && index < name.length; index++)
        {
            equal = keyBuffer.getByte(index) == name[index];
        }

        return equal;
    }

    private static boolean isWhitespace(
        byte octet)
    {
        return octet == ' ' || octet == '\n' || octet == '\r' || octet == '\t';
    }
}
