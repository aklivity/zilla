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

import java.math.BigDecimal;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

abstract class LlmToolTransform implements JsonTransform
{
    protected static final int DROP = 0;
    protected static final int REJECT = 1;
    protected static final int DESCEND = 2;
    protected static final int PASS_STRING = 3;
    protected static final int PASS_STRING_OR_DROP = 4;
    protected static final int PASS_OBJECT = 5;
    protected static final int HOLD_STRING = 6;
    protected static final int UNESCAPE_OBJECT = 7;
    protected static final int ESCAPE_OBJECT = 8;
    protected static final int DESCEND_KEPT = 9;
    protected static final int PASS_VALUE = 10;
    protected static final int PASS_DATA_URI = 11;
    protected static final int STRIP_DATA_URI = 12;
    protected static final int REPLACE_VALUE = 13;
    protected static final int DESCEND_KEPT_OR_REPLACE = 14;
    protected static final int CONVERT_STRING = 15;
    protected static final int DESCEND_KEPT_OR_ELEMENTS = 16;
    protected static final int DESCEND_ELEMENTS = 17;
    protected static final int WRAP_STRING_AS_ELEMENT = 18;
    protected static final int CLAMP_NUMBER = 19;
    protected static final int INTEGER_RANGE = 20;

    protected static final int MODE_OBJECT = 0;
    protected static final int MODE_EXTRACT = 1;
    protected static final int MODE_VALUE = 2;
    protected static final int MODE_ARRAY = 3;

    private static final String DATA_URI_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final int NONE = -1;
    private static final int QUEUE_CAPACITY = 16;
    static final int HELD_MAX = 4096;
    private static final int NAMES_CAPACITY = 32;
    private static final String SHARED_DIALECT = "x";
    private static final int SHARED_PREFIX_LENGTH = SHARED_DIALECT.length();

    protected String target;

    private final int mode;
    private final boolean sameDialect;
    private final int extractDepth;
    private final JsonEvent[] queueEvents;
    private final String[] queueTexts;
    private final boolean[] queueSources;
    private final boolean[] queueFragments;
    private final LiteralSource literalSource;
    private final LiteralControl literalControl;
    private final StringBuilder heldBuilder;
    private final String[] names;
    private final MemberView sharedMember;

    private JsonController control;
    private JsonSource source;
    private JsonSink sink;
    private int nameCount;
    private int queueHead;
    private int queueTail;
    private int depth;
    private int pending;
    private boolean passing;
    private boolean passEscaped;
    private int passDepth;
    private boolean skipping;
    private int skipDepth;
    private boolean holding;
    private boolean converting;
    private boolean wrapping;
    private boolean clamping;
    private boolean ranging;
    private boolean nesting;
    private int nestDepth;
    private String nested;
    private double clampMinimum;
    private double clampMaximum;
    private boolean awaitingContent;
    private boolean valueStarted;
    private boolean arrayOpen;
    private boolean elementPending;
    private String held;
    private String rejectedMember;

    protected LlmToolTransform()
    {
        this(MODE_OBJECT);
    }

    protected LlmToolTransform(
        int mode)
    {
        this(mode, false);
    }

    protected LlmToolTransform(
        int mode,
        boolean sameDialect)
    {
        this(mode, sameDialect, 1);
    }

    protected LlmToolTransform(
        int mode,
        boolean sameDialect,
        int extractDepth)
    {
        this.mode = mode;
        this.sameDialect = sameDialect;
        this.extractDepth = extractDepth;
        this.queueEvents = new JsonEvent[QUEUE_CAPACITY];
        this.queueTexts = new String[QUEUE_CAPACITY];
        this.queueSources = new boolean[QUEUE_CAPACITY];
        this.queueFragments = new boolean[QUEUE_CAPACITY];
        this.literalSource = new LiteralSource();
        this.literalControl = new LiteralControl();
        this.heldBuilder = new StringBuilder();
        this.names = new String[NAMES_CAPACITY];
        this.sharedMember = new MemberView();
        this.pending = NONE;
    }

    protected abstract int member(
        int depth,
        CharSequence key);

    protected abstract void onOpen();

    protected abstract Status onClose(
        int depth);

