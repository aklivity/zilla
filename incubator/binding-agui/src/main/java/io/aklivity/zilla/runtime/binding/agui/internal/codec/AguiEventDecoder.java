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

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiActivityType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiContentType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEncryptedSubtype;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEventMetaFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiRole;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiStateType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiStepType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiSubagentOutcome;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiSubagentType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;

public final class AguiEventDecoder
{
    public static final String CODE_INVALID_MESSAGE = "invalid_message";
    public static final String CODE_INVALID_SEQUENCE = "invalid_sequence";

    public static final int EX_MAX = 1024;
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_OUTCOME = "outcome";
    private static final String MEMBER_INTERRUPT_IDS = "interruptIds";
    private static final String ERROR_MALFORMED = "Malformed event";
    private static final String ERROR_TOO_LARGE = "Event too large";
    private static final String ERROR_TRUNCATED = "Truncated event";

    private static final Map<String, AguiRole> ROLES = Map.of(
        "assistant", AguiRole.ASSISTANT,
        "developer", AguiRole.DEVELOPER,
        "system", AguiRole.SYSTEM,
        "user", AguiRole.USER);

    private enum RunState
    {
        NEW,
        STARTED,
        FINISHED
    }

    private enum GroupState
    {
        NONE,
        OPENED,
        EMITTED
    }

    private enum PayloadMode
    {
        NONE,
        RAW,
        TEXT
    }

    private static final class Member
    {
        private final String name;

        private JsonEvent kind;
        private String text;
        private int valueStart;
        private int valueEnd = -1;

        private Member(
            String name)
        {
            this.name = name;
        }
    }

    private static final class Rejection extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private final String code;

