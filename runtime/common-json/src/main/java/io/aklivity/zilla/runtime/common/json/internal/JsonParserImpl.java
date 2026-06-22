/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json.internal;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParsingException;

import org.agrona.DirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;
import io.aklivity.zilla.runtime.common.json.JsonStep;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;
import io.aklivity.zilla.runtime.common.json.internal.json.JsonValues;

public final class JsonParserImpl implements JsonParserEx
{
    // a non-negative integer lexeme of at most this many digits is within long range; getInt()/getLong()
    // reject anything longer as too large for a primitive, directing the caller to getBigDecimal()
    private static final int LONG_DIGITS = 19;

    // default cap on the decoded chars retained for one value/key when no MAX_VALUE_SIZE config is given:
    // generous enough for any reasonable value, bounding only adversarial accumulation
    private static final int DEFAULT_MAX_VALUE_SIZE = 1 << 24;

    private final InputStream in;
    private final DirectBufferInputStreamEx ownedInput;
    private final JsonTokenizer tokenizer;
    private final JsonLocationImpl location;
    private final UnsafeBufferEx segmentView = new UnsafeBufferEx(0, 0);
    private final UnsafeBufferEx verbatimView = new UnsafeBufferEx(0, 0);

    private Event currentEvent;
    private JsonEvent lastEvent;
    private DocState docState = DocState.NOT_STARTED;
    private SegmentState segmentState = SegmentState.NONE;
    private long frameBaseStreamOffset;
    private long segmentStartOffset;
    private int segmentSliceOffset;
    private int segmentSliceLength;
    private int segmentConsumed;
    private int segmentDepth;
    private boolean armNextValue;
    // running char cursor into the decoded chars of the current canonical value-string: getStringView()
    // exposes the unconsumed remainder from here and consumed() advances it, so a resumed bounded write
    // continues where the output bound left off
    private int stringViewOffset;
    // stream offset up to which verbatim bytes have already been pulled via getVerbatim(): the run not yet
    // handed out is [verbatimCursor, parse-frontier); each bounded pull advances this cursor by what it
    // returned, so the source can discard flushed bytes and retain only the unpulled remainder
    private long verbatimCursor;
    // armed by skipValue() when the dropped member was its container's first: the next verbatim pull trims
    // the leading separator off the new-first surviving sibling so the survivors stay well-formed
    private boolean trimLeadingSeparator;
    private final StringView stringViewRO = new StringView();
    // structural step log of the current verbatim run: each delivered body event's step kind plus the stream
    // offset at the end of its token, so getVerbatim() can bound a block on a token boundary. stepHead is the
    // first step not yet handed out by a block; the log compacts once a prior run has been fully drained
    private JsonStep[] stepKinds = new JsonStep[16];
    private long[] stepEnds = new long[16];
    private int stepCount;
    private int stepHead;
    private final StepCursor cursor = new StepCursor();
    private final Block block = new Block();

    private enum SegmentState
    {
        NONE,
        PENDING_START,
        SCANNING
    }

    private enum DocState
    {
        NOT_STARTED,
        STARTED,
        ENDED
    }

    public JsonParserImpl()
    {
        this(Map.of());
    }

    public JsonParserImpl(
        Map<String, ?> config)
    {
        this.ownedInput = new DirectBufferInputStreamEx();
        this.in = ownedInput;
        this.tokenizer = new JsonTokenizer(false, maxValueSize(config));
        this.location = new JsonLocationImpl(tokenizer);
    }

    public JsonParserImpl(
        InputStream in)
    {
        this(in, Map.of());
    }

    public JsonParserImpl(
        InputStream in,
        Map<String, ?> config)
    {
        if (!in.markSupported())
        {
            throw new IllegalArgumentException("InputStream must support mark/reset");
        }
        this.in = in;
        this.ownedInput = null;
        // A DirectBufferInputStreamEx is a resumable frame source whose EOF is a frame boundary;
        // any other stream is one-shot, so its EOF is the terminal delimiter for a trailing number.
        this.tokenizer = new JsonTokenizer(
            !(in instanceof DirectBufferInputStreamEx), maxValueSize(config));
        this.location = new JsonLocationImpl(tokenizer);
    }