    protected void onBegin()
    {
    }

    protected Status onDescend(
        int depth)
    {
        return Status.ADVANCED;
    }

    protected int element(
        int depth)
    {
        return PASS_VALUE;
    }

    protected void onValueEnd()
    {
    }

    protected String converted(
        String value)
    {
        return value;
    }

    protected Status onReplace(
        JsonEvent event)
    {
        return Status.REJECTED;
    }

    protected Status onEnd()
    {
        return Status.ADVANCED;
    }

    protected void onEscapedEnd()
    {
    }

    protected Status onEndObject()
    {
        return Status.ADVANCED;
    }

    protected final String attribute(
        String name)
    {
        final DirectBufferEx value = control.envelope().get(name, 0);

        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }

    protected final boolean attributeEquals(
        String name,
        String expected)
    {
        final DirectBufferEx value = control.envelope().get(name, 0);
        final int length = expected.length();
        boolean equal = value != null && value.capacity() == length;

        for (int index = 0; equal && index < length; index++)
        {
            equal = value.getByte(index) == expected.charAt(index);
        }

        return equal;
    }

    protected final String held()
    {
        return held;
    }

    protected final void clearHeld()
    {
        held = null;
    }

    protected final void pass(
        JsonEvent event)
    {
        enqueue(event, null, true, false);
    }

    protected final void emit(
        JsonEvent event)
    {
        enqueue(event, null, false, false);
    }

    protected final void emitFragment(
        JsonEvent event,
        String text)
    {
        enqueue(event, text, false, true);
    }

    protected final void emit(
        JsonEvent event,
        String text)
    {
        enqueue(event, text, false, false);
    }

    protected final int prefix(
        String dialect,
        CharSequence key)
    {
        target = name(dialect, key);

        return PASS_VALUE;
    }

    protected final int unprefix(
        String dialect,
        CharSequence key)
    {
        final int length = key.length();
        int colon = 0;

        while (colon < length && key.charAt(colon) != ':')
        {
            colon++;
        }

        int action = PASS_VALUE;

        if (colon == length)
        {
            target = name(null, key);
        }
        else if (colon == SHARED_PREFIX_LENGTH && regionMatches(key, 0, colon, SHARED_DIALECT))
        {
            action = shared(sharedMember.wrap(key, colon + 1, length));
        }
        else if (sameDialect && regionMatches(key, 0, colon, dialect))
        {
            target = name(null, key, colon + 1, length);
        }
        else
        {
            action = DROP;
        }

        return action;
    }

    protected int shared(
        CharSequence member)
    {
        return DROP;
    }

    protected final int share(
        CharSequence member)
    {
        target = name(SHARED_DIALECT, member);

        return PASS_VALUE;
    }

    protected final int clamp(
        String name,
        double minimum,
        double maximum)
    {
        target = name;
        clampMinimum = minimum;
        clampMaximum = maximum;

        return CLAMP_NUMBER;
    }

    protected final int rangeInteger(
        String name,
        double minimum,
        double maximum)
    {
        target = name;
        clampMinimum = minimum;
        clampMaximum = maximum;

        return INTEGER_RANGE;
    }

    protected final void nest(
        String container)
    {
        nested = container;
    }

    protected final int reject(
        CharSequence member)
    {
        rejectedMember = member.toString();

        return REJECT;
    }

    protected final String rejectedMember()
    {
        return rejectedMember;
    }

    protected final String name(
        CharSequence value)
    {
        return name(null, value);
    }

    private String name(
        String dialect,
        CharSequence key)
    {
        return name(dialect, key, 0, key.length());
    }

    private String name(
        String dialect,
        CharSequence key,
        int from,
        int to)
    {
        final int prefixLength = dialect != null ? dialect.length() + 1 : 0;
        final int length = prefixLength + to - from;
        String found = null;

        for (int index = 0; found == null && index < nameCount; index++)
        {
            final String candidate = names[index];

            if (candidate.length() == length &&
                (dialect == null || candidate.startsWith(dialect) && candidate.charAt(dialect.length()) == ':') &&
                regionMatches(key, from, to, candidate, prefixLength))
            {
                found = candidate;
            }
        }

        if (found == null)
        {
            found = (dialect != null ? dialect + ":" : "") + key.subSequence(from, to);

            if (nameCount < NAMES_CAPACITY)
            {
                names[nameCount++] = found;
            }
        }

        return found;
    }

