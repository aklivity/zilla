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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.response;

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class A2aEventScanner
{
    static final String ERROR_MALFORMED = "Malformed event";
    static final String ERROR_TOO_LARGE = "Event too large";

    private static final int DEPTH_MAX = 64;
    private static final int SCALAR_MAX = 8;
    private static final String STATE_PREFIX = "TASK_STATE_";

    private enum Lex
    {
        VALUE,
        ARRAY_START,
        OBJECT_START,
        KEY,
        COLON,
        AFTER_VALUE,
        KEY_STRING,
        VALUE_STRING,
        TEXT_STRING,
        SCALAR,
        CAPTURE,
        DONE,
        FAILED
    }

    private enum Ctx
    {
        ROOT,
        TASK,
        MESSAGE_EVENT,
        STATUS_UPDATE,
        ARTIFACT_UPDATE,
        STATUS,
        MESSAGE,
        ARTIFACTS,
        ARTIFACT,
        PARTS,
        PART,
        IGNORED
    }

    private enum Want
    {
        NONE,
        TASK_ID,
        CONTEXT_ID,
        STATE,
        MESSAGE_ID,
        ARTIFACT_ID,
        TEXT,
        DATA,
        URL,
        RAW,
        MEDIA_TYPE,
        FILENAME,
        APPEND,
        LAST_CHUNK
    }

    private enum Entity
    {
        NONE,
        PENDING,
        STARTED
    }

    private enum Capture
    {
        UNSET,
        STRING,
        COMPOSITE,
        SCALAR
    }

    private final A2aScanSink sink;
    private final int maximum;
    private final boolean content;
    private final MutableDirectBufferEx memberBuffer;
    private final MutableDirectBufferEx textBuffer;
    private final MutableDirectBufferEx dataBuffer;
    private final Ctx[] contexts = new Ctx[DEPTH_MAX];
    private final boolean[] arrays = new boolean[DEPTH_MAX];

    private Lex lex = Lex.VALUE;
    private int depth;
    private String member;
    private Want want = Want.NONE;
    private Entity entity = Entity.NONE;
    private A2aMessageScope entityScope;
    private String entityId;
    private String partUrl;
    private String partRaw;
    private String partMediaType;
    private String partFilename;
    private int memberLength;
    private int textLength;
    private int dataLength;
    private boolean textFirst;
    private boolean escaped;
    private int unicodeDigits;
    private int unicodeValue;
    private int highSurrogate;
    private Capture capture = Capture.UNSET;
    private int captureDepth;
    private boolean captureInString;
    private boolean captureEscaped;

    A2aEventScanner(
        A2aScanSink sink,
        int maximum,
        boolean content)
    {
        this.sink = sink;
        this.maximum = maximum;
        this.content = content;
        this.memberBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.textBuffer = new UnsafeBufferEx(new byte[content ? maximum : 0]);
        this.dataBuffer = new UnsafeBufferEx(new byte[content ? maximum : 0]);
    }

    void reset()
    {
        lex = Lex.VALUE;
        depth = 0;
        member = null;
        want = Want.NONE;
        entity = Entity.NONE;
        entityScope = null;
        entityId = null;
        clearPart();
        memberLength = 0;
        textLength = 0;
        textFirst = false;
        clearEscape();
        capture = Capture.UNSET;
    }

    boolean complete()
    {
        return lex == Lex.DONE;
    }

    boolean failed()
    {
        return lex == Lex.FAILED;
    }

    void scan(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        while (progress < limit && lex != Lex.FAILED)
        {
            if (step(buffer.getByte(progress)))
            {
                progress++;
            }
        }

        if (lex == Lex.TEXT_STRING)
        {
            emitText(false);
        }
    }

    private boolean step(
        byte octet)
    {
        boolean consumed = true;

        switch (lex)
        {
        case KEY_STRING:
        case VALUE_STRING:
        case TEXT_STRING:
            stringOctet(octet);
            break;
        case SCALAR:
            consumed = scalarOctet(octet);
            break;
        case CAPTURE:
            consumed = captureOctet(octet);
            break;
        default:
            consumed = structuralOctet(octet);
            break;
        }

        return consumed;
    }

    private boolean structuralOctet(
        byte octet)
    {
        boolean consumed = true;

        if (!isWhitespace(octet))
        {
            switch (lex)
            {
            case OBJECT_START:
                if (octet == '}')
                {
                    closeContainer(octet);
                }
                else
                {
                    beginKey(octet);
                }
                break;
            case KEY:
                beginKey(octet);
                break;
            case COLON:
                if (octet == ':')
                {
                    lex = Lex.VALUE;
                }
                else
                {
                    malformed(ERROR_MALFORMED);
                }
                break;
            case ARRAY_START:
                if (octet == ']')
                {
                    closeContainer(octet);
                }
                else
                {
                    consumed = beginValue(octet);
                }
                break;
            case VALUE:
                consumed = beginValue(octet);
                break;
            case AFTER_VALUE:
                afterValue(octet);
                break;
            default:
                malformed(ERROR_MALFORMED);
                break;
            }
        }

        return consumed;
    }

    private void beginKey(
        byte octet)
    {
        if (octet == '"')
        {
            lex = Lex.KEY_STRING;
            memberLength = 0;
            clearEscape();
        }
        else
        {
            malformed(ERROR_MALFORMED);
        }
    }

    private boolean beginValue(
        byte octet)
    {
        boolean consumed = true;

        if (depth == 0)
        {
            if (octet == '{')
            {
                openContainer(octet);
            }
            else
            {
                malformed(ERROR_MALFORMED);
            }
        }
        else if (want == Want.DATA)
        {
            lex = Lex.CAPTURE;
            capture = Capture.UNSET;
            dataLength = 0;
            consumed = false;
        }
        else if (octet == '{' || octet == '[')
        {
            openContainer(octet);
        }
        else if (octet == '"')
        {
            lex = want == Want.TEXT ? Lex.TEXT_STRING : Lex.VALUE_STRING;
            memberLength = 0;
            textFirst = true;
            clearEscape();
        }
        else
        {
            lex = Lex.SCALAR;
            memberLength = 0;
            consumed = false;
        }

        return consumed;
    }

    private void afterValue(
        byte octet)
    {
        if (octet == ',')
        {
            lex = arrays[depth - 1] ? Lex.VALUE : Lex.KEY;
        }
        else if (octet == '}' || octet == ']')
        {
            closeContainer(octet);
        }
        else
        {
            malformed(ERROR_MALFORMED);
        }
    }

    private void openContainer(
        byte octet)
    {
        final boolean array = octet == '[';
        final Ctx child = depth == 0 ? Ctx.ROOT : childOf(contexts[depth - 1], member, array);

        if (depth == DEPTH_MAX)
        {
            malformed(ERROR_TOO_LARGE);
        }
        else
        {
            contexts[depth] = child;
            arrays[depth] = array;
            depth++;
            member = null;
            want = Want.NONE;
            lex = array ? Lex.ARRAY_START : Lex.OBJECT_START;

            openContext(child);
        }
    }

    private void openContext(
        Ctx child)
    {
        switch (child)
        {
        case TASK:
            sink.kind(A2aEventKind.TASK);
            break;
        case STATUS_UPDATE:
            sink.kind(A2aEventKind.STATUS_UPDATE);
            break;
        case ARTIFACT_UPDATE:
            sink.kind(A2aEventKind.ARTIFACT_UPDATE);
            break;
        case MESSAGE_EVENT:
            sink.kind(A2aEventKind.MESSAGE);
            beginEntity(A2aMessageScope.REPLY);
            break;
        case MESSAGE:
            beginEntity(A2aMessageScope.STATUS);
            break;
        case ARTIFACT:
            beginEntity(null);
            break;
        case PARTS:
            startEntity();
            break;
        case PART:
            clearPart();
            break;
        default:
            break;
        }
    }

    private void closeContainer(
        byte octet)
    {
        final boolean array = octet == ']';

        if (depth == 0 || arrays[depth - 1] != array)
        {
            malformed(ERROR_MALFORMED);
        }
        else
        {
            final Ctx closed = contexts[--depth];

            switch (closed)
            {
            case PART:
                endPart();
                break;
            case MESSAGE:
            case MESSAGE_EVENT:
            case ARTIFACT:
                endEntity();
                break;
            default:
                break;
            }

            member = null;
            want = Want.NONE;

            if (depth == 0)
            {
                lex = Lex.DONE;
                sink.ended();
            }
            else
            {
                lex = Lex.AFTER_VALUE;
            }
        }
    }

    private void beginEntity(
        A2aMessageScope scope)
    {
        entity = Entity.PENDING;
        entityScope = scope;
        entityId = null;
    }

    private void startEntity()
    {
        if (entity == Entity.PENDING)
        {
            if (entityScope != null)
            {
                sink.messageStarted(entityScope, entityId);
            }
            else
            {
                sink.artifactStarted(entityId);
            }

            entity = Entity.STARTED;
        }
    }

    private void endEntity()
    {
        startEntity();

        if (entity == Entity.STARTED)
        {
            if (entityScope != null)
            {
                sink.messageEnded(entityScope);
            }
            else
            {
                sink.artifactEnded();
            }

            entity = Entity.NONE;
        }
    }

    private void endPart()
    {
        if (dataLength > 0)
        {
            sink.data(dataBuffer, 0, dataLength);
        }
        else if (partUrl != null || partRaw != null)
        {
            sink.file(partMediaType, partFilename, partUrl, partRaw);
        }

        clearPart();
    }

    private void clearPart()
    {
        partUrl = null;
        partRaw = null;
        partMediaType = null;
        partFilename = null;
        dataLength = 0;
    }

    private void stringOctet(
        byte octet)
    {
        if (unicodeDigits > 0)
        {
            final int digit = Character.digit(octet, 16);

            if (digit < 0)
            {
                malformed(ERROR_MALFORMED);
            }
            else
            {
                unicodeValue = (unicodeValue << 4) | digit;

                if (--unicodeDigits == 0)
                {
                    emitCodeUnit(unicodeValue);
                }
            }
        }
        else if (escaped)
        {
            escaped = false;
            escapeOctet(octet);
        }
        else if (octet == '\\')
        {
            escaped = true;
        }
        else if (octet == '"')
        {
            endString();
        }
        else
        {
            emitRaw(octet);
        }
    }

    private void escapeOctet(
        byte octet)
    {
        switch (octet)
        {
        case '"':
        case '\\':
        case '/':
            emitRaw(octet);
            break;
        case 'b':
            emitRaw((byte) '\b');
            break;
        case 'f':
            emitRaw((byte) '\f');
            break;
        case 'n':
            emitRaw((byte) '\n');
            break;
        case 'r':
            emitRaw((byte) '\r');
            break;
        case 't':
            emitRaw((byte) '\t');
            break;
        case 'u':
            unicodeDigits = 4;
            unicodeValue = 0;
            break;
        default:
            malformed(ERROR_MALFORMED);
            break;
        }
    }

    private void emitCodeUnit(
        int unit)
    {
        if (Character.isHighSurrogate((char) unit) && highSurrogate == 0)
        {
            highSurrogate = unit;
        }
        else if (Character.isLowSurrogate((char) unit) && highSurrogate != 0)
        {
            final int codePoint = Character.toCodePoint((char) highSurrogate, (char) unit);
            highSurrogate = 0;
            emitCodePoint(codePoint);
        }
        else if (Character.isSurrogate((char) unit) || highSurrogate != 0)
        {
            malformed(ERROR_MALFORMED);
        }
        else
        {
            emitCodePoint(unit);
        }
    }

    private void emitCodePoint(
        int codePoint)
    {
        if (codePoint < 0x80)
        {
            put((byte) codePoint);
        }
        else if (codePoint < 0x800)
        {
            put((byte) (0xC0 | codePoint >> 6));
            put((byte) (0x80 | codePoint & 0x3F));
        }
        else if (codePoint < 0x10000)
        {
            put((byte) (0xE0 | codePoint >> 12));
            put((byte) (0x80 | codePoint >> 6 & 0x3F));
            put((byte) (0x80 | codePoint & 0x3F));
        }
        else
        {
            put((byte) (0xF0 | codePoint >> 18));
            put((byte) (0x80 | codePoint >> 12 & 0x3F));
            put((byte) (0x80 | codePoint >> 6 & 0x3F));
            put((byte) (0x80 | codePoint & 0x3F));
        }
    }

    private void emitRaw(
        byte octet)
    {
        if (highSurrogate != 0)
        {
            malformed(ERROR_MALFORMED);
        }
        else
        {
            put(octet);
        }
    }

    private void put(
        byte octet)
    {
        switch (lex)
        {
        case TEXT_STRING:
            if (textLength + 1 > textBuffer.capacity())
            {
                emitText(false);
            }
            textBuffer.putByte(textLength++, octet);
            break;
        case KEY_STRING:
        case VALUE_STRING:
            if (lex == Lex.KEY_STRING || want != Want.NONE)
            {
                if (memberLength == maximum)
                {
                    malformed(ERROR_TOO_LARGE);
                }
                else
                {
                    memberBuffer.putByte(memberLength++, octet);
                }
            }
            break;
        default:
            break;
        }
    }

    private void endString()
    {
        if (highSurrogate != 0 || escaped)
        {
            malformed(ERROR_MALFORMED);
        }
        else
        {
            switch (lex)
            {
            case KEY_STRING:
                member = memberString();
                want = wantOf(contexts[depth - 1], member);
                lex = Lex.COLON;
                break;
            case TEXT_STRING:
                emitText(true);
                valueEnded();
                break;
            default:
                if (want != Want.NONE)
                {
                    stringValue(want, memberString());
                }
                valueEnded();
                break;
            }
        }
    }

    private void valueEnded()
    {
        member = null;
        want = Want.NONE;
        lex = Lex.AFTER_VALUE;
    }

    private boolean scalarOctet(
        byte octet)
    {
        final boolean delimiter = isWhitespace(octet) || octet == ',' || octet == '}' || octet == ']';

        if (delimiter)
        {
            if (want == Want.APPEND || want == Want.LAST_CHUNK)
            {
                final boolean flag = isTrue();

                if (want == Want.APPEND)
                {
                    sink.append(flag);
                }
                else
                {
                    sink.lastChunk(flag);
                }
            }

            valueEnded();
        }
        else if (memberLength < SCALAR_MAX)
        {
            memberBuffer.putByte(memberLength++, octet);
        }

        return !delimiter;
    }

    private boolean isTrue()
    {
        return memberLength == 4 &&
            memberBuffer.getByte(0) == 't' &&
            memberBuffer.getByte(1) == 'r' &&
            memberBuffer.getByte(2) == 'u' &&
            memberBuffer.getByte(3) == 'e';
    }

    private boolean captureOctet(
        byte octet)
    {
        boolean consumed = true;

        if (capture == Capture.UNSET)
        {
            capture = octet == '"' ? Capture.STRING : octet == '{' || octet == '[' ? Capture.COMPOSITE : Capture.SCALAR;
            captureDepth = 0;
            captureInString = false;
            captureEscaped = false;
        }

        if (capture == Capture.SCALAR)
        {
            if (isWhitespace(octet) || octet == ',' || octet == '}' || octet == ']')
            {
                valueEnded();
                consumed = false;
            }
            else
            {
                capture(octet);
            }
        }
        else
        {
            capture(octet);

            if (captureInString)
            {
                if (captureEscaped)
                {
                    captureEscaped = false;
                }
                else if (octet == '\\')
                {
                    captureEscaped = true;
                }
                else if (octet == '"' && dataLength > 1)
                {
                    captureInString = false;

                    if (capture == Capture.STRING)
                    {
                        valueEnded();
                    }
                }
            }
            else if (octet == '"')
            {
                captureInString = true;
            }
            else if (capture == Capture.COMPOSITE)
            {
                if (octet == '{' || octet == '[')
                {
                    captureDepth++;
                }
                else if (octet == '}' || octet == ']')
                {
                    if (--captureDepth == 0)
                    {
                        valueEnded();
                    }
                }
            }
        }

        return consumed;
    }

    private void capture(
        byte octet)
    {
        if (dataLength == dataBuffer.capacity())
        {
            malformed(ERROR_TOO_LARGE);
        }
        else
        {
            dataBuffer.putByte(dataLength++, octet);
        }
    }

    private void stringValue(
        Want target,
        String value)
    {
        switch (target)
        {
        case TASK_ID:
            sink.taskId(value);
            break;
        case CONTEXT_ID:
            sink.contextId(value);
            break;
        case STATE:
            sink.state(stateOf(value));
            break;
        case MESSAGE_ID:
        case ARTIFACT_ID:
            if (entity == Entity.PENDING)
            {
                entityId = value;
            }
            break;
        case URL:
            partUrl = value;
            break;
        case RAW:
            partRaw = value;
            break;
        case MEDIA_TYPE:
            partMediaType = value;
            break;
        case FILENAME:
            partFilename = value;
            break;
        default:
            break;
        }
    }

    private String memberString()
    {
        return memberBuffer.getStringWithoutLengthUtf8(0, memberLength);
    }

    private void emitText(
        boolean fin)
    {
        final int flags = (textFirst ? INIT : 0) | (fin ? FIN : 0);
        final int length = fin ? textLength : utf8Whole(textBuffer, textLength);

        if (flags != 0 || length > 0)
        {
            sink.text(flags, textBuffer, 0, length);
            textFirst = false;

            final int tail = textLength - length;

            for (int index = 0; index < tail; index++)
            {
                textBuffer.putByte(index, textBuffer.getByte(length + index));
            }

            textLength = tail;
        }
    }

    private void clearEscape()
    {
        escaped = false;
        unicodeDigits = 0;
        unicodeValue = 0;
        highSurrogate = 0;
    }

    private void malformed(
        String message)
    {
        lex = Lex.FAILED;
        sink.malformed(message);
    }

    private Ctx childOf(
        Ctx parent,
        String member,
        boolean array)
    {
        Ctx child = Ctx.IGNORED;

        switch (parent)
        {
        case ROOT:
            child = array ? Ctx.IGNORED : rootOf(member);
            break;
        case TASK:
            child = "status".equals(member) && !array ? Ctx.STATUS :
                "artifacts".equals(member) && array ? Ctx.ARTIFACTS : Ctx.IGNORED;
            break;
        case STATUS_UPDATE:
            child = "status".equals(member) && !array ? Ctx.STATUS : Ctx.IGNORED;
            break;
        case ARTIFACT_UPDATE:
            child = "artifact".equals(member) && !array ? Ctx.ARTIFACT : Ctx.IGNORED;
            break;
        case STATUS:
            child = "message".equals(member) && !array ? Ctx.MESSAGE : Ctx.IGNORED;
            break;
        case MESSAGE:
        case MESSAGE_EVENT:
        case ARTIFACT:
            child = "parts".equals(member) && array ? Ctx.PARTS : Ctx.IGNORED;
            break;
        case ARTIFACTS:
            child = array ? Ctx.IGNORED : Ctx.ARTIFACT;
            break;
        case PARTS:
            child = array ? Ctx.IGNORED : Ctx.PART;
            break;
        default:
            break;
        }

        return child;
    }

    private Ctx rootOf(
        String member)
    {
        Ctx child = Ctx.IGNORED;

        if (member != null)
        {
            switch (member)
            {
            case "task":
                child = Ctx.TASK;
                break;
            case "message":
                child = Ctx.MESSAGE_EVENT;
                break;
            case "statusUpdate":
                child = Ctx.STATUS_UPDATE;
                break;
            case "artifactUpdate":
                child = Ctx.ARTIFACT_UPDATE;
                break;
            default:
                break;
            }
        }

        return child;
    }

    private Want wantOf(
        Ctx parent,
        String member)
    {
        Want want = Want.NONE;

        switch (parent)
        {
        case TASK:
            want = "id".equals(member) ? Want.TASK_ID : "contextId".equals(member) ? Want.CONTEXT_ID : Want.NONE;
            break;
        case STATUS_UPDATE:
            want = "taskId".equals(member) ? Want.TASK_ID : "contextId".equals(member) ? Want.CONTEXT_ID : Want.NONE;
            break;
        case ARTIFACT_UPDATE:
            want = "taskId".equals(member) ? Want.TASK_ID :
                "contextId".equals(member) ? Want.CONTEXT_ID :
                "append".equals(member) ? Want.APPEND :
                "lastChunk".equals(member) ? Want.LAST_CHUNK : Want.NONE;
            break;
        case MESSAGE_EVENT:
            want = "messageId".equals(member) ? Want.MESSAGE_ID :
                "taskId".equals(member) ? Want.TASK_ID :
                "contextId".equals(member) ? Want.CONTEXT_ID : Want.NONE;
            break;
        case STATUS:
            want = "state".equals(member) ? Want.STATE : Want.NONE;
            break;
        case MESSAGE:
            want = "messageId".equals(member) ? Want.MESSAGE_ID : Want.NONE;
            break;
        case ARTIFACT:
            want = "artifactId".equals(member) ? Want.ARTIFACT_ID : Want.NONE;
            break;
        case PART:
            want = partWantOf(member);
            break;
        default:
            break;
        }

        return want;
    }

    private Want partWantOf(
        String member)
    {
        Want want = Want.NONE;

        switch (member)
        {
        case "text":
            want = content ? Want.TEXT : Want.NONE;
            break;
        case "data":
            want = content ? Want.DATA : Want.NONE;
            break;
        case "url":
            want = Want.URL;
            break;
        case "raw":
            want = Want.RAW;
            break;
        case "mediaType":
            want = Want.MEDIA_TYPE;
            break;
        case "filename":
            want = Want.FILENAME;
            break;
        default:
            break;
        }

        return want;
    }

    private static A2aTaskState stateOf(
        String value)
    {
        A2aTaskState state = A2aTaskState.UNSPECIFIED;

        try
        {
            state = A2aTaskState.valueOf(value.startsWith(STATE_PREFIX) ? value.substring(STATE_PREFIX.length()) : value);
        }
        catch (IllegalArgumentException ex)
        {
            state = A2aTaskState.UNSPECIFIED;
        }

        return state;
    }

    private static int utf8Whole(
        MutableDirectBufferEx buffer,
        int length)
    {
        int tail = 0;

        for (int back = 1; back <= 3 && back <= length; back++)
        {
            final byte octet = buffer.getByte(length - back);

            if ((octet & 0xC0) != 0x80)
            {
                final int needed = (octet & 0x80) == 0 ? 1 :
                    (octet & 0xE0) == 0xC0 ? 2 :
                    (octet & 0xF0) == 0xE0 ? 3 :
                    (octet & 0xF8) == 0xF0 ? 4 : 1;

                tail = needed > back ? back : 0;
                break;
            }
        }

        return length - tail;
    }

    private static boolean isWhitespace(
        byte octet)
    {
        return octet == ' ' || octet == '\n' || octet == '\r' || octet == '\t';
    }
}