    private static int maxValueSize(
        Map<String, ?> config)
    {
        final Object value = config.get(JsonParserEx.MAX_VALUE_SIZE);
        return value instanceof Integer size ? size : DEFAULT_MAX_VALUE_SIZE;
    }

    @Override
    public JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        frameBaseStreamOffset = tokenizer.streamOffset();
        tokenizer.window(limit - offset);
        ownedInput.wrap(buffer, offset, limit - offset);
        return this;
    }

    // Wraps the next input window [offset, limit) of a chunked feed; last == true marks the final window, so
    // its EOF is the terminal delimiter (completing a trailing scalar, rejecting a truncated value) rather
    // than a frame boundary with more bytes to come.
    @Override
    public JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last)
    {
        tokenizer.terminal(last);
        return wrap(buffer, offset, limit);
    }

    @Override
    public int remaining()
    {
        return (int)(frameBaseStreamOffset + ownedInput.length() - tokenizer.streamOffset());
    }

    @Override
    public void reset()
    {
        tokenizer.reset();
        segmentState = SegmentState.NONE;
        segmentDepth = 0;
        segmentConsumed = 0;
        armNextValue = false;
        stringViewOffset = 0;
        verbatimCursor = 0;
        trimLeadingSeparator = false;
        stepCount = 0;
        stepHead = 0;
        lastEvent = null;
        currentEvent = null;
        docState = DocState.NOT_STARTED;
    }

    @Override
    public boolean hasNext()
    {
        boolean result;
        if (segmentState == SegmentState.PENDING_START)
        {
            result = true;
        }
        else
        {
            result = tokenizerHasNext();
        }
        return result;
    }

    private boolean tokenizerHasNext()
    {
        boolean result;
        if (tokenizer.event() != null)
        {
            result = true;
        }
        else
        {
            // before resuming a value that spans windows, tell the tokenizer how much of the prior
            // fragment the consumer took (the char cursor) so it keeps the unconsumed remainder and
            // accumulates rather than discarding a declined fragment
            if (tokenizer.fragmenting())
            {
                tokenizer.markScratchConsumed(stringViewOffset);
            }
            try
            {
                result = tokenizer.advance(in);
            }
            catch (IOException ex)
            {
                throw new JsonParsingException(ex.getMessage(), ex, location);
            }
        }
        return result;
    }

    private int bufferOffset(
        long streamOffset)
    {
        return ownedInput.offset() + (int)(streamOffset - frameBaseStreamOffset);
    }

    @Override
    public Event next()
    {
        if (tokenizer.event() == null && !tokenizerHasNext())
        {
            throw new JsonParsingException("No more events", location);
        }
        Event e = tokenizer.event();
        tokenizer.clearEvent();
        currentEvent = e;
        return e;
    }

    @Override
    public Event currentEvent()
    {
        return currentEvent;
    }

    @Override
    public JsonEvent nextEvent(
        Mode mode)
    {
        if (mode == Mode.SEGMENTED)
        {
            // arm the just-delivered boundary to stream verbatim before pulling its events (the mode-driven
            // peer of a stage's JsonController.segmentable(); keyed off the previously delivered event)
            if (lastEvent == JsonEvent.START_OBJECT || lastEvent == JsonEvent.START_ARRAY)
            {
                segmentStartOffset = tokenizer.streamOffset() - 1;
                segmentState = SegmentState.PENDING_START;
                segmentDepth = 1;
                tokenizer.segmenting(true);
            }
            else if (lastEvent == JsonEvent.START_DOCUMENT)
            {
                armNextValue = true;
                // a bare top-level string then streams verbatim too; cleared by the tokenizer for a non-string
                tokenizer.scalarSegment(true);
            }
            else if (lastEvent == JsonEvent.KEY_NAME)
            {
                // arm the upcoming value-string to stream verbatim; the tokenizer clears it for a non-string
                tokenizer.scalarSegment(true);
            }
        }
        JsonEvent event;
        if (!hasNextEvent())
        {
            // window consumed mid-document, or the document already ended: no event this pull
            event = null;
        }
        else if (docState == DocState.NOT_STARTED)
        {
            docState = DocState.STARTED;
            event = JsonEvent.START_DOCUMENT;
        }
        else if (segmentState == SegmentState.NONE && !tokenizerHasNext() && tokenizer.done())
        {
            docState = DocState.ENDED;
            event = JsonEvent.END_DOCUMENT;
        }
        else
        {
            event = nextToken();
        }
        if (event != null)
        {
            // lastEvent is the canonical delivered event (the basis for the streaming-only accessor asserts);
            // currentEvent is its jakarta projection (null for a segment or document framing), keeping the
            // shared jakarta getters' asserts valid whether driven by the pipeline or the raw parser
            lastEvent = event;
            currentEvent = toEvent(event);
            if (isBodyEvent(event))
            {
                // record the run's structural step for getVerbatim(); framing and
                // segment events are not verbatim steps, and skipValue()'s internal next() never reaches here
                appendStep(event);
            }
        }
        return event;
    }

    private static Event toEvent(
        JsonEvent event)
    {
        return switch (event)
        {
        case START_OBJECT -> Event.START_OBJECT;
        case END_OBJECT -> Event.END_OBJECT;
        case START_ARRAY -> Event.START_ARRAY;
        case END_ARRAY -> Event.END_ARRAY;
        case KEY_NAME -> Event.KEY_NAME;
        case VALUE_STRING -> Event.VALUE_STRING;
        case VALUE_NUMBER -> Event.VALUE_NUMBER;
        case VALUE_TRUE -> Event.VALUE_TRUE;
        case VALUE_FALSE -> Event.VALUE_FALSE;
        case VALUE_NULL -> Event.VALUE_NULL;
        default -> null;
        };
    }

    @Override
    public boolean hasNextEvent()
    {
        boolean result;
        if (docState == DocState.NOT_STARTED)
        {
            result = true;
        }
        else if (docState == DocState.ENDED)
        {
            result = false;
        }
        else if (segmentState == SegmentState.PENDING_START)
        {
            result = true;
        }
        else if (tokenizerHasNext())
        {
            result = true;
        }
        else
        {
            result = tokenizer.done();
        }
        return result;
    }

    private JsonEvent nextToken()
    {
        JsonEvent event;
        switch (segmentState)
        {
        case PENDING_START:
            event = scanSegment(bufferOffset(segmentStartOffset));
            break;
        case SCANNING:
            event = scanSegment(ownedInput.offset());
            break;
        default:
            lastEvent = JsonEvent.of(next());
            if (lastEvent == JsonEvent.VALUE_NUMBER ||
                lastEvent == JsonEvent.KEY_NAME ||
                lastEvent == JsonEvent.VALUE_STRING && !tokenizer.stringVerbatim())
            {
                // a decoded char-scalar (number lexeme, canonical string, or object key): the sink renders it
                // from the decoded char view, so reset the shared char cursor as it is delivered; the raw
                // segment slice is not used
                stringViewOffset = 0;
            }
            else if (lastEvent == JsonEvent.VALUE_STRING)
            {
                // verbatim string token (with quotes): delivered as a segment so the raw bytes are spliced
                // through the byte path; getSegment() is then valid only on this segmented event
                segmentSliceOffset = bufferOffset(tokenizer.valueStreamStart());
                segmentSliceLength = (int) (tokenizer.valueStreamEnd() - tokenizer.valueStreamStart());
                segmentConsumed = 0;
                lastEvent = JsonEvent.SEGMENT;
            }
            if (armNextValue)
            {
                armNextValue = false;
                if (lastEvent == JsonEvent.START_OBJECT || lastEvent == JsonEvent.START_ARRAY)
                {
                    segmentStartOffset = tokenizer.streamOffset() - 1;
                    segmentDepth = 1;
                    segmentState = SegmentState.PENDING_START;
                    tokenizer.segmenting(true);
                    event = scanSegment(bufferOffset(segmentStartOffset));
                }
                else
                {
                    event = lastEvent;
                }
            }
            else
            {
                event = lastEvent;
            }
            break;
        }
        return event;
    }

    // Scans the segmented value's raw bytes, emitting one SEGMENT per fragment: when the frame is
    // exhausted before the value closes it suspends as SCANNING (more fragments follow, deferredBytes
    // true); when structural depth returns to zero the value is complete (deferredBytes false).
    private JsonEvent scanSegment(
        int sliceStart)
    {
        JsonEvent event = null;
        while (event == null)
        {
            if (!tokenizerHasNext())
            {
                segmentSliceOffset = sliceStart;
                segmentSliceLength = ownedInput.offset() + ownedInput.length() - sliceStart;
                segmentConsumed = 0;
                segmentState = SegmentState.SCANNING;
                event = JsonEvent.SEGMENT;
            }
            else
            {
                Event token = next();
                if (token == Event.START_OBJECT || token == Event.START_ARRAY)
                {
                    segmentDepth++;
                }
                else if (token == Event.END_OBJECT || token == Event.END_ARRAY)
                {
                    segmentDepth--;
                }

                if (segmentDepth == 0)
                {
                    tokenizer.segmenting(false);
                    final int sliceEnd = bufferOffset(tokenizer.streamOffset());
                    segmentSliceOffset = sliceStart;
                    segmentSliceLength = sliceEnd - sliceStart;
                    segmentConsumed = 0;
                    segmentState = SegmentState.NONE;
                    event = JsonEvent.SEGMENT;
                }
            }
        }
        return event;
    }

    @Override
    public void consumed(
        int sourceUnits)
    {
        // a decoded char-scalar (key or value) advances the shared char cursor; a verbatim string or raw
        // segment advances its byte cursor
        if (charScalar())
        {
            stringViewOffset += sourceUnits;
        }
        else
        {
            segmentConsumed += sourceUnits;
        }
    }

    @Override
    public boolean deferredBytes()
    {
        return segmentState == SegmentState.SCANNING || tokenizer.fragmenting();
    }

    @Override
    public String getString()
    {
        assert currentEvent == Event.KEY_NAME || currentEvent == Event.VALUE_STRING || currentEvent == Event.VALUE_NUMBER;
        return tokenizer.stringValue();
    }

    @Override
    public CharSequence getStringView()
    {
        // valid on both drives: the pump tracks lastEvent, a jakarta next() tracks currentEvent
        assert lastEvent == JsonEvent.VALUE_STRING || lastEvent == JsonEvent.VALUE_NUMBER ||
            lastEvent == JsonEvent.KEY_NAME ||
            currentEvent == Event.VALUE_STRING || currentEvent == Event.VALUE_NUMBER ||
            currentEvent == Event.KEY_NAME;
        // while rendering a decoded char-scalar (key or value), expose the unconsumed char remainder from the
        // shared cursor so a resumed write continues from where the bounded output left off; otherwise (a
        // verbatim/segment value) the full decoded view
        return charScalar()
            ? stringViewRO.wrap(tokenizer.stringView(), stringViewOffset)
            : tokenizer.stringView();
    }

    // True while the current scalar is delivered decoded (rendered canonically by the sink from its char
    // view): a number lexeme, a canonical string, or an object key — all share the one char cursor
    // (stringViewOffset), which is reset as each is delivered, since only one is ever active at a time. A
    // verbatim value-string is delivered as a SEGMENT, so any VALUE_STRING here is canonical; derived from the
    // delivered event, valid while parked on resume.
    private boolean charScalar()
    {
        return lastEvent == JsonEvent.VALUE_NUMBER ||
            lastEvent == JsonEvent.VALUE_STRING ||
            lastEvent == JsonEvent.KEY_NAME;
    }

    @Override
    public boolean isIntegralNumber()
    {
        assert currentEvent == Event.VALUE_NUMBER;
        // the current number's char view (not stringValue()), so scanning it materializes no String; with
        // consumed(0) accumulation a value the caller needs whole is fully present in scratch by this point
        final CharSequence v = tokenizer.stringView();
        if (v == null)
        {
            throw new IllegalStateException("Not a number");
        }
        boolean integral = true;
        for (int i = 0; integral && i < v.length(); i++)
        {
            final char c = v.charAt(i);
            integral = c != '.' && c != 'e' && c != 'E';
        }
        return integral;
    }

    @Override
    public int getInt()
    {
        assert currentEvent == Event.VALUE_NUMBER;
        assert !deferredBytes();
        final CharSequence lexeme = tokenizer.stringView();
        if (integerDigits(lexeme) > LONG_DIGITS)
        {
            throw new IllegalStateException("number exceeds long range; use getBigDecimal()");
        }
        return Integer.parseInt(lexeme, 0, lexeme.length(), 10);
    }

    @Override
    public long getLong()
    {
        assert currentEvent == Event.VALUE_NUMBER;
        assert !deferredBytes();
        final CharSequence lexeme = tokenizer.stringView();
        if (integerDigits(lexeme) > LONG_DIGITS)
        {
            throw new IllegalStateException("number exceeds long range; use getBigDecimal()");
        }
        return Long.parseLong(lexeme, 0, lexeme.length(), 10);
    }

    @Override
    public BigDecimal getBigDecimal()
    {
        assert currentEvent == Event.VALUE_NUMBER;
        // getBigDecimal() yields the whole value, so the lexeme must be complete; a caller that needs a
        // value still spanning windows must first gather it (e.g. push back via consumed(0)) and only
        // read it once the deferred bytes have arrived
        assert !deferredBytes();
        return new BigDecimal(tokenizer.stringView().toString());
    }

    // count of leading integer digits after an optional sign; a longer integer lexeme cannot fit a long,
    // so getInt()/getLong() reject it rather than overflow, directing the caller to getBigDecimal()
    private static int integerDigits(
        CharSequence lexeme)
    {
        int index = lexeme.length() > 0 && lexeme.charAt(0) == '-' ? 1 : 0;
        int digits = 0;
        while (index < lexeme.length() && lexeme.charAt(index) >= '0' && lexeme.charAt(index) <= '9')
        {
            index++;
            digits++;
        }
        return digits;
    }

    @Override
    public JsonLocation getLocation()
    {
        return location;
    }

    @Override
    public DirectBuffer getSegment()
    {
        assert lastEvent != null && lastEvent.segmented();
        // re-expose the unconsumed remainder of the segment slice after consumed() pushback, append-only
        segmentView.wrap(ownedInput.buffer(), segmentSliceOffset + segmentConsumed, segmentSliceLength - segmentConsumed);
        return segmentView;
    }

    @Override
    public JsonVerbatim getVerbatim(
        int limit)
    {
        // verbatim consumption of a structured run; a segment's bytes are pulled via getSegment() instead
        assert lastEvent != null && !lastEvent.segmented();
        // drop any steps already behind the cursor — a skipValue() jumps the cursor past the dropped member's
        // own steps
        while (stepHead < stepCount && stepEnds[stepHead] <= verbatimCursor)
        {
            stepHead++;
        }
        // a prior skipValue() of a container's first member leaves the new-first survivor with a dangling
        // leading separator; trim it (and any leading whitespace) off the front of this run, and drop the
        // SEPARATOR step the trimmed comma stood for so the block's structure matches its trimmed bytes
        if (trimLeadingSeparator)
        {
            trimLeadingSeparator();
            trimLeadingSeparator = false;
            if (stepHead < stepCount && stepKinds[stepHead] == JsonStep.SEPARATOR)
            {
                stepHead++;
            }
        }
        // the run not yet pulled is [verbatimCursor, parse-frontier); when it fits the byte limit drain all of
        // it — to the frontier, so trailing bytes consumed in end-of-window lookahead (a separator or whitespace
        // past the last token) are not stranded into a replaced window — otherwise bound to the highest token
        // boundary at or before the limit, so a partial block's bytes and structure still agree on a token
        final long frontier = tokenizer.streamOffset();
        final long budget = verbatimCursor + limit;
        final int from = stepHead;
        final long byteEnd;
        int last;
        if (frontier <= budget)
        {
            byteEnd = frontier;
            last = stepCount - 1;
        }
        else
        {
            last = from - 1;
            for (int index = from; index < stepCount && stepEnds[index] <= budget; index++)
            {
                last = index;
            }
            byteEnd = last >= from ? stepEnds[last] : verbatimCursor;
        }
        final int length = (int) (byteEnd - verbatimCursor);
        verbatimView.wrap(ownedInput.buffer(), bufferOffset(verbatimCursor), length);
        block.wrap(from, last);
        verbatimCursor = byteEnd;
        stepHead = last + 1;
        return block;
    }

    @Override
    public void skipValue()
    {
        // valid only at an object member boundary (current event is a delivered KEY_NAME); the value has not
        // yet been read, so the member's leading-separator occupancy is still the key's
        assert lastEvent == JsonEvent.KEY_NAME;
        final boolean occupied = tokenizer.memberSeparated();
        // drive the value's events internally so the caller never sees them: a scalar is one event, a container
        // runs to its matching close
        final Event value = next();
        if (value == Event.START_OBJECT || value == Event.START_ARRAY)
        {
            int depth = 1;
            while (depth > 0)
            {
                final Event token = next();
                if (token == Event.START_OBJECT || token == Event.START_ARRAY)
                {
                    depth++;
                }
                else if (token == Event.END_OBJECT || token == Event.END_ARRAY)
                {
                    depth--;
                }
            }
        }
        // discard the whole member by advancing the verbatim cursor to the value's end: when occupied this also
        // drops the dropped member's leading separator (the surviving sibling carries its own); when the dropped
        // member was the container's first, defer trimming the next survivor's now-dangling leading separator
        verbatimCursor = tokenizer.streamOffset();
        trimLeadingSeparator = !occupied;
    }

    // Advances the verbatim cursor past the new-first survivor's leading whitespace and a single separator
    // comma if present — bounded to the parse frontier so an empty container (the dropped member had no
    // surviving sibling) consumes only its trailing whitespace, leaving the bare close to render {@code {}}.
    private void trimLeadingSeparator()
    {
        final long frontier = tokenizer.streamOffset();
        final DirectBuffer buffer = ownedInput.buffer();
        while (verbatimCursor < frontier && isWhitespace(buffer.getByte(bufferOffset(verbatimCursor))))
        {
            verbatimCursor++;
        }
        if (verbatimCursor < frontier && buffer.getByte(bufferOffset(verbatimCursor)) == ',')
        {
            verbatimCursor++;
        }
    }

    private static boolean isWhitespace(
        byte b)
    {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }

    // Records a delivered body event as one or more steps of the current verbatim run, each tagged with the
    // stream offset at the end of its token so getVerbatim() can bound a block on a token boundary. A separated
    // member/element is preceded by a SEPARATOR step (the comma carried in the source bytes). The log compacts
    // once a prior run has been fully drained by getVerbatim() (stepHead caught up to stepCount).
    private void appendStep(
        JsonEvent kind)
    {
        if (stepHead == stepCount)
        {
            stepCount = 0;
            stepHead = 0;
        }
        final long end = tokenizer.streamOffset();
        // a separated member/element carries its comma in the source bytes; the tokenizer reports it one-shot
        // per token, so emit exactly one SEPARATOR step ahead of that token — no container tracking needed
        if (tokenizer.separatorBefore())
        {
            append(JsonStep.SEPARATOR, end);
        }
        append(toStep(kind), end);
    }

    private void append(
        JsonStep step,
        long end)
    {
        if (stepCount == stepKinds.length)
        {
            stepKinds = Arrays.copyOf(stepKinds, stepCount * 2);
            stepEnds = Arrays.copyOf(stepEnds, stepCount * 2);
        }
        stepKinds[stepCount] = step;
        stepEnds[stepCount] = end;
        stepCount++;
    }

    private static JsonStep toStep(
        JsonEvent kind)
    {
        return switch (kind)
        {
        case START_OBJECT -> JsonStep.START_OBJECT;
        case END_OBJECT -> JsonStep.END_OBJECT;
        case START_ARRAY -> JsonStep.START_ARRAY;
        case END_ARRAY -> JsonStep.END_ARRAY;
        case KEY_NAME -> JsonStep.KEY_NAME;
        default -> JsonStep.VALUE;
        };
    }

    private static boolean isBodyEvent(
        JsonEvent event)
    {
        return event == JsonEvent.START_OBJECT || event == JsonEvent.END_OBJECT ||
            event == JsonEvent.START_ARRAY || event == JsonEvent.END_ARRAY ||
            event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER ||
            event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_FALSE || event == JsonEvent.VALUE_NULL;
    }

    @Override
    public void close()
    {
    }

    @Override
    public JsonValue getValue()
    {
        if (currentEvent == null)
        {
            throw new IllegalStateException("Parser is not positioned on a value");
        }
        return switch (currentEvent)
        {
        case START_OBJECT -> getObject();
        case START_ARRAY -> getArray();
        case VALUE_STRING, KEY_NAME -> JsonValues.string(getString());
        case VALUE_NUMBER -> JsonValues.numberLiteral(tokenizer.stringView().toString());
        case VALUE_TRUE -> JsonValue.TRUE;
        case VALUE_FALSE -> JsonValue.FALSE;
        case VALUE_NULL -> JsonValue.NULL;
        default -> throw new IllegalStateException("Parser is not positioned on a value: " + currentEvent);
        };
    }

    @Override
    public JsonObject getObject()
    {
        if (currentEvent != Event.START_OBJECT)
        {
            throw new IllegalStateException("Parser is not positioned on START_OBJECT");
        }

        JsonObjectBuilder object = JsonValues.objectBuilder();
        Event event = next();
        while (event != Event.END_OBJECT)
        {
            if (event != Event.KEY_NAME)
            {
                throw new JsonParsingException("Expected object key", location);
            }
            String key = getString();
            next();
            object.add(key, getValue());
            event = next();
        }
        return object.build();
    }

    @Override
    public JsonArray getArray()
    {
        if (currentEvent != Event.START_ARRAY)
        {
            throw new IllegalStateException("Parser is not positioned on START_ARRAY");
        }

        JsonArrayBuilder array = JsonValues.arrayBuilder();
        Event event = next();
        while (event != Event.END_ARRAY)
        {
            array.add(getValue());
            event = next();
        }
        return array.build();
    }

    @Override
    public Stream<JsonValue> getArrayStream()
    {
        if (currentEvent != Event.START_ARRAY)
        {
            throw new IllegalStateException("Parser is not positioned on START_ARRAY");
        }
        return StreamSupport.stream(new ArrayElementSpliterator(), false);
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream()
    {
        if (currentEvent != Event.START_OBJECT)
        {
            throw new IllegalStateException("Parser is not positioned on START_OBJECT");
        }
        return StreamSupport.stream(new ObjectEntrySpliterator(), false);
    }

    @Override
    public Stream<JsonValue> getValueStream()
    {
        return StreamSupport.stream(new TopLevelValueSpliterator(), false);
    }

    @Override
    public void skipObject()
    {
        if (tokenizer.inObjectContext())
        {
            skipStructure();
        }
    }

    @Override
    public void skipArray()
    {
        if (tokenizer.inArrayContext())
        {
            skipStructure();
        }
    }

    private void skipStructure()
    {
        int depth = 1;
        while (depth > 0 && hasNext())
        {
            Event event = next();
            if (event == Event.START_OBJECT || event == Event.START_ARRAY)
            {
                depth++;
            }
            else if (event == Event.END_OBJECT || event == Event.END_ARRAY)
            {
                depth--;
            }
        }
    }

    private final class ArrayElementSpliterator extends Spliterators.AbstractSpliterator<JsonValue>
    {
        private ArrayElementSpliterator()
        {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
        }

        @Override
        public boolean tryAdvance(
            Consumer<? super JsonValue> action)
        {
            boolean advanced = false;
            if (next() != Event.END_ARRAY)
            {
                action.accept(getValue());
                advanced = true;
            }
            return advanced;
        }
    }

    private final class ObjectEntrySpliterator extends Spliterators.AbstractSpliterator<Map.Entry<String, JsonValue>>
    {
        private ObjectEntrySpliterator()
        {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
        }

        @Override
        public boolean tryAdvance(
            Consumer<? super Map.Entry<String, JsonValue>> action)
        {
            boolean advanced = false;
            if (next() != Event.END_OBJECT)
            {
                String key = getString();
                next();
                action.accept(new AbstractMap.SimpleImmutableEntry<>(key, getValue()));
                advanced = true;
            }
            return advanced;
        }
    }

    private final class TopLevelValueSpliterator extends Spliterators.AbstractSpliterator<JsonValue>
    {
        private TopLevelValueSpliterator()
        {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
        }

        @Override
        public boolean tryAdvance(
            Consumer<? super JsonValue> action)
        {
            boolean advanced = false;
            if (hasNext())
            {
                next();
                action.accept(getValue());
                advanced = true;
            }
            return advanced;
        }
    }

    // Reusable, allocation-free char view onto the decoded string remainder: wraps the tokenizer's decoded
    // chars at a running offset so a bounded write can resume from where it left off without copying.
    private static final class StringView implements CharSequence
    {
        private CharSequence base;
        private int offset;

        private StringView wrap(
            CharSequence base,
            int offset)
        {
            this.base = base;
            this.offset = offset;
            return this;
        }

        @Override
        public int length()
        {
            return base.length() - offset;
        }

        @Override
        public char charAt(
            int index)
        {
            return base.charAt(offset + index);
        }

        @Override
        public CharSequence subSequence(
            int start,
            int end)
        {
            return base.subSequence(offset + start, offset + end);
        }

        @Override
        public String toString()
        {
            return base.subSequence(offset, base.length()).toString();
        }
    }

    // Reused, on-stack single-pass cursor over the step log for the current block: steps [from, from + count)
    // of the parser's array. remove() inherited from Iterator throws, so a caller cannot corrupt parser state.
    private final class StepCursor implements Iterator<JsonStep>
    {
        private int index;
        private int end;

        private StepCursor reset(
            int from,
            int last)
        {
            this.index = from;
            this.end = Math.max(from, last + 1);
            return this;
        }

        @Override
        public boolean hasNext()
        {
            return index < end;
        }

        @Override
        public JsonStep next()
        {
            return stepKinds[index++];
        }
    }

    // Reused, on-stack JsonVerbatim block: the contiguous run bytes and their structural transcript, both
    // bounded to the same whole-token prefix by getVerbatim(). getSteps() hands back the shared cursor reset to
    // this block's step range, so a fresh forward pass each call with no allocation. Valid on-stack only.
    private final class Block implements JsonVerbatim
    {
        private int from;
        private int last;

        private void wrap(
            int from,
            int last)
        {
            this.from = from;
            this.last = last;
        }

        @Override
        public Iterator<JsonStep> getSteps()
        {
            return cursor.reset(from, last);
        }

        @Override
        public DirectBuffer getSegment()
        {
            return verbatimView;
        }
    }
}