    private static boolean regionMatches(
        CharSequence value,
        int from,
        int to,
        String expected)
    {
        return to - from == expected.length() && regionMatches(value, from, to, expected, 0);
    }

    private static boolean regionMatches(
        CharSequence value,
        int from,
        int to,
        String expected,
        int expectedFrom)
    {
        boolean matches = true;

        for (int index = from; matches && index < to; index++)
        {
            matches = value.charAt(index) == expected.charAt(expectedFrom + index - from);
        }

        return matches;
    }

    protected static boolean matches(
        CharSequence value,
        String expected)
    {
        final int length = expected.length();
        boolean matches = value.length() == length;

        for (int index = 0; matches && index < length; index++)
        {
            matches = value.charAt(index) == expected.charAt(index);
        }

        return matches;
    }

    @Override
    public boolean deterministic()
    {
        return true;
    }

    @Override
    public void reset()
    {
        queueHead = 0;
        queueTail = 0;
        depth = 0;
        pending = NONE;
        passing = false;
        passEscaped = false;
        skipping = false;
        holding = false;
        converting = false;
        wrapping = false;
        clamping = false;
        ranging = false;
        nesting = false;
        nested = null;
        awaitingContent = false;
        valueStarted = false;
        arrayOpen = false;
        elementPending = false;
        held = null;
        rejectedMember = null;
        heldBuilder.setLength(0);
    }

