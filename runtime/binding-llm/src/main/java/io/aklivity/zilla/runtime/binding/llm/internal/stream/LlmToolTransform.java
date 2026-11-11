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

import io.aklivity.zilla.runtime.binding.llm.config.LlmMember;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.binding.llm.internal.dialect.LlmDialectNames;
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
    protected static final int CAPTURE_VALUE = 21;
    protected static final int DESCEND_OR_REPLACE = 22;
    protected static final int DESCEND_OR_PASS = 23;
    protected static final int LIMIT_STRING = 24;
    protected static final int CAPTURE_SCALAR = 25;

    protected static final int MODE_OBJECT = 0;
    protected static final int MODE_EXTRACT = 1;
    protected static final int MODE_VALUE = 2;
    protected static final int MODE_ARRAY = 3;

    private static final String DATA_URI_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final int JUDGE_VALUE = 26;
    private static final int NONE = -1;
    private static final int BASE_SHIFT = 18;
    private static final int BASE_BITS = -1 << BASE_SHIFT;
    private static final int BIT_ELEMENT = 1 << BASE_SHIFT;
    private static final int BIT_NEST_KEY = 1 << BASE_SHIFT + 1;
    private static final int BIT_NEST_START = 1 << BASE_SHIFT + 2;
    private static final int BIT_MEMBER = 1 << BASE_SHIFT + 3;
    private static final int BIT_NEST_END = 1 << BASE_SHIFT + 4;
    private static final int BIT_PASS = 1 << BASE_SHIFT + 5;
    private static final int BIT_VALUE = 1 << BASE_SHIFT + 6;
    private static final int BIT_WRAP_START = 1 << BASE_SHIFT + 7;
    private static final int BIT_WRAP_END = 1 << BASE_SHIFT + 8;
    private static final int BIT_KEY = 1 << BASE_SHIFT + 9;
    private static final int BIT_OPEN = 1 << BASE_SHIFT + 10;
    private static final int BIT_CLOSE = 1 << BASE_SHIFT + 11;
    private static final int NO_BIT = 0;
    static final int HELD_MAX = 4096;
    private static final int NAMES_CAPACITY = 32;
    private static final String SHARED_DIALECT = "x";
    private static final int SHARED_PREFIX_LENGTH = SHARED_DIALECT.length();

    protected String target;

    private final int mode;
    private final String dialect;
    private final int extractDepth;
    private final LlmCapturedValue captured;
    private final LlmCapturedScalar scalar;
    private final LiteralSource literalSource;
    private final LiteralControl literalControl;
    private final StringBuilder heldBuilder;
    private final String[] names;
    private final MemberView sharedMember;

    private LlmModelCapability legality;

    private JsonController control;
    private JsonSource source;
    private JsonSink sink;
    private Status outcome;
    private int mask;
    private int inflight;
    private int looped;
    private int loopOrdinal;
    private int nameCount;
    private int depth;
    private int pending;
    private boolean passing;
    private boolean passEscaped;
    private int passDepth;
    private boolean skipping;
    private int skipDepth;
    private boolean holding;
    private boolean converting;
    private boolean limiting;
    private boolean overflowing;
    private int limitLength;
    private int limitUnits;
    private boolean wrapping;
    private boolean clamping;
    private boolean ranging;
    private int captureDepth;
    private int captureSize;
    private int verdict;
    private int captureLimit;
    private String captureMember;
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
    private LlmSchemaScanner scanner;
    private String scannedMember;

    protected LlmToolTransform()
    {
        this(MODE_OBJECT);
    }

    protected LlmToolTransform(
        int mode)
    {
        this(mode, null);
    }

    protected LlmToolTransform(
        int mode,
        String dialect)
    {
        this(mode, dialect, 1);
    }

    protected LlmToolTransform(
        int mode,
        String dialect,
        int extractDepth)
    {
        this(mode, dialect, extractDepth, null, null);
    }

    protected LlmToolTransform(
        int mode,
        String dialect,
        int extractDepth,
        LlmCapturedValue captured)
    {
        this(mode, dialect, extractDepth, captured, null);
    }

    protected LlmToolTransform(
        int mode,
        String dialect,
        int extractDepth,
        LlmCapturedValue captured,
        LlmCapturedScalar scalar)
    {
        this.mode = mode;
        this.dialect = dialect;
        this.extractDepth = extractDepth;
        this.captured = captured;
        this.scalar = scalar;
        this.captureLimit = HELD_MAX;
        this.literalSource = new LiteralSource();
        this.literalControl = new LiteralControl();
        this.heldBuilder = new StringBuilder();
        this.names = new String[NAMES_CAPACITY];
        this.sharedMember = new MemberView();
        this.legality = LlmModelCapability.UNSPECIFIED;
        this.pending = NONE;
        this.outcome = Status.ADVANCED;
    }

    protected abstract int member(
        int depth,
        CharSequence key);

    protected final void legality(
        LlmModelCapability legality)
    {
        this.legality = legality;
    }

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

    protected String adapted(
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

    protected final boolean pass(
        int bit,
        JsonEvent event)
    {
        return accept(owned(bit), event, null, true, false);
    }

    protected final boolean emit(
        int bit,
        JsonEvent event)
    {
        return accept(owned(bit), event, null, false, false);
    }

    protected final boolean emitFragment(
        int bit,
        JsonEvent event,
        String text)
    {
        return accept(owned(bit), event, text, false, true);
    }

    protected final boolean emit(
        int bit,
        JsonEvent event,
        String text)
    {
        return accept(owned(bit), event, text, false, false);
    }

    protected final boolean emitted(
        int bit)
    {
        return (mask & owned(bit)) != 0;
    }

    protected final int prefix(
        String scope,
        CharSequence key)
    {
        target = name(scope, key);

        return PASS_VALUE;
    }

    protected final int unprefix(
        CharSequence key)
    {
        final int length = key.length();
        int colon = 0;

        while (colon < length && key.charAt(colon) != ':')
        {
            colon++;
        }

        int action = PASS_VALUE;

        if (colon == length && matches(key, LlmCacheBreakpoint.MEMBER))
        {
            action = breakpoint(key);
        }
        else if (colon == length)
        {
            target = name(null, key);
        }
        else if (colon == SHARED_PREFIX_LENGTH && regionMatches(key, 0, colon, SHARED_DIALECT))
        {
            action = legal(sharedMember.wrap(key, colon + 1, length));
        }
        else if (LlmDialectNames.admits(key, 0, colon, dialect))
        {
            target = name(null, key, colon + 1, length);
        }
        else if (LlmBehaviour.listed(key))
        {
            action = judge(key);
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

    private int legal(
        CharSequence member)
    {
        final int listed = legality.unsupported != 0 ? LlmMember.of(member) : LlmMember.INVALID;

        return listed == LlmMember.INVALID || !legality.unsupported(listed)
            ? shared(member)
            : LlmMember.behavioural(listed) ? judge(member) : DROP;
    }

    private int judge(
        CharSequence member)
    {
        target = null;
        captureMember = name(member);
        captureDepth = 0;
        captureSize = 0;
        verdict = LlmLegality.UNDECIDED;
        rejectedMember = captureMember;

        return JUDGE_VALUE;
    }

    protected int breakpoint(
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

    protected final int limitString(
        int length)
    {
        limitLength = length;
        limitUnits = length << 1;

        return LIMIT_STRING;
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

    protected final void scan(
        LlmSchemaScanner scanner,
        String member)
    {
        this.scanner = scanner;
        this.scannedMember = member;
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

    protected final int capture(
        CharSequence member)
    {
        captureMember = name(member);
        captureDepth = 0;
        captured.clear();

        return CAPTURE_VALUE;
    }

    protected final int captureScalar(
        CharSequence member)
    {
        captureMember = name(member);
        captureDepth = 0;
        scalar.clear();

        return CAPTURE_SCALAR;
    }

    protected final void captureLimit(
        int limit)
    {
        captureLimit = limit;
    }

    protected final String capturedMember()
    {
        return captureMember;
    }

    protected final Status rejected(
        CharSequence member)
    {
        rejectedMember = name(member);

        return Status.REJECTED;
    }

    protected Status onCaptured(
        LlmCapturedValue value)
    {
        return Status.REJECTED;
    }

    protected Status onScalar(
        LlmCapturedScalar value)
    {
        return Status.REJECTED;
    }

    protected final boolean emitCaptured(
        int bit,
        LlmCapturedValue value,
        int index)
    {
        final int end = value.end(index);
        boolean delivered = (mask & owned(bit)) != 0;

        if (!delivered)
        {
            delivered = true;

            for (int at = index + looped; delivered && at < end; at++)
            {
                final String text = value.text(at);

                delivered = write(bit, value.event(at), text, false, false, false);

                if (delivered)
                {
                    looped++;
                }
            }

            if (delivered)
            {
                mask |= bit;
                looped = 0;
            }
        }

        return delivered;
    }

    /**
     * Emits one event of a loop whose number of emits depends on data. The whole loop shares the single bit
     * {@code loopBit}, set by {@link #endLoop(int)} once every emit of the loop has been delivered, plus a cursor of
     * delivered emits. Each call is identified by its position in the loop, so the loop must be a pure function of
     * its input: the same emits, in the same order, on every replay. Every emit of the loop goes through
     * {@code emitLoop} or {@link #emitLoopCaptured(int, LlmCapturedValue, int)}, including extra events emitted on only
     * some iterations. Returns true when the event was delivered now or earlier, false when the sink cannot take it.
     */
    protected final boolean emitLoop(
        int loopBit,
        JsonEvent event)
    {
        return acceptLoop(owned(loopBit), event, null);
    }

    protected final boolean emitLoop(
        int loopBit,
        JsonEvent event,
        String text)
    {
        return acceptLoop(owned(loopBit), event, text);
    }

    protected final boolean emitLoopCaptured(
        int loopBit,
        LlmCapturedValue value,
        int index)
    {
        final int loop = owned(loopBit);
        final int end = value.end(index);
        boolean delivered = true;

        for (int at = index; delivered && at < end; at++)
        {
            delivered = acceptLoop(loop, value.event(at), value.text(at));
        }

        return delivered;
    }

    /**
     * Closes the loop begun by its {@code emitLoop} calls: when every emit of the pass has been delivered it sets
     * {@code loopBit} and clears the cursor so a following loop starts from its first emit. Returns true when the loop
     * is delivered. Call it after the loop, even when the loop emitted nothing, and chain later emits and state
     * changes on its result.
     */
    protected final boolean endLoop(
        int loopBit)
    {
        final int loop = owned(loopBit);
        boolean delivered = (mask & loop) != 0;

        if (!delivered && loopOrdinal == looped)
        {
            mask |= loop;
            looped = 0;
            delivered = true;
        }

        loopOrdinal = 0;

        return delivered;
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
        outcome = Status.ADVANCED;
        mask = 0;
        inflight = NO_BIT;
        looped = 0;
        loopOrdinal = 0;
        depth = 0;
        pending = NONE;
        passing = false;
        passEscaped = false;
        skipping = false;
        holding = false;
        converting = false;
        limiting = false;
        overflowing = false;
        wrapping = false;
        clamping = false;
        ranging = false;
        captureDepth = 0;
        captureMember = null;
        nesting = false;
        nested = null;
        awaitingContent = false;
        valueStarted = false;
        arrayOpen = false;
        elementPending = false;
        held = null;
        rejectedMember = null;
        scanner = null;
        scannedMember = null;
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
        this.mask = 0;
        this.inflight = NO_BIT;
        this.looped = 0;

        return process(event);
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

        return process(event);
    }

    private Status process(
        JsonEvent event)
    {
        this.outcome = Status.ADVANCED;
        this.loopOrdinal = 0;

        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_DOCUMENT:
            relay(BIT_PASS, event);
            break;
        case END_DOCUMENT:
            status = onEndDocument();
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

        if (delivered())
        {
            commit();
        }

        return settle(status);
    }

    private Status settle(
        Status status)
    {
        return !delivered() || status == Status.ADVANCED ? outcome : status;
    }

    private Status onEndDocument()
    {
        final Status status = mode == MODE_VALUE ? valueEnd() : onEndObject();

        if (status == Status.ADVANCED)
        {
            relay(BIT_PASS, JsonEvent.END_DOCUMENT);
        }

        return status;
    }

    private Status onKey()
    {
        Status status = Status.ADVANCED;

        if (capturing())
        {
            status = captureText(JsonEvent.KEY_NAME);
        }
        else if (passing)
        {
            status = scan(JsonEvent.KEY_NAME);

            if (status == Status.ADVANCED)
            {
                relay(BIT_PASS, JsonEvent.KEY_NAME);
            }
        }
        else if (!skipping)
        {
            nested = null;

            final int action = member(depth, source.getStringView());

            if (delivered())
            {
                pending = action;

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
                if (mode == MODE_EXTRACT || put(BIT_KEY, JsonEvent.KEY_NAME, target))
                {
                    pending = NONE;
                    awaitingContent = true;
                }
            }
            else
            {
                status = Status.REJECTED;
            }
        }
        else if (awaitingContent && put(BIT_OPEN, JsonEvent.START_OBJECT) && put(BIT_CLOSE, JsonEvent.END_OBJECT))
        {
            awaitingContent = false;
        }

        return status;
    }

    private Status onValue(
        JsonEvent event)
    {
        Status status;

        if (skipping)
        {
            status = skip(event);
        }
        else if (capturing())
        {
            status = capture(event);
        }
        else if (awaitingContent)
        {
            status = event == JsonEvent.START_OBJECT ? startPass(event) : Status.REJECTED;

            if (delivered())
            {
                awaitingContent = false;
            }
        }
        else if (mode == MODE_VALUE && !valueStarted)
        {
            onBegin();

            status = Status.ADVANCED;

            if (delivered())
            {
                status = event == JsonEvent.START_OBJECT ? startPass(event) : Status.REJECTED;
            }

            if (delivered())
            {
                valueStarted = true;
            }
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
            if (relay(BIT_PASS, event))
            {
                arrayOpen = true;
            }

            status = Status.ADVANCED;
        }
        else if (event == JsonEvent.START_OBJECT && depth == 0 && (mode != MODE_ARRAY || arrayOpen))
        {
            boolean opened = true;

            if (mode == MODE_ARRAY)
            {
                elementPending = true;
            }
            else if (mode != MODE_EXTRACT)
            {
                opened = relay(BIT_PASS, event);
            }

            if (opened)
            {
                onOpen();

                if (delivered())
                {
                    depth++;
                }
            }

            status = Status.ADVANCED;
        }
        else if (event == JsonEvent.END_OBJECT && depth > 0)
        {
            status = onClose(depth);

            if (status == Status.ADVANCED && delivered() && (mode != MODE_ARRAY || depth != 1 || closeElement()))
            {
                depth--;
            }
        }
        else if (mode == MODE_ARRAY && event == JsonEvent.END_ARRAY && depth == 0 && arrayOpen)
        {
            if (relay(BIT_PASS, event))
            {
                arrayOpen = false;
            }

            status = Status.ADVANCED;
        }
        else if (depth > 0)
        {
            status = event == JsonEvent.END_ARRAY ? endElements(event) : beginElement(event);
        }

        return status;
    }

    private boolean closeElement()
    {
        final boolean opened = !elementPending || (mask & BIT_ELEMENT) != 0;
        final boolean closed = !opened || put(BIT_CLOSE, JsonEvent.END_OBJECT);

        if (closed)
        {
            elementPending = false;
        }

        return closed;
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
        if (relay(BIT_PASS, event) && (!nesting || depth - 1 != nestDepth || closeNested()))
        {
            depth--;
        }

        return Status.ADVANCED;
    }

    private Status begin(
        JsonEvent event)
    {
        final int action = pending;

        boolean starved = false;
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
        case DESCEND_OR_REPLACE:
            status = event == JsonEvent.START_OBJECT ? descendSilent() : replace(event);
            break;
        case DESCEND_OR_PASS:
            status = event == JsonEvent.START_OBJECT ? descendSilent() : beginPass(event);
            break;
        case REPLACE_VALUE:
            status = replace(event);
            break;
        case PASS_DATA_URI:
            status = event == JsonEvent.VALUE_STRING ? beginDataUri(event) : Status.REJECTED;
            break;
        case STRIP_DATA_URI:
            status = event == JsonEvent.VALUE_STRING ? beginStripped(event) : Status.REJECTED;
            starved = status == Status.STARVED;
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
        case LIMIT_STRING:
            limiting = event == JsonEvent.VALUE_STRING;
            status = limiting ? hold() : beginSkip(event);
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
            if (event != JsonEvent.START_OBJECT)
            {
                status = Status.REJECTED;
            }
            else if (escapeMember())
            {
                status = startPass(event);

                if (delivered())
                {
                    passEscaped = true;
                }
            }
            break;
        default:
            status = beginSkip(event);
            break;
        }

        if (delivered() && !starved)
        {
            pending = NONE;
        }

        return status;
    }

    private boolean escapeMember()
    {
        return put(BIT_KEY, JsonEvent.KEY_NAME, target) && put(BIT_OPEN, JsonEvent.START_ESCAPED);
    }

    private boolean capturing()
    {
        return pending == CAPTURE_VALUE || pending == CAPTURE_SCALAR || pending == JUDGE_VALUE;
    }

    private Status capture(
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        if (!attempted())
        {
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                captureDepth++;
                status = record(event, null);
                break;
            case END_OBJECT:
            case END_ARRAY:
                captureDepth--;
                status = record(event, null);
                break;
            case VALUE_STRING:
            case VALUE_NUMBER:
                status = captureText(event);
                break;
            default:
                status = record(event, null);
                break;
            }
        }

        if (status == Status.ADVANCED && captureDepth == 0)
        {
            status = switch (pending)
            {
            case JUDGE_VALUE -> judged();
            case CAPTURE_SCALAR -> onScalar(scalar);
            default -> onCaptured(captured);
            };

            if (delivered())
            {
                pending = NONE;
            }

            if (status == Status.REJECTED && rejectedMember == null)
            {
                rejectedMember = captureMember;
            }
        }

        return status;
    }

    private Status judged()
    {
        Status status = Status.REJECTED;

        if (LlmLegality.harmless(verdict))
        {
            rejectedMember = null;
            status = Status.ADVANCED;
        }

        return status;
    }

    private Status captureText(
        JsonEvent event)
    {
        final CharSequence view = source.getStringView();

        heldBuilder.append(view);
        control.consumed(view.length());

        Status status = Status.STARVED;

        if (heldBuilder.length() > captureLimit)
        {
            status = Status.REJECTED;
        }
        else if (!source.deferredBytes())
        {
            status = pending == JUDGE_VALUE ? decide(event, heldBuilder) : record(event, heldBuilder.toString());
            heldBuilder.setLength(0);
        }

        return status;
    }

    private Status record(
        JsonEvent event,
        String text)
    {
        return switch (pending)
        {
        case JUDGE_VALUE -> decide(event, text);
        case CAPTURE_SCALAR -> scalar.add(event, text, captureLimit) ? Status.ADVANCED : Status.REJECTED;
        default -> captured.add(event, text, captureLimit) ? Status.ADVANCED : Status.REJECTED;
        };
    }

    private Status decide(
        JsonEvent event,
        CharSequence text)
    {
        captureSize += LlmCapturedValue.cost(text);

        final boolean accepted = captureSize <= captureLimit;

        if (accepted)
        {
            verdict = LlmLegality.advance(captureMember, verdict, captureDepth, event, text);
        }

        return accepted ? Status.ADVANCED : Status.REJECTED;
    }

    private Status descendKept(
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        if ((mode == MODE_EXTRACT || target == null || emitMember()) && relay(BIT_PASS, event))
        {
            status = onDescend(depth + 1);

            if (delivered())
            {
                depth++;
            }
        }

        return status;
    }

    private Status descendSilent()
    {
        final Status status = onDescend(depth + 1);

        if (delivered())
        {
            depth++;
        }

        return status;
    }

    private Status descendElements(
        JsonEvent event)
    {
        if ((mode == MODE_EXTRACT || target == null || emitMember()) && relay(BIT_PASS, event))
        {
            depth++;
        }

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

    private boolean emitMember()
    {
        final boolean opening = nested != null && !nesting;

        final boolean written = (!opening ||
            put(BIT_NEST_KEY, JsonEvent.KEY_NAME, nested) && put(BIT_NEST_START, JsonEvent.START_OBJECT)) &&
            put(BIT_MEMBER, JsonEvent.KEY_NAME, target);

        if (written && opening)
        {
            nestDepth = depth;
        }

        return written;
    }

    private boolean closePassed()
    {
        return passDepth != nestDepth || closeNested();
    }

    private boolean closeNested()
    {
        return !(nesting || (mask & BIT_NEST_START) != 0) || put(BIT_NEST_END, JsonEvent.END_OBJECT);
    }

    private void commit()
    {
        nesting = (nesting || (mask & BIT_NEST_START) != 0) && (mask & BIT_NEST_END) == 0;

        if ((mask & BIT_ELEMENT) != 0)
        {
            elementPending = false;
        }
    }

    private String limited()
    {
        return !overflowing && heldBuilder.codePointCount(0, heldBuilder.length()) <= limitLength
            ? heldBuilder.toString()
            : null;
    }

    private boolean emitAdapted(
        String value)
    {
        final String adapted = value != null ? adapted(value) : null;

        return adapted == null || emitMember() && put(BIT_VALUE, JsonEvent.VALUE_STRING, adapted) && closeNested();
    }

    private boolean emitConverted(
        String value)
    {
        boolean written = target == null || emitMember();

        if (written)
        {
            written = wrapping
                ? put(BIT_WRAP_START, JsonEvent.START_ARRAY) &&
                    put(BIT_VALUE, JsonEvent.VALUE_STRING, converted(value)) &&
                    put(BIT_WRAP_END, JsonEvent.END_ARRAY)
                : put(BIT_VALUE, JsonEvent.VALUE_STRING, converted(value));
        }

        return written && closeNested();
    }

    private boolean emitRanged(
        String number)
    {
        final double value = Double.parseDouble(number);

        return !(value >= clampMinimum && value <= clampMaximum && value == Math.rint(value)) ||
            emitMember() && put(BIT_VALUE, JsonEvent.VALUE_NUMBER, number) && closeNested();
    }

    private boolean emitClamped(
        String number)
    {
        final double value = Double.parseDouble(number);
        final String bounded = value < clampMinimum
            ? Double.toString(clampMinimum)
            : value > clampMaximum
                ? Double.toString(clampMaximum)
                : number;

        return emitMember() && put(BIT_VALUE, JsonEvent.VALUE_NUMBER, bounded) && closeNested();
    }

    private Status replace(
        JsonEvent event)
    {
        Status status = onReplace(event);

        if (status == Status.ADVANCED && delivered())
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
            status = Status.ADVANCED;

            if (dataUriPrefix(mediaType))
            {
                status = startPass(event);
            }
        }

        return status;
    }

    private boolean dataUriPrefix(
        String mediaType)
    {
        return put(BIT_KEY, JsonEvent.KEY_NAME, target) &&
            putFragment(BIT_OPEN, JsonEvent.VALUE_STRING, DATA_URI_PREFIX + mediaType + BASE64_MARKER);
    }

    private Status beginStripped(
        JsonEvent event)
    {
        final DirectBufferEx mediaType = control.envelope().get("media-type", 0);
        Status status = Status.REJECTED;

        if (mediaType != null)
        {
            boolean stripped = attempted();

            if (!stripped)
            {
                final CharSequence view = source.getStringView();
                final int prefixLength = DATA_URI_PREFIX.length() + mediaType.capacity() + BASE64_MARKER.length();

                if (view.length() >= prefixLength)
                {
                    if (prefixMatches(view, mediaType))
                    {
                        control.consumed(prefixLength);
                        stripped = true;
                    }
                }
                else if (source.deferredBytes())
                {
                    control.consumed(0);
                    status = Status.STARVED;
                }
            }

            if (stripped)
            {
                status = beginPass(event);
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
        Status status = Status.ADVANCED;

        if (mode == MODE_EXTRACT && depth <= extractDepth || target == null || emitMember())
        {
            status = startPass(event);
        }

        return status;
    }

    private Status startPass(
        JsonEvent event)
    {
        final boolean deferred = source.deferredBytes();

        Status status = Status.ADVANCED;

        passDepth = depth;

        if (scanner != null && !attempted(BIT_PASS))
        {
            scanner.begin();
            status = scanned(event);
        }

        if (status == Status.ADVANCED && relay(BIT_PASS, event))
        {
            if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
            {
                passing = true;
                depth++;
            }
            else if (deferred)
            {
                passing = true;
            }
            else if (closePassed())
            {
                passing = false;
                scanner = null;
            }
        }

        return status;
    }

    private Status scanned(
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        if (scanner != null)
        {
            final boolean text = event == JsonEvent.KEY_NAME ||
                event == JsonEvent.VALUE_STRING ||
                event == JsonEvent.VALUE_NUMBER;
            final boolean deferred = event == JsonEvent.VALUE_STRING && source.deferredBytes();
            final String keyword = scanner.scan(event, text ? source.getStringView() : null, deferred);

            if (keyword != null)
            {
                rejectedMember = scannedMember + " keyword " + keyword;
                status = Status.REJECTED;
            }
        }

        return status;
    }

    private Status scan(
        JsonEvent event)
    {
        return scanner != null && !attempted(BIT_PASS) ? scanned(event) : Status.ADVANCED;
    }

    private Status carry(
        JsonEvent event)
    {
        final Status status = scan(event);

        if (status == Status.ADVANCED)
        {
            final boolean deferred = source.deferredBytes();
            final int level;
            final boolean continuing;

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                level = depth + 1;
                continuing = true;
                break;
            case END_OBJECT:
            case END_ARRAY:
                level = depth - 1;
                continuing = level != passDepth;
                break;
            default:
                level = depth;
                continuing = deferred || depth != passDepth;
                break;
            }

            if (relay(BIT_PASS, event) && (continuing || finishPass()))
            {
                depth = level;
                passing = continuing;

                if (!continuing)
                {
                    passEscaped = false;
                    scanner = null;
                }
            }
        }

        return status;
    }

    private Status valueEnd()
    {
        return valueStarted ? onEnd() : Status.REJECTED;
    }

    private boolean finishPass()
    {
        boolean finished = true;

        if (passEscaped)
        {
            finished = put(BIT_CLOSE, JsonEvent.END_ESCAPED);

            if (finished)
            {
                onEscapedEnd();
                finished = delivered();
            }
        }

        if (finished && mode == MODE_VALUE)
        {
            onValueEnd();
            finished = delivered();
        }

        return finished && closePassed();
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
        if (!attempted())
        {
            final CharSequence view = source.getStringView();

            if (limiting && !overflowing && heldBuilder.length() + view.length() > limitUnits)
            {
                overflowing = true;
                heldBuilder.setLength(0);
            }
            else if (!overflowing)
            {
                heldBuilder.append(view);
            }

            control.consumed(view.length());
        }

        Status status = Status.REJECTED;

        if (heldBuilder.length() <= HELD_MAX)
        {
            status = Status.STARVED;

            if (source.deferredBytes())
            {
                holding = true;
            }
            else
            {
                final String value = limiting ? limited() : name(heldBuilder);

                if (released(value))
                {
                    heldBuilder.setLength(0);
                    holding = false;
                }

                status = Status.ADVANCED;
            }
        }

        return status;
    }

    private boolean released(
        String value)
    {
        final boolean released;

        if (limiting)
        {
            released = emitAdapted(value);

            if (released)
            {
                limiting = false;
                overflowing = false;
            }
        }
        else if (converting)
        {
            released = emitConverted(value);

            if (released)
            {
                converting = false;
                wrapping = false;
            }
        }
        else if (ranging)
        {
            released = emitRanged(value);

            if (released)
            {
                ranging = false;
            }
        }
        else if (clamping)
        {
            released = emitClamped(value);

            if (released)
            {
                clamping = false;
            }
        }
        else
        {
            held = value;
            released = true;
        }

        return released;
    }

    private boolean relay(
        int bit,
        JsonEvent event)
    {
        return accept(bit, event, null, true, false);
    }

    private boolean put(
        int bit,
        JsonEvent event)
    {
        return accept(bit, event, null, false, false);
    }

    private boolean put(
        int bit,
        JsonEvent event,
        String text)
    {
        return accept(bit, event, text, false, false);
    }

    private boolean putFragment(
        int bit,
        JsonEvent event,
        String text)
    {
        return accept(bit, event, text, false, true);
    }

    private static int owned(
        int bit)
    {
        assert bit != NO_BIT && (bit & BASE_BITS) == 0 : "emit bit must be one of the low " + BASE_SHIFT + " bits";

        return bit;
    }

    private boolean delivered()
    {
        return outcome == Status.ADVANCED || outcome == Status.COMPLETED;
    }

    private boolean attempted()
    {
        return (mask | inflight) != 0;
    }

    private boolean attempted(
        int bit)
    {
        return (mask & bit) != 0 || inflight == bit;
    }

    private boolean acceptLoop(
        int loop,
        JsonEvent event,
        String text)
    {
        final int ordinal = loopOrdinal++;
        boolean accepted = (mask & loop) != 0 || ordinal < looped;

        if (!accepted && ordinal == looped && opened())
        {
            accepted = write(loop, event, text, false, false, false);

            if (accepted)
            {
                looped++;
            }
        }

        return accepted;
    }

    private boolean opened()
    {
        return !elementPending || (mask & BIT_ELEMENT) != 0 ||
            write(BIT_ELEMENT, JsonEvent.START_OBJECT, null, false, false, true);
    }

    private boolean accept(
        int bit,
        JsonEvent event,
        String text,
        boolean fromSource,
        boolean fragment)
    {
        return opened() && write(bit, event, text, fromSource, fragment, true);
    }

    private boolean write(
        int bit,
        JsonEvent event,
        String text,
        boolean fromSource,
        boolean fragment,
        boolean record)
    {
        boolean accepted = true;

        if ((mask & bit) == 0 || !record)
        {
            accepted = delivered();

            if (accepted)
            {
                final Status status = inflight == bit
                    ? fromSource
                        ? sink.resume(control, source, event)
                        : sink.resume(literalControl, literalSource, event)
                    : fromSource
                        ? sink.transform(control, source, event)
                        : sink.transform(literalControl, literalSource.with(text, fragment), event);

                accepted = status == Status.ADVANCED || status == Status.COMPLETED;
                inflight = status == Status.SUSPENDED ? bit : NO_BIT;

                if (accepted && record)
                {
                    mask |= bit;
                }

                if (status != Status.ADVANCED)
                {
                    outcome = status;
                }
            }
        }

        return accepted;
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
