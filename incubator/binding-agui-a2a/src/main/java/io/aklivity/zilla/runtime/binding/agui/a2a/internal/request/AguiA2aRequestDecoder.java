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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.request;

import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aText.matches;

import java.util.Map;

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Append;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Kind;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;

public final class AguiA2aRequestDecoder
{
    public enum Status
    {
        PENDING,
        COMPLETE,
        REJECTED
    }

    private enum Identifier
    {
        THREAD_ID,
        RUN_ID,
        PARENT_RUN_ID
    }

    private enum Role
    {
        UNKNOWN,
        USER,
        OTHER
    }

    @FunctionalInterface
    private interface Decoder
    {
        void decode();
    }

    private static final int TEXT_MAX = 256;
    private static final CharSequence OVERFLOW = "";
    private static final int SEEN_THREAD_ID = 1;
    private static final int SEEN_RUN_ID = 2;
    private static final int SEEN_PARENT_RUN_ID = 4;
    private static final int SEEN_REQUIRED = SEEN_THREAD_ID | SEEN_RUN_ID;

    private static final String KEY_THREAD_ID = "threadId";
    private static final String KEY_RUN_ID = "runId";
    private static final String KEY_PARENT_RUN_ID = "parentRunId";
    private static final String KEY_MESSAGES = "messages";
    private static final String KEY_STATE = "state";
    private static final String KEY_CONTEXT = "context";
    private static final String KEY_FORWARDED_PROPS = "forwardedProps";
    private static final String KEY_RESUME = "resume";
    private static final String KEY_ROLE = "role";
    private static final String KEY_RESERVED = "agui";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_TOOL = "tool";

    private final Decoder decodeStart = this::decodeStart;
    private final Decoder decodeMembers = this::decodeMembers;
    private final Decoder decodeId = this::decodeId;
    private final Decoder decodeCapture = this::decodeCapture;
    private final Decoder decodeMessages = this::decodeMessages;
    private final Decoder decodeMessageNext = this::decodeMessageNext;
    private final Decoder decodeMessageCapture = this::decodeMessageCapture;
    private final Decoder decodeMessageDrain = this::decodeMessageDrain;
    private final Decoder decodeMessageRole = this::decodeMessageRole;
    private final Decoder decodeDone = this::decodeDone;
    private final Decoder decodeReject = this::decodeReject;
    private final Decoder decodeIgnore = this::decodeIgnore;

    private final JsonParserEx parser;
    private final String threadId;
    private final String runId;
    private final String parentRunId;
    private final AguiA2aRetention retention;
    private final AguiA2aRetained retained;
    private final int textMax;
    private final StringBuilder text;

    private Decoder decoder = decodeStart;
    private Decoder messageDecoder = decodeMessageCapture;
    private AguiA2aRejection rejection;
    private DirectBufferEx window;
    private int windowLimit;
    private int seen;
    private int textLength;
    private CharSequence current;
    private Identifier identifier;
    private Kind captureKind;
    private int captureFrom;
    private int captureMark;
    private int captureEvents;
    private JsonEvent captureFirst;
    private int depth;
    private int historyMark;
    private int historyCount;
    private int messageMark;
    private Role messageRole = Role.UNKNOWN;
    private int oversize;
    private int rejectDepth;
    private int turns;

    public AguiA2aRequestDecoder(
        long streamId,
        String threadId,
        String runId,
        String parentRunId,
        AguiA2aRetention retention)
    {
        this.parser = JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
        this.threadId = threadId;
        this.runId = runId;
        this.parentRunId = parentRunId;
        this.retention = retention;
        this.retained = new AguiA2aRetained(retention, streamId);
        this.textMax = Math.max(TEXT_MAX, Math.max(threadId.length(), runId.length()));
        this.text = new StringBuilder(textMax);
    }