    @Override
    public final Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        this.control = control;
        this.source = source;
        this.sink = sink;

        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_DOCUMENT:
            pass(event);
            break;
        case END_DOCUMENT:
            status = mode == MODE_VALUE ? valueEnd() : onEndObject();
            pass(event);
            break;
        case KEY_NAME:
            status = onKey();
            break;
        case START_ESCAPED:
        case END_ESCAPED:
            status = onEscaped(event);
            break;
        default:
            status = onValue(event);
            break;
        }

        return status == Status.ADVANCED ? drain() : status;
    }

    @Override
    public final Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        this.control = control;
        this.source = source;
        this.sink = sink;

        Status status = Status.ADVANCED;

        if (queueHead < queueTail)
        {
            status = queueSources[queueHead]
                ? sink.resume(control, source, event)
                : sink.resume(literalControl, literalSource, queueEvents[queueHead]);

            if (status != Status.SUSPENDED)
            {
                queueHead++;
            }

            if (status == Status.ADVANCED)
            {
                status = drain();
            }
        }

        return status;
    }

    private Status onKey()
    {
        Status status = Status.ADVANCED;

        if (passing)
        {
            pass(JsonEvent.KEY_NAME);
        }
        else if (!skipping && !source.deferredBytes())
        {
            nested = null;
            pending = member(depth, source.getStringView());

            if (pending == REJECT)
            {
                pending = NONE;
                status = Status.REJECTED;
            }
            else if (pending == UNESCAPE_OBJECT)
            {
                control.escaped();
            }
        }

        return status;
    }

    private Status onEscaped(
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        if (event == JsonEvent.START_ESCAPED)
        {
            if (pending == UNESCAPE_OBJECT)
            {
                pending = NONE;
                awaitingContent = true;

                if (mode != MODE_EXTRACT)
                {
                    emit(JsonEvent.KEY_NAME, target);
                }
            }
            else
            {
                status = Status.REJECTED;
            }
        }
        else if (awaitingContent)
        {
            awaitingContent = false;
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }

        return status;
    }

    private Status onValue(
        JsonEvent event)
    {
        final Status status;

        if (skipping)
        {
            status = skip(event);
        }
        else if (awaitingContent)
        {
            awaitingContent = false;
            status = event == JsonEvent.START_OBJECT ? startPass(event) : Status.REJECTED;
        }
        else if (mode == MODE_VALUE && !valueStarted)
        {
            valueStarted = true;
            onBegin();
            status = event == JsonEvent.START_OBJECT ? startPass(event) : Status.REJECTED;
        }
        else if (pending != NONE)
        {
            status = begin(event);
        }
        else if (holding)
        {
            status = hold();
        }
        else if (passing)
        {
            status = carry(event);
        }
        else
        {
            status = structure(event);
        }

        return status;
    }

    private Status structure(
        JsonEvent event)
    {
        Status status = Status.REJECTED;

        if (mode == MODE_ARRAY && event == JsonEvent.START_ARRAY && depth == 0 && !arrayOpen)
        {
            pass(event);
            arrayOpen = true;
            status = Status.ADVANCED;
        }
        else if (event == JsonEvent.START_OBJECT && depth == 0 && (mode != MODE_ARRAY || arrayOpen))
        {
            if (mode == MODE_ARRAY)
            {
                elementPending = true;
            }
            else if (mode != MODE_EXTRACT)
            {
                pass(event);
            }

            depth++;
            onOpen();
            status = Status.ADVANCED;
        }
        else if (event == JsonEvent.END_OBJECT && depth > 0)
        {
            status = onClose(depth);

            if (mode == MODE_ARRAY && depth == 1)
            {
                if (!elementPending)
                {
                    emit(JsonEvent.END_OBJECT);
                }

                elementPending = false;
            }

            depth--;
        }
        else if (mode == MODE_ARRAY && event == JsonEvent.END_ARRAY && depth == 0 && arrayOpen)
        {
            pass(event);
            arrayOpen = false;
            status = Status.ADVANCED;
        }
        else if (depth > 0)
        {
            status = event == JsonEvent.END_ARRAY ? endElements(event) : beginElement(event);
        }

        return status;
    }

    private Status beginElement(
        JsonEvent event)
    {
        target = null;
        pending = element(depth);

        Status status = Status.REJECTED;

        if (pending != REJECT)
        {
            status = begin(event);
        }
        else
        {
            pending = NONE;
        }

        return status;
    }

    private Status endElements(
        JsonEvent event)
    {
        pass(event);
        depth--;

        if (nesting && depth == nestDepth)
        {
            closeNested();
        }

        return Status.ADVANCED;
    }

    private Status begin(
        JsonEvent event)
    {
        final int action = pending;
        pending = NONE;

        Status status = Status.ADVANCED;

        switch (action)
        {
        case DESCEND:
            if (event == JsonEvent.START_OBJECT)
            {
                depth++;
            }
            else
            {
                status = Status.REJECTED;
            }
            break;
        case PASS_VALUE:
            status = beginPass(event);
            break;
        case DESCEND_KEPT:
            status = event == JsonEvent.START_OBJECT ? descendKept(event) : Status.REJECTED;
            break;
        case DESCEND_KEPT_OR_REPLACE:
            status = event == JsonEvent.START_OBJECT ? descendKept(event) : replace(event);
            break;
        case REPLACE_VALUE:
            status = replace(event);
            break;
        case PASS_DATA_URI:
            status = event == JsonEvent.VALUE_STRING ? beginDataUri(event) : Status.REJECTED;
            break;
        case STRIP_DATA_URI:
            status = event == JsonEvent.VALUE_STRING ? beginStripped(event) : Status.REJECTED;
            break;
        case PASS_STRING:
            status = event == JsonEvent.VALUE_STRING ? beginPass(event) : Status.REJECTED;
            break;
        case PASS_STRING_OR_DROP:
            status = event == JsonEvent.VALUE_STRING ? beginPass(event) : beginSkip(event);
            break;
        case PASS_OBJECT:
            status = event == JsonEvent.START_OBJECT ? beginPass(event) : Status.REJECTED;
            break;
        case HOLD_STRING:
            status = event == JsonEvent.VALUE_STRING ? hold() : Status.REJECTED;
            break;
        case CONVERT_STRING:
            if (event == JsonEvent.START_ARRAY)
            {
                status = descendElements(event);
            }
            else
            {
                converting = event == JsonEvent.VALUE_STRING;
                status = converting ? hold() : Status.REJECTED;
            }
            break;
        case DESCEND_KEPT_OR_ELEMENTS:
            status = descendKeptOrElements(event);
            break;
        case DESCEND_ELEMENTS:
            status = descendOrSkipElements(event);
            break;
        case WRAP_STRING_AS_ELEMENT:
            status = wrapStringAsElement(event);
            break;
        case CLAMP_NUMBER:
            status = event == JsonEvent.VALUE_NUMBER ? holdNumber() : Status.REJECTED;
            break;
        case INTEGER_RANGE:
            status = holdInteger(event);
            break;
        case ESCAPE_OBJECT:
            if (event == JsonEvent.START_OBJECT)
            {
                emit(JsonEvent.KEY_NAME, target);
                emit(JsonEvent.START_ESCAPED);
                passEscaped = true;
                status = startPass(event);
            }
            else
            {
                status = Status.REJECTED;
            }
            break;
        default:
            status = beginSkip(event);
            break;
        }

        return status;
    }

    private Status descendKept(
        JsonEvent event)
    {
        if (mode != MODE_EXTRACT && target != null)
        {
            emitMember();
        }

        pass(event);
        depth++;

        return onDescend(depth);
    }

    private Status descendElements(
        JsonEvent event)
    {
        if (mode != MODE_EXTRACT && target != null)
        {
            emitMember();
        }

        pass(event);
        depth++;

        return Status.ADVANCED;
    }

    private Status descendKeptOrElements(
        JsonEvent event)
    {
        final Status status;

        if (event == JsonEvent.START_OBJECT)
        {
            status = descendKept(event);
        }
        else if (event == JsonEvent.START_ARRAY)
        {
            status = descendElements(event);
        }
        else
        {
            status = beginPass(event);
        }

        return status;
    }

    private Status descendOrSkipElements(
        JsonEvent event)
    {
        final Status status;

        if (event == JsonEvent.START_ARRAY)
        {
            status = descendElements(event);
        }
        else if (event == JsonEvent.VALUE_NULL)
        {
            status = Status.ADVANCED;
        }
        else
        {
            status = Status.REJECTED;
        }

        return status;
    }

    private Status wrapStringAsElement(
        JsonEvent event)
    {
        final Status status;

        if (event == JsonEvent.VALUE_STRING)
        {
            converting = true;
            wrapping = true;
            status = hold();
        }
        else
        {
            status = descendKeptOrElements(event);
        }

        return status;
    }

    private Status holdNumber()
    {
        clamping = true;

        return hold();
    }

    private Status holdInteger(
        JsonEvent event)
    {
        final Status status;

        if (event == JsonEvent.VALUE_NUMBER)
        {
            ranging = true;
            status = hold();
        }
        else if (event == JsonEvent.VALUE_NULL)
        {
            status = Status.ADVANCED;
        }
        else
        {
            status = Status.REJECTED;
        }

        return status;
    }

    private void emitMember()
    {
        if (nested != null && !nesting)
        {
            emit(JsonEvent.KEY_NAME, nested);
            emit(JsonEvent.START_OBJECT);
            nesting = true;
            nestDepth = depth;
        }

        emit(JsonEvent.KEY_NAME, target);
    }

    private void closePassed()
    {
        if (passDepth == nestDepth)
        {
            closeNested();
        }
    }

    private void closeNested()
    {
        if (nesting)
        {
            nesting = false;
            emit(JsonEvent.END_OBJECT);
        }
    }

    private void emitRanged(
        String number)
    {
        final double value = Double.parseDouble(number);

        if (value >= clampMinimum && value <= clampMaximum && value == Math.rint(value))
        {
            emitMember();
            emit(JsonEvent.VALUE_NUMBER, number);
            closeNested();
        }
    }

    private void emitClamped(
        String number)
    {
        final double value = Double.parseDouble(number);

        emitMember();

        if (value < clampMinimum)
        {
            emit(JsonEvent.VALUE_NUMBER, Double.toString(clampMinimum));
        }
        else if (value > clampMaximum)
        {
            emit(JsonEvent.VALUE_NUMBER, Double.toString(clampMaximum));
        }
        else
        {
            emit(JsonEvent.VALUE_NUMBER, number);
        }

        closeNested();
    }

    private Status replace(
        JsonEvent event)
    {
        Status status = onReplace(event);

        if (status == Status.ADVANCED)
        {
            status = beginSkip(event);
        }

        return status;
    }

    private Status beginDataUri(
        JsonEvent event)
    {
        final String mediaType = attribute("media-type");
        Status status = Status.REJECTED;

        if (mediaType != null)
        {
            emit(JsonEvent.KEY_NAME, target);
            emitFragment(JsonEvent.VALUE_STRING, DATA_URI_PREFIX + mediaType + BASE64_MARKER);
            status = startPass(event);
        }

        return status;
    }

    private Status beginStripped(
        JsonEvent event)
    {
        final DirectBufferEx mediaType = control.envelope().get("media-type", 0);
        Status status = Status.REJECTED;

        if (mediaType != null)
        {
            final CharSequence view = source.getStringView();
            final int prefixLength = DATA_URI_PREFIX.length() + mediaType.capacity() + BASE64_MARKER.length();

            if (view.length() >= prefixLength)
            {
                if (prefixMatches(view, mediaType))
                {
                    control.consumed(prefixLength);
                    status = beginPass(event);
                }
            }
            else if (source.deferredBytes())
            {
                control.consumed(0);
                pending = STRIP_DATA_URI;
                status = Status.STARVED;
            }
        }

        return status;
    }

    private static boolean prefixMatches(
        CharSequence view,
        DirectBufferEx mediaType)
    {
        final int mediaTypeLength = mediaType.capacity();
        boolean matches = startsWith(view, 0, DATA_URI_PREFIX);

        for (int index = 0; matches && index < mediaTypeLength; index++)
        {
            matches = view.charAt(DATA_URI_PREFIX.length() + index) == (char) mediaType.getByte(index);
        }

        return matches && startsWith(view, DATA_URI_PREFIX.length() + mediaTypeLength, BASE64_MARKER);
    }

    private static boolean startsWith(
        CharSequence view,
        int offset,
        String prefix)
    {
        boolean matches = true;

        for (int index = 0; matches && index < prefix.length(); index++)
        {
            matches = view.charAt(offset + index) == prefix.charAt(index);
        }

        return matches;
    }

    private Status beginPass(
        JsonEvent event)
    {
        if ((mode != MODE_EXTRACT || depth > extractDepth) && target != null)
        {
            emitMember();
        }

        return startPass(event);
    }

    private Status startPass(
        JsonEvent event)
    {
        pass(event);
        passDepth = depth;

        if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
        {
            passing = true;
            depth++;
        }
        else
        {
            passing = source.deferredBytes();

            if (!passing)
            {
                closePassed();
            }
        }

        return Status.ADVANCED;
    }

    private Status carry(
        JsonEvent event)
    {
        pass(event);

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            passing = depth != passDepth;
            break;
        default:
            passing = source.deferredBytes() || depth != passDepth;
            break;
        }

        finishPass();

        return Status.ADVANCED;
    }

    private Status valueEnd()
    {
        return valueStarted ? onEnd() : Status.REJECTED;
    }

    private void finishPass()
    {
        if (!passing && passEscaped)
        {
            passEscaped = false;
            emit(JsonEvent.END_ESCAPED);
            onEscapedEnd();
        }

        if (!passing && mode == MODE_VALUE)
        {
            onValueEnd();
        }

        if (!passing)
        {
            closePassed();
        }
    }

    private Status beginSkip(
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            skipping = true;
            skipDepth = depth;
            depth++;
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
            if (source.deferredBytes())
            {
                skipping = true;
                skipDepth = depth;
                status = drop();
            }
            break;
        default:
            break;
        }

        return status;
    }

    private Status skip(
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            skipping = depth != skipDepth;
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
            if (source.deferredBytes())
            {
                status = drop();
            }
            else
            {
                skipping = depth != skipDepth;
            }
            break;
        default:
            skipping = depth != skipDepth;
            break;
        }

        return status;
    }

    private Status drop()
    {
        control.consumed(source.getStringView().length());

        return Status.STARVED;
    }

    private Status hold()
    {
        final CharSequence view = source.getStringView();

        heldBuilder.append(view);
        control.consumed(view.length());

        Status status = Status.REJECTED;

        if (heldBuilder.length() <= HELD_MAX)
        {
            holding = source.deferredBytes();
            status = Status.STARVED;

            if (!holding)
            {
                final String value = name(heldBuilder);
                heldBuilder.setLength(0);

                if (converting)
                {
                    converting = false;

                    if (target != null)
                    {
                        emitMember();
                    }

                    if (wrapping)
                    {
                        wrapping = false;
                        emit(JsonEvent.START_ARRAY);
                        emit(JsonEvent.VALUE_STRING, converted(value));
                        emit(JsonEvent.END_ARRAY);
                    }
                    else
                    {
                        emit(JsonEvent.VALUE_STRING, converted(value));
                    }

                    closeNested();
                }
                else if (ranging)
                {
                    ranging = false;
                    emitRanged(value);
                }
                else if (clamping)
                {
                    clamping = false;
                    emitClamped(value);
                }
                else
                {
                    held = value;
                }

                status = Status.ADVANCED;
            }
        }

        return status;
    }

    private void enqueue(
        JsonEvent event,
        String text,
        boolean fromSource,
        boolean fragment)
    {
        if (elementPending)
        {
            elementPending = false;
            store(JsonEvent.START_OBJECT, null, false, false);
        }

        store(event, text, fromSource, fragment);
    }

    private void store(
        JsonEvent event,
        String text,
        boolean fromSource,
        boolean fragment)
    {
        queueEvents[queueTail] = event;
        queueTexts[queueTail] = text;
        queueSources[queueTail] = fromSource;
        queueFragments[queueTail] = fragment;
        queueTail++;
    }

    private Status drain()
    {
        Status status = Status.ADVANCED;

        while (status == Status.ADVANCED && queueHead < queueTail)
        {
            status = queueSources[queueHead]
                ? sink.transform(control, source, queueEvents[queueHead])
                : sink.transform(literalControl, literalSource.with(queueTexts[queueHead], queueFragments[queueHead]),
                    queueEvents[queueHead]);

            if (status != Status.SUSPENDED)
            {
                queueHead++;
            }
        }

        if (queueHead == queueTail)
        {
            queueHead = 0;
            queueTail = 0;
        }

        return status;
    }

    private final class LiteralControl implements JsonController
    {
        @Override
        public void segmentable()
        {
        }

        @Override
        public long authorization()
        {
            return control.authorization();
        }

        @Override
        public JsonEnvelope envelope()
        {
            return control.envelope();
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            literalSource.advance(sourceBytes);
        }
    }

    private static final class LiteralSource implements JsonSource
    {
        private final LiteralView view;

        private boolean fragment;

        private LiteralSource()
        {
            this.view = new LiteralView();
        }

        private LiteralSource with(
            String text,
            boolean fragment)
        {
            view.text = text != null ? text : "";
            view.offset = 0;
            this.fragment = fragment;

            return this;
        }

        private void advance(
            int consumed)
        {
            view.offset += consumed;
        }

        @Override
        public String getString()
        {
            return view.toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return view;
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBufferEx getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deferredBytes()
        {
            return fragment;
        }
    }

    private static final class LiteralView implements CharSequence
    {
        private String text;
        private int offset;

        @Override
        public int length()
        {
            return text.length() - offset;
        }

        @Override
        public char charAt(
            int index)
        {
            return text.charAt(offset + index);
        }

        @Override
        public CharSequence subSequence(
            int start,
            int end)
        {
            return text.substring(offset + start, offset + end);
        }

        @Override
        public String toString()
        {
            return text.substring(offset);
        }
    }

    private static final class MemberView implements CharSequence
    {
        private CharSequence key;
        private int from;
        private int to;

        MemberView wrap(
            CharSequence key,
            int from,
            int to)
        {
            this.key = key;
            this.from = from;
            this.to = to;

            return this;
        }

        @Override
        public int length()
        {
            return to - from;
        }

        @Override
        public char charAt(
            int index)
        {
            return key.charAt(from + index);
        }

        @Override
        public CharSequence subSequence(
            int start,
            int end)
        {
            return key.subSequence(from + start, from + end);
        }

        @Override
        public String toString()
        {
            return key.subSequence(from, to).toString();
        }
    }
}