        private Rejection(
            String code,
            String message)
        {
            super(message, null, false, false);
            this.code = code;
        }
    }

    @FunctionalInterface
    private interface AguiEventDecoderState
    {
        int decode();
    }

    private final AguiEventDecoderState decodeIdle = this::decodeIdle;
    private final AguiEventDecoderState decodeStart = this::decodeStart;
    private final AguiEventDecoderState decodeLookahead = this::decodeLookahead;
    private final AguiEventDecoderState decodeEventStart = this::decodeEventStart;
    private final AguiEventDecoderState decodeEvent = this::decodeEvent;
    private final AguiEventDecoderState decodeRawValue = this::decodeRawValue;
    private final AguiEventDecoderState decodeTextValue = this::decodeTextValue;
    private final AguiEventDecoderState decodeEventEnd = this::decodeEventEnd;
    private final AguiEventDecoderState decodeEnd = this::decodeEnd;
    private final AguiEventDecoderState decodeIgnore = this::decodeIgnore;

    private final AguiEventSink sink;
    private final int typeId;
    private final int maximum;
    private final JsonParserEx parser =
        JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
    private final MutableDirectBufferEx slotBuffer;
    private final MutableDirectBufferEx eventBuffer;
    private final MutableDirectBufferEx holdBuffer;
    private final MutableDirectBufferEx textBuffer;
    private final MutableDirectBufferEx pendingExBuffer;
    private final AguiDataExFW.Builder dataExRW = new AguiDataExFW.Builder();
    private final AguiDataExFW dataExRO = new AguiDataExFW();
    private final List<Member> members = new ArrayList<>();
    private final List<Member> specifics = new ArrayList<>();
    private final List<String> interruptIds = new ArrayList<>();
    private final Set<String> steps = new HashSet<>();
    private final Set<String> subagents = new HashSet<>();

    private AguiEventDecoderState decoder = decodeIdle;
    private RunState run = RunState.NEW;
    private GroupState group = GroupState.NONE;
    private String groupId;
    private AguiEventType openType;
    private String openId;
    private AguiEventType eventType;
    private PayloadMode streamMode = PayloadMode.NONE;
    private String outcomeType;
    private DirectBufferEx windowBuffer;
    private int windowOffset;
    private int windowLimit;
    private boolean windowLast;
    private int carryLength;
    private int rawFrom;
    private int rawSkip;
    private int valueDepth;
    private char highSurrogate;
    private int pendingExLength;
    private int holdLength;

    public AguiEventDecoder(
        AguiEventSink sink,
        int typeId,
        int maximum)
    {
        this.sink = sink;
        this.typeId = typeId;
        this.maximum = maximum;
        this.slotBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.eventBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.holdBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.textBuffer = new UnsafeBufferEx(new byte[maximum + Integer.BYTES]);
        this.pendingExBuffer = new UnsafeBufferEx(new byte[maximum + EX_MAX]);
    }

    public void decode(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final boolean init = (flags & INIT) != 0;
        final boolean fin = (flags & FIN) != 0;

        if (decoder == decodeIdle && init)
        {
            parser.reset();
            carryLength = 0;
            decoder = decodeStart;
        }
        else if (decoder == decodeIdle || init && decoder != decodeIgnore)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }

        if (decoder != decodeIgnore)
        {
            decodeInput(fin, buffer, offset, limit);
        }

        if (fin && decoder == decodeEventEnd)
        {
            decoder = decodeIdle;
            completeEvent();
        }
        else if (fin && decoder == decodeEnd)
        {
            decoder = decodeIdle;
        }
        else if (fin && decoder != decodeIgnore)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_TRUNCATED);
        }
    }

    public void finish()
    {
        if (decoder == decodeIdle)
        {
            if (run != RunState.FINISHED)
            {
                fail(CODE_INVALID_SEQUENCE, "Run not finished");
            }
        }
        else if (decoder != decodeIgnore)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_TRUNCATED);
        }
    }

    private void decodeInput(
        boolean fin,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        do
        {
            final boolean carried = carryLength > 0;
            final int copied = carried ? Math.min(maximum - carryLength, limit - progress) : limit - progress;

            if (carried)
            {
                slotBuffer.putBytes(carryLength, buffer, progress, copied);
                windowBuffer = slotBuffer;
                windowOffset = 0;
                windowLimit = carryLength + copied;
            }
            else
            {
                windowBuffer = buffer;
                windowOffset = progress;
                windowLimit = progress + copied;
            }

            progress += copied;
            windowLast = fin && progress == limit;
            parser.wrap(windowBuffer, windowOffset, windowLimit, windowLast);

            final int consumed = decodeWindow();

            if (decoder != decodeIgnore)
            {
                flushRaw(consumed);
                carry(consumed);

                if (copied == 0 && consumed == windowOffset && windowLimit - windowOffset == maximum)
                {
                    fail(CODE_INVALID_MESSAGE, ERROR_TOO_LARGE);
                }
            }
        }
        while (decoder != decodeIgnore && progress < limit);
    }

    private int decodeWindow()
    {
        int position = windowOffset;

        if (decoder == decodeRawValue)
        {
            rawFrom = windowOffset + rawSkip;
        }

        try
        {
            int previous;
            AguiEventDecoderState previousDecoder;

            do
            {
                previous = position;
                previousDecoder = decoder;
                position = decoder.decode();
            }
            while ((position != previous || decoder != previousDecoder) && decoder != decodeIgnore);
        }
        catch (JsonException ex)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }
        catch (Rejection ex)
        {
            fail(ex.code, ex.getMessage());
        }

        return position;
    }

    private void flushRaw(
        int consumed)
    {
        if (decoder == decodeRawValue)
        {
            emitRaw(rawFrom, windowLimit, false);
            rawSkip = windowLimit - consumed;
        }
    }

    private void carry(
        int consumed)
    {
        final int tail = windowLimit - consumed;

        if (tail > maximum)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_TOO_LARGE);
        }
        else if (windowBuffer == slotBuffer)
        {
            System.arraycopy(slotBuffer.byteArray(), consumed, slotBuffer.byteArray(), 0, tail);
        }
        else
        {
            slotBuffer.putBytes(0, windowBuffer, consumed, tail);
        }

        carryLength = tail;
    }

    private int position()
    {
        return windowLimit - parser.remaining();
    }

    private int decodeIdle()
    {
        return position();
    }

    private int decodeStart()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_DOCUMENT)
            {
                decoder = decodeLookahead;
            }
            else
            {
                fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return position();
    }

    private int decodeLookahead()
    {
        final int start = position();
        final int fullLimit = windowLimit;
        final int lookaheadLimit = Math.min(fullLimit, start + maximum);
        final boolean capped = lookaheadLimit < fullLimit;

        windowLimit = lookaheadLimit;
        parser.wrap(windowBuffer, start, lookaheadLimit, windowLast && !capped);
        parser.mark();

        members.clear();
        specifics.clear();
        interruptIds.clear();
        outcomeType = null;

        final boolean complete = lookaheadObject();
        int progress;

        if (complete)
        {
            progress = position();
            eventBuffer.putBytes(0, windowBuffer, start, lookaheadLimit - start);
            rebase(start);
            windowLimit = fullLimit;
            parser.wrap(windowBuffer, progress, fullLimit, windowLast);
            decoder = decodeEventEnd;
        }
        else
        {
            parser.rewind();
            progress = position();
            windowLimit = fullLimit;
            eventBuffer.putBytes(0, windowBuffer, start, lookaheadLimit - start);
            rebase(start);

            if (windowLast && !capped)
            {
                fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
            else if (streamable())
            {
                gate(eventType);
                streamMode = prepare(eventType, true);
                parser.wrap(windowBuffer, progress, fullLimit, windowLast);
                decoder = decodeEventStart;
            }
            else if (capped)
            {
                fail(CODE_INVALID_MESSAGE, ERROR_TOO_LARGE);
            }
        }

        return progress;
    }

    private void rebase(
        int start)
    {
        members.forEach(member -> rebase(member, start));
        specifics.forEach(member -> rebase(member, start));
    }

    private static void rebase(
        Member member,
        int start)
    {
        member.valueStart -= start;
        member.valueEnd = member.valueEnd >= 0 ? member.valueEnd - start : member.valueEnd;
    }

    private boolean lookaheadObject()
    {
        boolean complete = false;
        boolean pending = parser.hasNextEvent();

        if (pending && parser.nextEvent() != JsonEvent.START_OBJECT)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }

        while (pending && parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final Member member = new Member(parser.getStringView().toString());
                    members.add(member);
                    pending = lookaheadValue(member, false);
                }
                break;
            case END_OBJECT:
                complete = true;
                pending = false;
                break;
            default:
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return complete;
    }

    private boolean lookaheadValue(
        Member member,
        boolean outcome)
    {
        member.valueStart = position();

        final JsonEvent event = lookaheadEvent(member);
        boolean complete = false;

        if (event != null)
        {
            member.kind = event;

            switch (event)
            {
            case VALUE_STRING:
            case VALUE_NUMBER:
                member.text = parser.getString();
                complete = true;
                break;
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                complete = true;
                break;
            case START_OBJECT:
                complete = MEMBER_OUTCOME.equals(member.name) && !outcome ? lookaheadOutcome() : lookaheadComposite();
                break;
            case START_ARRAY:
                complete = outcome && MEMBER_INTERRUPT_IDS.equals(member.name) ? lookaheadInterruptIds() : lookaheadComposite();
                break;
            default:
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }

            member.valueEnd = complete ? position() : -1;
        }

        return complete;
    }

    private JsonEvent lookaheadEvent(
        Member member)
    {
        JsonEvent event = null;

        while (event == null && parser.hasNextEvent())
        {
            final JsonEvent next = parser.nextEvent();

            if (fragment(next))
            {
                member.kind = next;
                parser.consumed(0);
            }
            else
            {
                event = next;
            }
        }

        return event;
    }

    private boolean fragment(
        JsonEvent event)
    {
        return (event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER || event == JsonEvent.KEY_NAME) &&
            parser.deferredBytes();
    }

    private boolean lookaheadComposite()
    {
        int depth = 1;

        while (depth > 0 && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (fragment(event))
            {
                parser.consumed(0);
            }
            else if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
            {
                depth++;
            }
            else if (event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY)
            {
                depth--;
            }
        }

        return depth == 0;
    }

    private boolean lookaheadOutcome()
    {
        boolean complete = false;
        boolean pending = true;

        while (pending && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (fragment(event))
            {
                parser.consumed(0);
            }
            else if (event == JsonEvent.KEY_NAME)
            {
                final Member member = new Member(parser.getStringView().toString());

                pending = lookaheadValue(member, true);

                if (MEMBER_TYPE.equals(member.name))
                {
                    outcomeType = member.kind == JsonEvent.VALUE_STRING ? member.text : null;
                }
                else
                {
                    specifics.add(member);
                }
            }
            else if (event == JsonEvent.END_OBJECT)
            {
                complete = true;
                pending = false;
            }
            else
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return complete;
    }

    private boolean lookaheadInterruptIds()
    {
        boolean complete = false;
        boolean pending = true;

        while (pending && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (fragment(event))
            {
                parser.consumed(0);
            }
            else if (event == JsonEvent.VALUE_STRING)
            {
                interruptIds.add(parser.getString());
            }
            else if (event == JsonEvent.END_ARRAY)
            {
                complete = true;
                pending = false;
            }
            else
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return complete;
    }

    private boolean streamable()
    {
        final Member typeMember = member(MEMBER_TYPE);
        final AguiEventType type = typeMember != null && typeMember.valueEnd >= 0 && typeMember.kind == JsonEvent.VALUE_STRING
            ? AguiEventType.of(typeMember.text)
            : null;
        final Member payload = type != null && type.streamable ? member(type.payload) : null;

        eventType = type;

        return payload != null && payload.valueEnd < 0 && payload.kind != null;
    }

    private int decodeEventStart()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                decoder = decodeEvent;
            }
            else
            {
                fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return position();
    }

    private int decodeEvent()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (eventType.payload.contentEquals(parser.getStringView()))
                {
                    rawFrom = position();
                    rawSkip = 0;
                    valueDepth = 0;
                    decoder = streamMode == PayloadMode.TEXT ? decodeTextValue : decodeRawValue;
                }
                else
                {
                    parser.skipValue();
                }
                break;
            case END_OBJECT:
                decoder = decodeEnd;
                break;
            default:
                fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
                break;
            }
        }

        return position();
    }

    private int decodeRawValue()
    {
        while (decoder == decodeRawValue && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = fragment(event);

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                valueDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                valueDepth--;
                break;
            default:
                break;
            }

            if (deferred)
            {
                parser.consumed(parser.getStringView().length());
            }
            else if (event != JsonEvent.KEY_NAME && valueDepth == 0)
            {
                emitRaw(rawFrom, position(), true);
                rawSkip = 0;
                decoder = decodeEvent;
            }
        }

        return position();
    }

    private int decodeTextValue()
    {
        if (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.VALUE_STRING)
            {
                final CharSequence chars = parser.getStringView();
                final boolean deferred = parser.deferredBytes();

                emitText(chars, deferred ? 0 : FIN);

                if (deferred)
                {
                    parser.consumed(chars.length());
                }
                else
                {
                    decoder = decodeEvent;
                }
            }
            else
            {
                fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return position();
    }

    private int decodeEventEnd()
    {
        return decodeTrailing();
    }

    private int decodeEnd()
    {
        return decodeTrailing();
    }

    private int decodeTrailing()
    {
        for (int index = position(); index < windowLimit; index++)
        {
            if (!isWhitespace(windowBuffer.getByte(index)))
            {
                fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return windowLimit;
    }

    private int decodeIgnore()
    {
        return windowLimit;
    }

    private static boolean isWhitespace(
        byte octet)
    {
        return octet == ' ' || octet == '\n' || octet == '\r' || octet == '\t';
    }

    private void emitRaw(
        int from,
        int to,
        boolean last)
    {
        final int flags = (pendingExLength > 0 ? INIT : 0) | (last ? FIN : 0);

        if (flags != 0 || to > from)
        {
            sink.message(flags, pendingExLength > 0 ? pendingEx() : null, windowBuffer, from, to);
            pendingExLength = 0;
        }
    }

    private void emitText(
        CharSequence chars,
        int flags)
    {
        int textLength = 0;

        for (int index = 0; index < chars.length(); index++)
        {
            final char unit = chars.charAt(index);

            if (textLength + Integer.BYTES > maximum)
            {
                emitTextChunk(textLength, 0);
                textLength = 0;
            }

            if (Character.isHighSurrogate(unit))
            {
                highSurrogate = unit;
            }
            else if (Character.isLowSurrogate(unit) && highSurrogate != 0)
            {
                textLength = putCodePoint(textLength, Character.toCodePoint(highSurrogate, unit));
                highSurrogate = 0;
            }
            else
            {
                textLength = putCodePoint(textLength, unit);
            }
        }

        emitTextChunk(textLength, flags & FIN);

        if ((flags & FIN) != 0)
        {
            highSurrogate = 0;
        }
    }

    private void emitTextChunk(
        int length,
        int fin)
    {
        final int flags = (pendingExLength > 0 ? INIT : 0) | fin;

        if (flags != 0 || length > 0)
        {
            sink.message(flags, pendingExLength > 0 ? pendingEx() : null, textBuffer, 0, length);
            pendingExLength = 0;
        }
    }

    private int putCodePoint(
        int offset,
        int codePoint)
    {
        int length = offset;

        if (codePoint < 0x80)
        {
            textBuffer.putByte(length++, (byte) codePoint);
        }
        else if (codePoint < 0x800)
        {
            textBuffer.putByte(length++, (byte) (0xC0 | codePoint >> 6));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint & 0x3F));
        }
        else if (codePoint < 0x10000)
        {
            textBuffer.putByte(length++, (byte) (0xE0 | codePoint >> 12));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint >> 6 & 0x3F));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint & 0x3F));
        }
        else
        {
            textBuffer.putByte(length++, (byte) (0xF0 | codePoint >> 18));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint >> 12 & 0x3F));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint >> 6 & 0x3F));
            textBuffer.putByte(length++, (byte) (0x80 | codePoint & 0x3F));
        }

        return length;
    }

    private Member member(
        String name)
    {
        Member found = null;

        for (int index = 0; found == null && index < members.size(); index++)
        {
            final Member candidate = members.get(index);

            if (candidate.name.equals(name))
            {
                found = candidate;
            }
        }

        return found;
    }

    private String optional(
        String name)
    {
        final Member member = member(name);
        String value = null;

        if (member != null && member.kind != JsonEvent.VALUE_NULL)
        {
            if (member.kind != JsonEvent.VALUE_STRING)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }

            value = member.text;
        }

        return value;
    }

    private String required(
        String name)
    {
        final String value = optional(name);

        if (value == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Missing member: " + name);
        }

        return value;
    }

    private long timestamp()
    {
        final Member member = member("timestamp");
        long timestamp = -1L;

        if (member != null && member.kind != JsonEvent.VALUE_NULL)
        {
            if (member.kind != JsonEvent.VALUE_NUMBER)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }

            try
            {
                timestamp = Long.parseLong(member.text);
            }
            catch (NumberFormatException ex)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return timestamp;
    }

    private AguiEventMetaFW.Builder meta(
        AguiEventMetaFW.Builder meta,
        boolean attribution)
    {
        final Member metadata = member("metadata");
        final AguiEventMetaFW.Builder timed = meta
            .timestamp(timestamp())
            .subagentRunId(attribution ? optional("subagentRunId") : null);

        if (metadata != null && metadata.kind != JsonEvent.VALUE_NULL)
        {
            timed.metadataLength(metadata.valueEnd - metadata.valueStart)
                .metadata(eventBuffer, metadata.valueStart, metadata.valueEnd - metadata.valueStart);
        }

        return timed;
    }

    private AguiDataExFW pendingEx()
    {
        return dataExRO.wrap(pendingExBuffer, 0, pendingExLength);
    }

    private void built(
        AguiDataExFW.Builder builder)
    {
        pendingExLength = builder.build().limit();
    }

    private AguiDataExFW.Builder ex()
    {
        return dataExRW.wrap(pendingExBuffer, 0, pendingExBuffer.capacity()).typeId(typeId);
    }

    private void fail(
        String code,
        String message)
    {
        decoder = decodeIgnore;
        sink.failed(code, message);
    }

    private void sequence(
        String message)
    {
        throw new Rejection(CODE_INVALID_SEQUENCE, message);
    }

    private void completeEvent()
    {
        try
        {
            final Member typeMember = member(MEMBER_TYPE);

            if (typeMember == null)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, "Missing event type");
            }

            if (typeMember.kind != JsonEvent.VALUE_STRING)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }

            final AguiEventType type = AguiEventType.of(typeMember.text);

            if (type == null)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported event type: " + typeMember.text);
            }

            completeEvent(type);
        }
        catch (Rejection ex)
        {
            fail(ex.code, ex.getMessage());
        }
    }

    private void completeEvent(
        AguiEventType type)
    {
        if (run == RunState.FINISHED)
        {
            sequence("Event after run finished");
        }

        if (run == RunState.NEW && type != AguiEventType.RUN_STARTED && type != AguiEventType.RUN_ERROR)
        {
            sequence("Event before run started");
        }

        switch (type)
        {
        case RUN_STARTED:
            decodeRunStarted();
            break;
        case RUN_FINISHED:
            decodeRunFinished();
            break;
        case RUN_ERROR:
            decodeRunError();
            break;
        case TEXT_MESSAGE_START:
            openMessage(type, required("messageId"), textEx());
            break;
        case TEXT_MESSAGE_CONTENT:
        case TEXT_MESSAGE_END:
            decodeContent(type, AguiEventType.TEXT_MESSAGE_START, "messageId", "Text message not started: ");
            break;
        case TOOL_CALL_START:
            openMessage(type, required("toolCallId"), toolCallEx());
            break;
        case TOOL_CALL_ARGS:
        case TOOL_CALL_END:
            decodeContent(type, AguiEventType.TOOL_CALL_START, "toolCallId", "Tool call not started: ");
            break;
        case REASONING_START:
            decodeReasoningStart();
            break;
        case REASONING_MESSAGE_START:
            decodeReasoningMessageStart();
            break;
        case REASONING_MESSAGE_CONTENT:
        case REASONING_MESSAGE_END:
            decodeContent(type, AguiEventType.REASONING_MESSAGE_START, "messageId", "Reasoning message not started: ");
            break;
        case REASONING_END:
            decodeReasoningEnd();
            break;
        default:
            decodePayloadEvent(type);
            break;
        }
    }

    private void decodeRunStarted()
    {
        if (run == RunState.STARTED)
        {
            sequence("Run already started");
        }

        final String threadId = required("threadId");
        final String runId = required("runId");
        final String parentRunId = optional("parentRunId");

        run = RunState.STARTED;
        sink.runStarted(threadId, runId, parentRunId);
    }

    private void decodeRunFinished()
    {
        if (openType != null)
        {
            sequence("Run finished with open message: " + openId);
        }

        if (group != GroupState.NONE)
        {
            sequence("Run finished with open reasoning: " + groupId);
        }

        if (!steps.isEmpty())
        {
            sequence("Run finished with unfinished steps");
        }

        if (!subagents.isEmpty())
        {
            sequence("Run finished with unfinished subagents");
        }

        final long timestamp = timestamp();
        final StringBuilder detail = new StringBuilder();
        AguiOutcomeType outcome = AguiOutcomeType.NONE;

        for (Member member : members)
        {
            switch (member.name)
            {
            case MEMBER_TYPE:
            case "threadId":
            case "runId":
            case "timestamp":
                break;
            case MEMBER_OUTCOME:
                outcome = outcome(member, detail);
                break;
            default:
                detail(detail, member);
                break;
            }
        }

        run = RunState.FINISHED;
        sink.runFinished(outcome, timestamp, detail.length() == 0 ? null : "{" + detail + "}");
    }

    private AguiOutcomeType outcome(
        Member member,
        StringBuilder detail)
    {
        if (member.kind != JsonEvent.START_OBJECT || outcomeType == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }

        final AguiOutcomeType outcome;

        switch (outcomeType)
        {
        case "success":
            outcome = AguiOutcomeType.SUCCESS;
            break;
        case "interrupt":
            outcome = AguiOutcomeType.INTERRUPT;
            break;
        case "cancelled":
            outcome = AguiOutcomeType.CANCELLED;
            break;
        default:
            throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported outcome type: " + outcomeType);
        }

        if (!specifics.isEmpty())
        {
            final StringBuilder specific = new StringBuilder();
            specifics.forEach(entry -> detail(specific, entry));
            detail(detail, MEMBER_OUTCOME, "{" + specific + "}");
        }

        return outcome;
    }

    private void detail(
        StringBuilder detail,
        Member member)
    {
        final String value = eventBuffer.getStringWithoutLengthUtf8(member.valueStart, member.valueEnd - member.valueStart);

        detail(detail, member.name, value);
    }

    private void detail(
        StringBuilder detail,
        String name,
        String value)
    {
        if (detail.length() > 0)
        {
            detail.append(',');
        }

        detail.append(Json.createValue(name)).append(':').append(value);
    }

    private void decodeRunError()
    {
        final String message = required("message");
        final String code = optional("code");

        emitOpen(false);
        run = RunState.FINISHED;
        sink.runError(message, code);
    }

    private void requireNoOpenMessage()
    {
        if (openType != null)
        {
            sequence("Message already open: " + openId);
        }
    }

    private void openMessage(
        AguiEventType type,
        String id,
        AguiDataExFW.Builder builder)
    {
        requireNoOpenMessage();

        built(builder);
        openType = type;
        openId = id;
        holdLength = 0;
    }

    private void decodeContent(
        AguiEventType type,
        AguiEventType opened,
        String idName,
        String error)
    {
        final String id = required(idName);

        if (openType != opened || !openId.equals(id))
        {
            sequence(error + id);
        }

        if (type.payload != null)
        {
            final String delta = required(type.payload);

            if (!delta.isEmpty())
            {
                if (holdLength > 0)
                {
                    emitOpen(false);
                }

                holdLength = holdBuffer.putStringWithoutLengthUtf8(0, delta);
            }
        }
        else
        {
            emitOpen(true);
            openType = null;
            openId = null;
        }
    }

    private void emitOpen(
        boolean last)
    {
        final int flags = (pendingExLength > 0 ? INIT : 0) | (last ? FIN : 0);

        if (holdLength > 0 || flags != 0)
        {
            sink.message(flags, pendingExLength > 0 ? pendingEx() : null, holdBuffer, 0, holdLength);
            pendingExLength = 0;
            holdLength = 0;
        }
    }

    private void decodeReasoningStart()
    {
        if (group != GroupState.NONE)
        {
            sequence("Reasoning already started: " + groupId);
        }

        groupId = required("messageId");
        group = GroupState.OPENED;
    }

    private void decodeReasoningMessageStart()
    {
        if (group == GroupState.NONE)
        {
            sequence("Reasoning not started");
        }

        requireNoOpenMessage();

        final String id = required("messageId");
        final String grouped = groupId.equals(id) ? null : groupId;

        openMessage(AguiEventType.REASONING_MESSAGE_START, id,
            ex().reasoning(r -> r.meta(m -> meta(m, true)).messageId(id).groupId(grouped)));
        group = GroupState.EMITTED;
    }

    private void decodeReasoningEnd()
    {
        final String id = required("messageId");

        if (group == GroupState.NONE)
        {
            sequence("Reasoning not started");
        }

        if (!groupId.equals(id))
        {
            sequence("Reasoning not started: " + id);
        }

        if (openType != null)
        {
            sequence("Message already open: " + openId);
        }

        emitGroup();
        group = GroupState.NONE;
        groupId = null;
    }

    private void emitGroup()
    {
        if (group == GroupState.OPENED)
        {
            built(ex().reasoning(r -> r.meta(m -> m.timestamp(-1)).messageId(groupId)));
            sink.message(INIT | FIN, pendingEx(), holdBuffer, 0, 0);
            pendingExLength = 0;
            group = GroupState.EMITTED;
        }
    }

    private void decodePayloadEvent(
        AguiEventType type)
    {
        gate(type);

        final PayloadMode mode = prepare(type, false);

        switch (mode)
        {
        case RAW:
            final Member raw = member(type.payload);
            sink.message(INIT | FIN, pendingEx(), eventBuffer, raw.valueStart, raw.valueEnd);
            pendingExLength = 0;
            break;
        case TEXT:
            final Member text = member(type.payload);

            if (text.kind != JsonEvent.VALUE_STRING)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }

            emitText(text.text, FIN);
            break;
        default:
            sink.message(INIT | FIN, pendingEx(), eventBuffer, 0, 0);
            pendingExLength = 0;
            break;
        }
    }

    private void gate(
        AguiEventType type)
    {
        if (run == RunState.FINISHED)
        {
            sequence("Event after run finished");
        }

        if (run == RunState.NEW)
        {
            sequence("Event before run started");
        }

        requireNoOpenMessage();
    }

    private PayloadMode prepare(
        AguiEventType type,
        boolean streaming)
    {
        final PayloadMode mode;

        switch (type)
        {
        case REASONING_ENCRYPTED_VALUE:
            mode = prepareEncrypted();
            break;
        case TOOL_CALL_RESULT:
            mode = prepareToolResult();
            break;
        case STATE_SNAPSHOT:
            built(ex().state(s -> s.meta(m -> meta(m, true)).type(t -> t.set(AguiStateType.SNAPSHOT))));
            mode = PayloadMode.RAW;
            break;
        case STATE_DELTA:
            built(ex().state(s -> s.meta(m -> meta(m, true)).type(t -> t.set(AguiStateType.DELTA))));
            mode = PayloadMode.RAW;
            break;
        case MESSAGES_SNAPSHOT:
            built(ex().state(s -> s.meta(m -> meta(m, true)).type(t -> t.set(AguiStateType.MESSAGES_SNAPSHOT))));
            mode = PayloadMode.RAW;
            break;
        case ACTIVITY_SNAPSHOT:
        case ACTIVITY_DELTA:
            mode = prepareActivity(type);
            break;
        case STEP_STARTED:
        case STEP_FINISHED:
            mode = prepareStep(type);
            break;
        case SUBAGENT_STARTED:
        case SUBAGENT_FINISHED:
        case SUBAGENT_ERROR:
            mode = prepareSubagent(type);
            break;
        case RAW:
            final String source = optional("source");
            built(ex().raw(r -> r.meta(m -> meta(m, true)).source(source)));
            mode = PayloadMode.RAW;
            break;
        default:
            final String name = required("name");
            built(ex().custom(c -> c.meta(m -> meta(m, true)).name(name)));
            mode = PayloadMode.RAW;
            break;
        }

        if (mode == PayloadMode.RAW && member(type.payload) == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Missing member: " + type.payload);
        }

        return mode;
    }

    private PayloadMode prepareEncrypted()
    {
        if (group == GroupState.NONE)
        {
            sequence("Reasoning not started");
        }

        emitGroup();

        final String subtype = required("subtype");
        final String entityId = required("entityId");
        final AguiEncryptedSubtype encrypted;

        switch (subtype)
        {
        case "message":
            encrypted = AguiEncryptedSubtype.MESSAGE;
            break;
        case "tool-call":
            encrypted = AguiEncryptedSubtype.TOOL_CALL;
            break;
        default:
            throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported subtype: " + subtype);
        }

        if (member("encryptedValue") == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Missing member: encryptedValue");
        }

        built(ex().reasoningEncrypted(r -> r.meta(m -> meta(m, true)).subtype(t -> t.set(encrypted)).entityId(entityId)));

        return PayloadMode.TEXT;
    }

    private PayloadMode prepareToolResult()
    {
        final String messageId = required("messageId");
        final String toolCallId = required("toolCallId");
        final Member content = member("content");

        if (content == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Missing member: content");
        }

        final AguiContentType type;

        if (content.kind == JsonEvent.VALUE_STRING)
        {
            type = AguiContentType.TEXT;
        }
        else if (content.kind == JsonEvent.START_ARRAY)
        {
            type = AguiContentType.PARTS;
        }
        else
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported tool result content");
        }

        built(ex().toolResult(r -> r.meta(m -> meta(m, true)).messageId(messageId).toolCallId(toolCallId)
            .content(c -> c.set(type))));

        return type == AguiContentType.TEXT ? PayloadMode.TEXT : PayloadMode.RAW;
    }

    private PayloadMode prepareActivity(
        AguiEventType type)
    {
        final String messageId = required("messageId");
        final String activityType = required("activityType");
        final boolean snapshot = type == AguiEventType.ACTIVITY_SNAPSHOT;
        final Member replace = member("replace");
        final int replaced = replace != null && replace.kind == JsonEvent.VALUE_FALSE ? 0 : 1;

        built(ex().activity(a -> a.meta(m -> meta(m, true))
            .type(t -> t.set(snapshot ? AguiActivityType.SNAPSHOT : AguiActivityType.DELTA))
            .messageId(messageId)
            .activityType(activityType)
            .replace(replaced)));

        return PayloadMode.RAW;
    }

    private PayloadMode prepareStep(
        AguiEventType type)
    {
        final String stepName = required("stepName");
        final boolean started = type == AguiEventType.STEP_STARTED;

        if (started && !steps.add(stepName))
        {
            sequence("Step already started: " + stepName);
        }
        else if (!started && !steps.remove(stepName))
        {
            sequence("Step not started: " + stepName);
        }

        built(ex().step(s -> s.meta(m -> meta(m, true)).type(t -> t.set(started ? AguiStepType.STARTED : AguiStepType.FINISHED))
            .stepName(stepName)));

        return PayloadMode.NONE;
    }

    private PayloadMode prepareSubagent(
        AguiEventType type)
    {
        final String subagentRunId = required("subagentRunId");

        switch (type)
        {
        case SUBAGENT_STARTED:
            if (!subagents.add(subagentRunId))
            {
                sequence("Subagent already started: " + subagentRunId);
            }
            break;
        default:
            if (!subagents.remove(subagentRunId))
            {
                sequence("Subagent not started: " + subagentRunId);
            }
            break;
        }

        final AguiSubagentType subagentType = type == AguiEventType.SUBAGENT_STARTED ? AguiSubagentType.STARTED
            : type == AguiEventType.SUBAGENT_FINISHED ? AguiSubagentType.FINISHED : AguiSubagentType.ERROR;
        final String name = optional("name");
        final String description = optional("description");
        final String parentSubagentRunId = optional("parentSubagentRunId");
        final String parentToolCallId = optional("parentToolCallId");
        final String parentMessageId = optional("parentMessageId");
        final String message = type == AguiEventType.SUBAGENT_ERROR ? required("message") : null;
        final String code = type == AguiEventType.SUBAGENT_ERROR ? optional("code") : null;
        final List<String> interruptIds = new ArrayList<>();
        final AguiSubagentOutcome outcome = type == AguiEventType.SUBAGENT_FINISHED
            ? subagentOutcome(interruptIds)
            : AguiSubagentOutcome.NONE;

        built(ex().subagent(s ->
        {
            s.meta(m -> meta(m, false))
                .type(t -> t.set(subagentType))
                .subagentRunId(subagentRunId)
                .name(name)
                .description(description)
                .parentSubagentRunId(parentSubagentRunId)
                .parentToolCallId(parentToolCallId)
                .parentMessageId(parentMessageId)
                .code(code)
                .message(message)
                .outcome(o -> o.set(outcome));
            interruptIds.forEach(id -> s.interruptIdsItem(i -> i.set(id, UTF_8)));
        }));

        return type == AguiEventType.SUBAGENT_FINISHED && member("result") != null ? PayloadMode.RAW : PayloadMode.NONE;
    }

    private AguiSubagentOutcome subagentOutcome(
        List<String> ids)
    {
        final Member member = member(MEMBER_OUTCOME);
        AguiSubagentOutcome outcome = AguiSubagentOutcome.NONE;

        if (member != null && member.valueEnd >= 0)
        {
            if (member.kind != JsonEvent.START_OBJECT)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }

            switch (outcomeType != null ? outcomeType.toLowerCase(Locale.ROOT) : "")
            {
            case "success":
                outcome = AguiSubagentOutcome.SUCCESS;
                break;
            case "suspended":
                outcome = AguiSubagentOutcome.SUSPENDED;
                ids.addAll(interruptIds);
                break;
            default:
                throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported outcome type: " + outcomeType);
            }
        }

        return outcome;
    }

    private AguiDataExFW.Builder textEx()
    {
        final String id = required("messageId");
        final String role = optional("role");
        final AguiRole resolved = role != null ? ROLES.get(role) : AguiRole.ASSISTANT;

        if (resolved == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported role: " + role);
        }

        return ex().text(t -> t.meta(m -> meta(m, true)).messageId(id).role(r -> r.set(resolved)));
    }

    private AguiDataExFW.Builder toolCallEx()
    {
        final String id = required("toolCallId");
        final String name = required("toolCallName");
        final String parentMessageId = optional("parentMessageId");

        return ex().toolCall(t -> t.meta(m -> meta(m, true)).toolCallId(id).toolCallName(name).parentMessageId(parentMessageId));
    }
}