    public int decode(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        try
        {
            window = buffer;
            windowLimit = limit;
            captureFrom = offset;
            parser.wrap(buffer, offset, limit, last);

            while (decoder != decodeDone && decoder != decodeIgnore && parser.hasNextEvent())
            {
                decoder.decode();
            }

            flushCapture();

            if (last && decoder != decodeDone && decoder != decodeIgnore)
            {
                reject(AguiA2aRejection.MALFORMED);
            }
        }
        catch (JsonException | IllegalStateException ex)
        {
            reject(AguiA2aRejection.MALFORMED);
        }

        return decoder == decodeDone || decoder == decodeIgnore ? limit : limit - parser.remaining();
    }

    public Status status()
    {
        return decoder == decodeDone ? Status.COMPLETE :
            decoder == decodeIgnore ? Status.REJECTED :
            Status.PENDING;
    }

    public AguiA2aRejection rejection()
    {
        return rejection;
    }

    public AguiA2aRetained retained()
    {
        return retained;
    }

    public boolean priorTurn()
    {
        return turns > 0;
    }

    private void decodeStart()
    {
        switch (next())
        {
        case START_DOCUMENT:
            break;
        case START_OBJECT:
            decoder = decodeMembers;
            break;
        default:
            reject(AguiA2aRejection.MALFORMED);
            break;
        }
    }

    private void decodeMembers()
    {
        switch (next())
        {
        case KEY_NAME:
            decodeKey(current);
            break;
        case END_OBJECT:
            decodeFinish();
            break;
        default:
            reject(AguiA2aRejection.MALFORMED);
            break;
        }
    }

    private void decodeKey(
        CharSequence name)
    {
        if (name == null)
        {
            decoder = decodeMembers;
        }
        else if (matches(name, KEY_THREAD_ID))
        {
            identifier = Identifier.THREAD_ID;
            decoder = decodeId;
        }
        else if (matches(name, KEY_RUN_ID))
        {
            identifier = Identifier.RUN_ID;
            decoder = decodeId;
        }
        else if (matches(name, KEY_PARENT_RUN_ID))
        {
            identifier = Identifier.PARENT_RUN_ID;
            decoder = decodeId;
        }
        else if (matches(name, KEY_MESSAGES))
        {
            decoder = decodeMessages;
        }
        else if (matches(name, KEY_STATE))
        {
            beginCapture(Kind.STATE);
        }
        else if (matches(name, KEY_CONTEXT))
        {
            beginCapture(Kind.CONTEXT);
        }
        else if (matches(name, KEY_FORWARDED_PROPS))
        {
            beginCapture(Kind.FORWARDED_PROPS);
        }
        else if (matches(name, KEY_RESUME))
        {
            beginCapture(Kind.RESUME);
        }
        else
        {
            parser.skipValue();
        }
    }

    private void beginCapture(
        Kind kind)
    {
        captureKind = kind;
        captureFrom = position();
        captureMark = retained.mark();
        depth = 0;
        captureEvents = 0;
        captureFirst = null;
        decoder = decodeCapture;
    }

    private void decodeId()
    {
        final String expected = switch (identifier)
        {
        case THREAD_ID -> threadId;
        case RUN_ID -> runId;
        case PARENT_RUN_ID -> parentRunId;
        };

        switch (next())
        {
        case VALUE_STRING:
            final CharSequence value = current;
            if (value != null)
            {
                if (expected != null && matches(value, expected))
                {
                    seen |= switch (identifier)
                    {
                    case THREAD_ID -> SEEN_THREAD_ID;
                    case RUN_ID -> SEEN_RUN_ID;
                    case PARENT_RUN_ID -> SEEN_PARENT_RUN_ID;
                    };
                    decoder = decodeMembers;
                }
                else
                {
                    reject(AguiA2aRejection.INVALID_RUN);
                }
            }
            break;
        case VALUE_NULL:
            if (identifier == Identifier.PARENT_RUN_ID && expected == null)
            {
                decoder = decodeMembers;
            }
            else
            {
                reject(identifier == Identifier.PARENT_RUN_ID ? AguiA2aRejection.INVALID_RUN : AguiA2aRejection.MALFORMED);
            }
            break;
        default:
            reject(AguiA2aRejection.MALFORMED);
            break;
        }
    }

