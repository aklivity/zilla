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
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;
import io.aklivity.zilla.runtime.common.json.internal.json.JsonValues;

public final class JsonParserImpl implements JsonParserEx
{
    private final InputStream in;
    private final DirectBufferInputStreamEx ownedInput;
    private final JsonTokenizer tokenizer;
    private final JsonLocationImpl location;
    private final UnsafeBuffer segmentView = new UnsafeBuffer(0, 0);

    private Event currentEvent;
    private JsonEvent lastEvent;
    private DocState docState = DocState.NOT_STARTED;
    private SegmentState segmentState = SegmentState.NONE;
    private long frameBaseStreamOffset;
    private long frameEndStreamOffset;
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
    private final StringView stringViewRO = new StringView();

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
        this.tokenizer = new JsonTokenizer();
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
            !(in instanceof DirectBufferInputStreamEx));
        this.location = new JsonLocationImpl(tokenizer);
    }

    @Override
    public JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        frameBaseStreamOffset = tokenizer.streamOffset();
        frameEndStreamOffset = frameBaseStreamOffset + length;
        tokenizer.window(length);
        ownedInput.wrap(buffer, offset, length);
        return this;
    }

    // Wraps the next input window of a chunked feed; last == true marks the final window, so its EOF is the
    // terminal delimiter (completing a trailing scalar, rejecting a truncated value) rather than a frame
    // boundary with more bytes to come.
    @Override
    public JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last)
    {
        tokenizer.terminal(last);
        return wrap(buffer, offset, length);
    }

    @Override
    public int remaining()
    {
        return (int)(frameEndStreamOffset - tokenizer.streamOffset());
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
                lastEvent == JsonEvent.VALUE_STRING && !tokenizer.stringVerbatim())
            {
                // structured scalar (number lexeme or canonical string): the sink renders it from the
                // decoded char view, so reset the char cursor; the raw segment slice is not used
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
        // a decoded scalar advances its char cursor; a verbatim string or raw segment its byte cursor
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
        // while rendering a structured scalar, expose the unconsumed char remainder so a resumed write
        // continues from where the bounded output left off; otherwise (a key, or a fresh value) the full
        // decoded view
        return charScalar()
            ? stringViewRO.wrap(tokenizer.stringView(), stringViewOffset)
            : tokenizer.stringView();
    }

    // True while the current scalar is delivered decoded (rendered canonically by the sink from its char
    // view): a number lexeme or a canonical string. A verbatim value-string is delivered as a SEGMENT, so
    // any VALUE_STRING here is canonical; derived from the delivered event, valid while parked on resume.
    private boolean charScalar()
    {
        return lastEvent == JsonEvent.VALUE_NUMBER || lastEvent == JsonEvent.VALUE_STRING;
    }

    @Override
    public boolean isIntegralNumber()
    {
        assert currentEvent == Event.VALUE_NUMBER;
        final CharSequence v = numberLexeme();
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
        if (tokenizer.numberFragmented())
        {
            throw new IllegalStateException("number spans multiple windows; use getBigDecimal()");
        }
        final CharSequence lexeme = tokenizer.stringView();
        return Integer.parseInt(lexeme, 0, lexeme.length(), 10);
    }

    @Override
    public long getLong()
    {
        assert currentEvent == Event.VALUE_NUMBER;
        if (tokenizer.numberFragmented())
        {
            throw new IllegalStateException("number spans multiple windows; use getBigDecimal()");
        }
        final CharSequence lexeme = tokenizer.stringView();
        return Long.parseLong(lexeme, 0, lexeme.length(), 10);
    }

    @Override
    public BigDecimal getBigDecimal()
    {
        assert currentEvent == Event.VALUE_NUMBER;
        return new BigDecimal(numberLexeme().toString());
    }

    // the current number's full lexeme; the char view (not stringValue()), so a caller that only scans
    // it — e.g. isIntegralNumber() — materializes no String
    private CharSequence numberLexeme()
    {
        return tokenizer.numberFragmented() ? tokenizer.numberLexeme() : tokenizer.stringView();
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
        case VALUE_NUMBER -> JsonValues.numberLiteral(numberLexeme().toString());
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
}