    private void decodeCapture()
    {
        final JsonEvent event = next();

        if (captureEvents++ == 0)
        {
            captureFirst = event;
        }

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        case KEY_NAME:
            if (captureKind == Kind.FORWARDED_PROPS && depth == 1)
            {
                final CharSequence key = current;
                if (key != null && matches(key, KEY_RESERVED))
                {
                    reject(AguiA2aRejection.RESERVED_KEY);
                }
            }
            break;
        default:
            break;
        }

        if (decoder == decodeCapture && depth == 0 && event != JsonEvent.KEY_NAME && !parser.deferredBytes())
        {
            decodeCaptured();
        }
    }

    private void decodeCaptured()
    {
        final boolean container = captureFirst == JsonEvent.START_OBJECT || captureFirst == JsonEvent.START_ARRAY;
        final boolean empty = captureFirst == JsonEvent.VALUE_NULL || container && captureEvents == 2;
        final Append result = empty ? Append.OK : retain(captureFrom, position());

        if (result != Append.OK)
        {
            rejectAppend(result, 1);
        }
        else
        {
            if (empty)
            {
                retained.truncate(captureMark, retained.count());
            }
            else if (captureKind == Kind.FORWARDED_PROPS && captureFirst == JsonEvent.START_OBJECT)
            {
                retained.entryMembers(Kind.FORWARDED_PROPS, captureMark);
            }
            else if (captureKind == Kind.FORWARDED_PROPS)
            {
                retained.entry(Kind.FORWARDED_VALUE, captureMark);
            }
            else
            {
                retained.entry(captureKind, captureMark);
            }

            decoder = decodeMembers;
        }
    }

    private void decodeMessages()
    {
        if (parser.hasNextEvent())
        {
            switch (next())
            {
            case START_ARRAY:
                historyMark = retained.mark();
                historyCount = retained.count();
                decoder = decodeMessageNext;
                break;
            case VALUE_NULL:
                decoder = decodeMembers;
                break;
            default:
                reject(AguiA2aRejection.MALFORMED);
                break;
            }
        }
    }

    private void decodeMessageNext()
    {
        switch (next())
        {
        case START_OBJECT:
            decodeMessageStart();
            break;
        case END_ARRAY:
            if (oversize > 0)
            {
                rejectTooLarge(1);
            }
            else
            {
                decoder = decodeMembers;
            }
            break;
        default:
            reject(AguiA2aRejection.MALFORMED);
            break;
        }
    }

    private void decodeMessageStart()
    {
        captureFrom = position() - 1;
        messageMark = retained.mark();
        messageRole = Role.UNKNOWN;
        depth = 1;
        decoder = decodeMessageCapture;
    }

    private void decodeMessageCapture()
    {
        decodeMessageEvent(next(), decodeMessageCapture);
    }

    private void decodeMessageDrain()
    {
        decodeMessageEvent(next(), decodeMessageDrain);
    }

    private void decodeMessageEvent(
        JsonEvent event,
        Decoder self)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            if (depth == 0)
            {
                decodeMessageEnd();
            }
            break;
        case KEY_NAME:
            if (depth == 1)
            {
                final CharSequence key = current;
                if (key != null && matches(key, KEY_ROLE))
                {
                    messageDecoder = self;
                    decoder = decodeMessageRole;
                }
            }
            break;
        default:
            break;
        }
    }

    private void decodeMessageRole()
    {
        final JsonEvent event = next();
        final CharSequence value = event == JsonEvent.VALUE_STRING ? current : OVERFLOW;

        if (value != null)
        {
            if (event == JsonEvent.VALUE_STRING && matches(value, ROLE_USER))
            {
                messageRole = Role.USER;
                decoder = messageDecoder;
            }
            else
            {
                messageRole = Role.OTHER;

                if (event == JsonEvent.VALUE_STRING && (matches(value, ROLE_ASSISTANT) || matches(value, ROLE_TOOL)))
                {
                    retained.truncate(historyMark, historyCount);
                    oversize = 0;
                    turns++;
                }
                else if (messageDecoder == decodeMessageCapture)
                {
                    retained.truncate(messageMark, retained.count());
                }

                decoder = decodeMessageDrain;
            }
        }
    }

    private void decodeMessageEnd()
    {
        if (decoder == decodeMessageCapture)
        {
            final Append result = retain(captureFrom, position());

            if (result == Append.UNAVAILABLE)
            {
                rejectAppend(result, 0);
            }
            else
            {
                if (result == Append.OK && messageRole == Role.USER)
                {
                    retained.entry(Kind.MESSAGE, messageMark);
                }
                else
                {
                    retained.truncate(messageMark, retained.count());
                    oversize += result == Append.FULL && messageRole == Role.USER ? 1 : 0;
                }

                decoder = decodeMessageNext;
            }
        }
        else
        {
            oversize += messageRole == Role.USER ? 1 : 0;
            decoder = decodeMessageNext;
        }
    }

    private void decodeFinish()
    {
        if ((seen & SEEN_REQUIRED) != SEEN_REQUIRED || parentRunId != null && (seen & SEEN_PARENT_RUN_ID) == 0)
        {
            reject(AguiA2aRejection.INVALID_RUN);
        }
        else if (retained.indexOf(Kind.MESSAGE) == -1 && retained.indexOf(Kind.RESUME) == -1)
        {
            reject(AguiA2aRejection.NO_USER_MESSAGE);
        }
        else
        {
            decoder = decodeDone;
        }
    }

    private void decodeDone()
    {
    }

    private void decodeReject()
    {
        switch (next())
        {
        case START_OBJECT:
        case START_ARRAY:
            rejectDepth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            rejectDepth--;
            break;
        default:
            break;
        }

        if (rejectDepth == 0)
        {
            decoder = decodeIgnore;
        }
    }

    private void decodeIgnore()
    {
    }

    private void flushCapture()
    {
        if (decoder == decodeCapture || decoder == decodeMessageCapture ||
            decoder == decodeMessageRole && messageDecoder == decodeMessageCapture)
        {
            final Append result = retain(captureFrom, position());

            if (result != Append.OK)
            {
                if (decoder == decodeCapture)
                {
                    rejectAppend(result, 1 + depth);
                }
                else if (result == Append.UNAVAILABLE)
                {
                    rejectAppend(result, 0);
                }
                else
                {
                    retained.truncate(messageMark, retained.count());
                    if (decoder == decodeMessageRole)
                    {
                        messageDecoder = decodeMessageDrain;
                    }
                    else
                    {
                        decoder = decodeMessageDrain;
                    }
                }
            }
        }
    }

    private JsonEvent next()
    {
        final JsonEvent event = parser.nextEvent();
        current = switch (event)
        {
        case KEY_NAME, VALUE_STRING, VALUE_NUMBER -> token();
        default -> null;
        };
        return event;
    }

    private CharSequence token()
    {
        final CharSequence view = parser.getStringView();
        final boolean fragment = parser.deferredBytes();
        CharSequence token = view;

        if (fragment || textLength > 0)
        {
            if (textLength == 0)
            {
                text.setLength(0);
            }

            if (textLength + view.length() <= textMax)
            {
                text.append(view);
            }

            textLength += view.length();
            token = fragment ? null : textLength <= textMax ? text : OVERFLOW;
            textLength = fragment ? textLength : 0;
        }

        if (fragment)
        {
            parser.consumed(view.length());
        }

        return token;
    }

    private Append retain(
        int from,
        int to)
    {
        return from < to ? retained.append(window, from, to - from) : Append.OK;
    }

    private int position()
    {
        return windowLimit - parser.remaining();
    }

    private void rejectAppend(
        Append result,
        int nesting)
    {
        if (result == Append.UNAVAILABLE)
        {
            reject(AguiA2aRejection.RETENTION_UNAVAILABLE);
        }
        else
        {
            rejectTooLarge(nesting);
        }
    }

    private void rejectTooLarge(
        int nesting)
    {
        this.rejection = AguiA2aRejection.TOO_LARGE;
        this.rejectDepth = nesting;
        this.decoder = decodeReject;
        retained.release();
    }

    private void reject(
        AguiA2aRejection rejection)
    {
        this.rejection = rejection;
        this.decoder = decodeIgnore;
        retained.release();
    }
}
