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
package io.aklivity.zilla.runtime.common.protobuf.json.internal;

import jakarta.json.JsonException;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.lang.Numbers;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufLocation;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParsingException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A {@link ProtobufParser} that reads JSON through a {@link JsonParserEx} and adapts it to the descriptor-
 * bound protobuf event cursor, applying the proto3 JSON mapping in reverse — json/proto field names to field
 * numbers, 64-bit integer strings and base64 {@code bytes} strings to wire values, enum names to numbers,
 * JSON arrays to {@code repeated} fields, JSON objects to messages or {@code map}s. It fits seamlessly as a
 * pure cursor feeding a {@link io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator}, or as the driver
 * of {@link io.aklivity.zilla.runtime.common.protobuf.Protobuf#stream(ProtobufParser)} into a wire
 * {@link io.aklivity.zilla.runtime.common.protobuf.ProtobufSink}.
 * <p>
 * Streaming: the JSON is pulled one {@link JsonEvent} at a time and translated incrementally, so input arrives
 * windowed — {@link #nextEvent(Mode)} returns {@code null} when a window is consumed mid-document and
 * {@link #resume} continues with the next window, exactly as the wire parser does. The translator carries no
 * document buffer; only the bounded per-message frame stack and a small pending-event ring. One JSON leaf value
 * must fit a single input window (it is read via the allocation-free {@link JsonParserEx#getStringView()}
 * view); message structure may split across windows at any token boundary.
 * <p>
 * Allocation: scalar values and keys are read through the parser's non-owning char views and parsed/encoded
 * straight into a reused buffer — integers via {@code Long.parseLong(CharSequence, …)} — so no per-value
 * {@code String}/{@code byte[]} is materialized (floats and enum names by string still round-trip through a
 * {@code String}).
 * <p>
 * Large {@code string}/{@code bytes} values stream as BOUNDED chunks through a small FIXED staging buffer
 * rather than being materialized whole: each {@code VALUE} event carries the next run of transcoded output
 * (UTF-8 for {@code string}, base64-decoded for {@code bytes}), aligned so a chunk never splits a code point
 * or base64 group, and {@link #deferredBytes()} reports the still-untranscoded remainder so the wire generator
 * keeps its length prefix open across chunks — mirroring the wire parser's length-delimited leaf streaming.
 */
public final class ProtobufJsonParserImpl implements ProtobufParser
{
    private static final int ESTIMATE = 1 << 16;
    // the fixed staging buffer that holds one bounded run of transcoded value bytes; a multiple of 3 so a
    // whole base64 4-char -> 3-byte group always fits without straddling the buffer end
    private static final int VALUE_CHUNK = 4092;

    private enum Kind
    {
        ROOT,
        MESSAGE,
        ARRAY,
        MAP
    }

    private final JsonParserEx parser;
    private final ProtobufSchema schema;
    private final String messageName;
    private final boolean rejectUnknownFields;
    private final UnsafeBufferEx estimateView;
    private final UnsafeBufferEx valueChunk;
    private final UnsafeBufferEx valueView;
    private final ProtobufLocation location = new ProtobufLocation()
    {
        @Override
        public long getStreamOffset()
        {
            return parser.getLocation().getStreamOffset();
        }
    };

    private Frame[] frames;
    private int depth;

    private ProtobufEvent[] queueEvent;
    private ProtobufField[] queueField;
    private ProtobufMessage[] queueMessage;
    private int queueHead;
    private int queueSize;

    private ProtobufEvent currentEvent;
    private ProtobufField currentField;
    private ProtobufMessage currentMessage;

    private long longValue;
    private double doubleValue;
    private float floatValue;
    // bytes of transcoded output currently staged in valueChunk
    private int decodedLength;
    // bytes of the current chunk the sink has drained; the output-pushback cursor within the chunk
    private int valueConsumed;

    // streaming-value state: a large string/bytes value is transcoded incrementally into valueChunk and
    // delivered as bounded VALUE chunks, the source chars cursored across without re-pulling the JSON parser
    private boolean valueStreaming;
    private boolean valueString;
    private CharSequence valueSource;
    private int valueCursor;
    private int valueSourceLength;
    // decoded bytes not yet transcoded into a chunk; reported as deferredBytes() so the generator's length
    // prefix counts the whole value and stays open until the last chunk
    private int valueRemaining;
    private ProtobufField valueField;
    private boolean valueClosesMapEntry;

    private boolean last;
    private boolean primed;
    private boolean finished;
    private boolean skipping;
    private boolean skipPrimed;
    private int skipDepth;

    public ProtobufJsonParserImpl(
        JsonParserEx parser,
        ProtobufSchema schema,
        String messageName)
    {
        this(parser, schema, messageName, false);
    }

    public ProtobufJsonParserImpl(
        JsonParserEx parser,
        ProtobufSchema schema,
        String messageName,
        boolean rejectUnknownFields)
    {
        this.parser = parser;
        this.schema = schema;
        this.messageName = messageName;
        this.rejectUnknownFields = rejectUnknownFields;
        this.estimateView = new UnsafeBufferEx(new byte[ESTIMATE]);
        this.valueChunk = new UnsafeBufferEx(new byte[VALUE_CHUNK]);
        this.valueView = new UnsafeBufferEx();
        this.frames = new Frame[8];
        for (int i = 0; i < frames.length; i++)
        {
            frames[i] = new Frame();
        }
        this.queueEvent = new ProtobufEvent[16];
        this.queueField = new ProtobufField[16];
        this.queueMessage = new ProtobufMessage[16];
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public ProtobufParser wrap(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last)
    {
        if (schema.message(messageName) == null)
        {
            throw new ProtobufParsingException("unknown message " + messageName);
        }
        this.last = last;
        depth = -1;
        queueHead = 0;
        queueSize = 0;
        valueConsumed = 0;
        decodedLength = 0;
        valueStreaming = false;
        valueSource = null;
        primed = false;
        finished = false;
        skipping = false;
        currentEvent = null;
        currentField = null;
        currentMessage = null;
        // a fresh document rewinds the reused JSON parser to DOC_START; window swaps (resume) do not
        parser.reset();
        parser.wrap(buffer, offset, limit);
        return this;
    }

    @Override
    public ProtobufParser resume(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last)
    {
        this.last = last;
        parser.wrap(buffer, offset, limit);
        return this;
    }

    @Override
    public boolean hasNext()
    {
        return queueSize > 0 || !finished;
    }

    @Override
    public ProtobufLocation getLocation()
    {
        return location;
    }

    @Override
    public int remaining()
    {
        return parser.remaining();
    }

    @Override
    public ProtobufEvent nextEvent(
        Mode mode)
    {
        ProtobufEvent event;
        if (queueSize == 0 && !produce())
        {
            event = null;
        }
        else
        {
            currentEvent = queueEvent[queueHead];
            currentField = queueField[queueHead];
            currentMessage = queueMessage[queueHead];
            queueHead = (queueHead + 1) % queueEvent.length;
            queueSize--;
            event = currentEvent;
        }
        return event;
    }

    @Override
    public ProtobufField field()
    {
        return currentField;
    }

    @Override
    public ProtobufMessage message()
    {
        return currentMessage;
    }

    @Override
    public int fieldNumber()
    {
        return currentField != null ? currentField.number() : -1;
    }

    @Override
    public ProtobufWireType wireType()
    {
        return currentField != null ? currentField.type().wireType() : null;
    }

    @Override
    public long longValue()
    {
        return longValue;
    }

    @Override
    public double doubleValue()
    {
        return doubleValue;
    }

    @Override
    public float floatValue()
    {
        return floatValue;
    }

    @Override
    public DirectBuffer segment()
    {
        DirectBuffer segment;
        if (currentEvent == ProtobufEvent.START_MESSAGE || currentEvent == ProtobufEvent.START_GROUP)
        {
            segment = estimateView;
        }
        else
        {
            // the current chunk's undrained bytes; on a within-chunk output pushback (resume) this re-exposes
            // [valueConsumed, decodedLength) so the sink continues where it left off
            valueView.wrap(valueChunk, valueConsumed, decodedLength - valueConsumed);
            segment = valueView;
        }
        return segment;
    }

    @Override
    public int deferredBytes()
    {
        // decoded value bytes still to come after this chunk's exposed slice: the not-yet-transcoded source
        // remainder. > 0 while the value continues, 0 only on the last chunk, so the generator's length prefix
        // (total = chunk length + deferred on the first call) is correct and the value stays open across chunks
        return valueRemaining;
    }

    @Override
    public void consumed(
        int sourceBytes)
    {
        // advance past the written prefix so segment() re-exposes this chunk's unconsumed remainder on resume;
        // a chunk drained short of decodedLength is output back-pressure — the next chunk is not staged until
        // the sink fully drains this one (handled by streamValueStep on the ADVANCED re-entry)
        valueConsumed += sourceBytes;
    }

    private boolean produce()
    {
        boolean starved = false;
        while (queueSize == 0 && !starved)
        {
            starved = !step();
        }
        return queueSize > 0;
    }

    private boolean step()
    {
        boolean progress;
        if (valueStreaming)
        {
            progress = streamValueStep();
        }
        else if (!primed)
        {
            progress = prime();
        }
        else if (skipping)
        {
            progress = skipStep();
        }
        else
        {
            Frame frame = frames[depth];
            switch (frame.kind)
            {
            case ROOT:
            case MESSAGE:
                progress = messageStep(frame);
                break;
            case ARRAY:
                progress = arrayStep(frame);
                break;
            case MAP:
                progress = mapStep(frame);
                break;
            default:
                progress = false;
                break;
            }
        }
        return progress;
    }

    private boolean prime()
    {
        JsonEvent token = pull();
        boolean progress;
        if (token == null)
        {
            progress = starve();
        }
        else if (token == JsonEvent.START_DOCUMENT)
        {
            // the event stream opens with a document frame before the root value; skip it
            progress = true;
        }
        else if (token == JsonEvent.START_OBJECT)
        {
            primed = true;
            ProtobufMessage root = schema.message(messageName);
            push(Kind.ROOT, root, null, false, false);
            enqueue(ProtobufEvent.START_MESSAGE, null, root);
            progress = true;
        }
        else
        {
            throw new ProtobufParsingException("expected json object");
        }
        return progress;
    }

    private boolean messageStep(
        Frame frame)
    {
        boolean progress;
        if (frame.pendingField != null)
        {
            progress = valueStep(frame);
        }
        else
        {
            JsonEvent token = pull();
            if (token == null)
            {
                progress = starve();
            }
            else if (token == JsonEvent.KEY_NAME)
            {
                ProtobufField field = frame.message.field(parser.getStringView());
                if (field == null)
                {
                    if (rejectUnknownFields)
                    {
                        throw new ProtobufParsingException("unknown field " + parser.getStringView());
                    }
                    beginSkip();
                }
                else
                {
                    frame.pendingField = field;
                }
                progress = true;
            }
            else if (token == JsonEvent.END_OBJECT)
            {
                closeMessage(frame);
                progress = true;
            }
            else
            {
                throw new ProtobufParsingException("expected json key or object end");
            }
        }
        return progress;
    }

    private boolean valueStep(
        Frame frame)
    {
        ProtobufField field = frame.pendingField;
        JsonEvent token = pull();
        boolean progress;
        if (token == null)
        {
            progress = starve();
        }
        else
        {
            frame.pendingField = null;
            dispatchValue(field, token);
            progress = true;
        }
        return progress;
    }

    private boolean arrayStep(
        Frame frame)
    {
        JsonEvent token = pull();
        boolean progress;
        if (token == null)
        {
            progress = starve();
        }
        else if (token == JsonEvent.END_ARRAY)
        {
            depth--;
            progress = true;
        }
        else
        {
            ProtobufField field = frame.field;
            if (field.composite())
            {
                expectStartObject(token);
                boolean group = field.type() == ProtobufType.GROUP;
                enqueue(ProtobufEvent.FIELD, field, null);
                push(Kind.MESSAGE, field.message(), null, group, false);
                enqueue(group ? ProtobufEvent.START_GROUP : ProtobufEvent.START_MESSAGE, null, field.message());
            }
            else if (token != JsonEvent.VALUE_NULL)
            {
                enqueue(ProtobufEvent.FIELD, field, null);
                emitValue(field, token, false);
            }
            progress = true;
        }
        return progress;
    }

    private boolean mapStep(
        Frame frame)
    {
        boolean progress;
        if (frame.mapStep == 0)
        {
            JsonEvent token = pull();
            if (token == null)
            {
                progress = starve();
            }
            else if (token == JsonEvent.END_OBJECT)
            {
                depth--;
                progress = true;
            }
            else if (token == JsonEvent.KEY_NAME)
            {
                ProtobufMessage entry = frame.message;
                ProtobufField keyField = entry.field(1);
                enqueue(ProtobufEvent.FIELD, frame.field, null);
                enqueue(ProtobufEvent.START_MESSAGE, null, entry);
                enqueue(ProtobufEvent.FIELD, keyField, null);
                emitKey(keyField);
                frame.mapStep = 1;
                progress = true;
            }
            else
            {
                throw new ProtobufParsingException("expected map key or object end");
            }
        }
        else
        {
            JsonEvent token = pull();
            if (token == null)
            {
                progress = starve();
            }
            else
            {
                ProtobufField valueField = frame.message.field(2);
                frame.mapStep = 0;
                if (valueField.composite())
                {
                    expectStartObject(token);
                    boolean group = valueField.type() == ProtobufType.GROUP;
                    enqueue(ProtobufEvent.FIELD, valueField, null);
                    push(Kind.MESSAGE, valueField.message(), null, group, true);
                    enqueue(group ? ProtobufEvent.START_GROUP : ProtobufEvent.START_MESSAGE, null, valueField.message());
                }
                else if (token == JsonEvent.VALUE_NULL)
                {
                    enqueue(ProtobufEvent.END_MESSAGE, null, null);
                }
                else
                {
                    enqueue(ProtobufEvent.FIELD, valueField, null);
                    emitValue(valueField, token, true);
                }
                progress = true;
            }
        }
        return progress;
    }

    private void dispatchValue(
        ProtobufField field,
        JsonEvent token)
    {
        if (token == JsonEvent.VALUE_NULL)
        {
            // proto3 JSON: a null value leaves the field absent
        }
        else if (map(field))
        {
            expectStartObject(token);
            push(Kind.MAP, field.message(), field, false, false);
        }
        else if (field.repeated())
        {
            if (token != JsonEvent.START_ARRAY)
            {
                throw new ProtobufParsingException("expected json array");
            }
            push(Kind.ARRAY, null, field, false, false);
        }
        else if (field.composite())
        {
            expectStartObject(token);
            boolean group = field.type() == ProtobufType.GROUP;
            enqueue(ProtobufEvent.FIELD, field, null);
            push(Kind.MESSAGE, field.message(), null, group, false);
            enqueue(group ? ProtobufEvent.START_GROUP : ProtobufEvent.START_MESSAGE, null, field.message());
        }
        else
        {
            enqueue(ProtobufEvent.FIELD, field, null);
            emitValue(field, token, false);
        }
    }

    private void closeMessage(
        Frame frame)
    {
        depth--;
        enqueue(frame.group ? ProtobufEvent.END_GROUP : ProtobufEvent.END_MESSAGE, null, null);
        if (frame.kind == Kind.ROOT)
        {
            finished = true;
        }
        if (frame.closesMapEntry)
        {
            enqueue(ProtobufEvent.END_MESSAGE, null, null);
        }
    }

    private boolean skipStep()
    {
        JsonEvent token = pull();
        boolean progress;
        if (token == null)
        {
            progress = starve();
        }
        else
        {
            if (!skipPrimed)
            {
                skipPrimed = true;
                if (token == JsonEvent.START_OBJECT || token == JsonEvent.START_ARRAY)
                {
                    skipDepth = 1;
                }
                else
                {
                    skipping = false;
                }
            }
            else if (token == JsonEvent.START_OBJECT || token == JsonEvent.START_ARRAY)
            {
                skipDepth++;
            }
            else if (token == JsonEvent.END_OBJECT || token == JsonEvent.END_ARRAY)
            {
                skipDepth--;
                skipping = skipDepth != 0;
            }
            progress = true;
        }
        return progress;
    }

    private void beginSkip()
    {
        skipping = true;
        skipPrimed = false;
        skipDepth = 0;
    }

    private boolean starve()
    {
        if (last)
        {
            throw new ProtobufParsingException("truncated json");
        }
        return false;
    }

    // the JSON-advance boundary: a malformed or non-JSON window makes the underlying jakarta parser throw a
    // JsonException (JsonParsingException for a syntax error), which would otherwise escape the pipeline; turn
    // it into a ProtobufParsingException so it rejects cleanly through the ProtobufException catch like any
    // parse failure
    private JsonEvent pull()
    {
        JsonEvent event;
        try
        {
            event = parser.hasNextEvent() ? parser.nextEvent() : null;
        }
        catch (JsonException ex)
        {
            throw new ProtobufParsingException("Invalid Protobuf event", ex);
        }
        return event;
    }

    // a field value: a string/bytes value begins a bounded streaming run (FIELD already enqueued), every other
    // scalar decodes whole and enqueues its single VALUE. closesMapEntry defers the map-entry END_MESSAGE until
    // the (possibly multi-chunk) value finishes
    private void emitValue(
        ProtobufField field,
        JsonEvent token,
        boolean closesMapEntry)
    {
        switch (field.type())
        {
        case STRING:
            beginStringStream(field, parser.getStringView(), closesMapEntry);
            break;
        case BYTES:
            beginBytesStream(field, parser.getStringView(), closesMapEntry);
            break;
        default:
            decodeScalar(field, token);
            enqueue(ProtobufEvent.VALUE, field, null);
            if (closesMapEntry)
            {
                enqueue(ProtobufEvent.END_MESSAGE, null, null);
            }
            break;
        }
    }

    // a map key (FIELD already enqueued, no map-entry END_MESSAGE follows): a string key streams; the other
    // legal key types decode whole into longValue with key-specific semantics
    private void emitKey(
        ProtobufField keyField)
    {
        CharSequence text = parser.getStringView();
        switch (keyField.type())
        {
        case STRING:
            beginStringStream(keyField, text, false);
            break;
        case BOOL:
            longValue = "true".contentEquals(text) ? 1L : 0L;
            enqueue(ProtobufEvent.VALUE, keyField, null);
            break;
        case UINT32:
        case FIXED32:
            longValue = parseLong(text) & 0xffffffffL;
            enqueue(ProtobufEvent.VALUE, keyField, null);
            break;
        case UINT64:
        case FIXED64:
            longValue = parseUnsignedLong(text);
            enqueue(ProtobufEvent.VALUE, keyField, null);
            break;
        default:
            longValue = parseLong(text);
            enqueue(ProtobufEvent.VALUE, keyField, null);
            break;
        }
    }

    private void decodeScalar(
        ProtobufField field,
        JsonEvent token)
    {
        switch (field.type())
        {
        case INT32:
        case SINT32:
        case SFIXED32:
        case INT64:
        case SINT64:
        case SFIXED64:
            longValue = parseLong(parser.getStringView());
            break;
        case UINT32:
        case FIXED32:
            longValue = parseLong(parser.getStringView()) & 0xffffffffL;
            break;
        case UINT64:
        case FIXED64:
            longValue = parseUnsignedLong(parser.getStringView());
            break;
        case BOOL:
            longValue = token == JsonEvent.VALUE_TRUE ? 1L : 0L;
            break;
        case FLOAT:
            floatValue = Numbers.parseFloat(parser.getStringView());
            break;
        case DOUBLE:
            doubleValue = Numbers.parseDouble(parser.getStringView());
            break;
        case ENUM:
            longValue = token == JsonEvent.VALUE_STRING
                ? enumNumber(field, parser.getStringView().toString())
                : parseLong(parser.getStringView());
            break;
        default:
            throw new ProtobufParsingException("unsupported scalar type " + field.type());
        }
    }

    // open a bounded UTF-8 streaming run over the value's chars: stage the first chunk, set deferredBytes() to
    // the still-untranscoded byte count (a whole-string UTF-8 length scan, allocation-free), and enqueue the
    // first VALUE. The whole value's chars are in-window (getStringView requires the leaf present), so this is
    // a transcode-progress cursor over them, not input back-pressure
    private void beginStringStream(
        ProtobufField field,
        CharSequence value,
        boolean closesMapEntry)
    {
        valueStreaming = true;
        valueString = true;
        valueSource = value;
        valueCursor = 0;
        valueSourceLength = value.length();
        valueField = field;
        valueClosesMapEntry = closesMapEntry;
        valueRemaining = utf8Length(value);
        refillStringChunk();
        enqueue(ProtobufEvent.VALUE, field, null);
    }

    // open a bounded base64-decode streaming run: the decoded length is computed from the base64 length and
    // padding without decoding, so the first chunk reports the correct deferred remainder
    private void beginBytesStream(
        ProtobufField field,
        CharSequence value,
        boolean closesMapEntry)
    {
        valueStreaming = true;
        valueString = false;
        valueSource = value;
        valueCursor = 0;
        valueSourceLength = base64SignificantLength(value);
        valueField = field;
        valueClosesMapEntry = closesMapEntry;
        valueRemaining = base64DecodedLength(value, valueSourceLength);
        refillBytesChunk();
        enqueue(ProtobufEvent.VALUE, field, null);
    }

    // drive the in-flight streaming value: when the current chunk is fully drained, stage the next bounded run
    // (resetting the output-pushback cursor) and enqueue another VALUE; when the source is exhausted, close the
    // run and emit the deferred map-entry END_MESSAGE if any
    private boolean streamValueStep()
    {
        if (valueCursor < valueSourceLength)
        {
            if (valueString)
            {
                refillStringChunk();
            }
            else
            {
                refillBytesChunk();
            }
            enqueue(ProtobufEvent.VALUE, valueField, null);
        }
        else
        {
            valueStreaming = false;
            valueSource = null;
            if (valueClosesMapEntry)
            {
                enqueue(ProtobufEvent.END_MESSAGE, null, null);
            }
        }
        return true;
    }

    // encode whole UTF-8 code points into valueChunk until the next code point would not fit; advance the char
    // cursor and decrement the still-to-come (deferred) byte count by exactly what was staged
    private void refillStringChunk()
    {
        valueConsumed = 0;
        CharSequence value = valueSource;
        int length = valueSourceLength;
        int i = valueCursor;
        int index = 0;
        boolean room = true;
        while (i < length && room)
        {
            int codePoint = value.charAt(i);
            int next = i + 1;
            if (codePoint >= 0xd800 && codePoint <= 0xdbff && next < length)
            {
                char low = value.charAt(next);
                if (low >= 0xdc00 && low <= 0xdfff)
                {
                    codePoint = ((codePoint - 0xd800) << 10) + (low - 0xdc00) + 0x10000;
                    next++;
                }
            }
            int width = codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
            if (index + width > VALUE_CHUNK)
            {
                room = false;
            }
            else
            {
                if (codePoint < 0x80)
                {
                    valueChunk.putByte(index++, (byte) codePoint);
                }
                else if (codePoint < 0x800)
                {
                    valueChunk.putByte(index++, (byte) (0xc0 | codePoint >> 6));
                    valueChunk.putByte(index++, (byte) (0x80 | codePoint & 0x3f));
                }
                else if (codePoint < 0x10000)
                {
                    valueChunk.putByte(index++, (byte) (0xe0 | codePoint >> 12));
                    valueChunk.putByte(index++, (byte) (0x80 | codePoint >> 6 & 0x3f));
                    valueChunk.putByte(index++, (byte) (0x80 | codePoint & 0x3f));
                }
                else
                {
                    valueChunk.putByte(index++, (byte) (0xf0 | codePoint >> 18));
                    valueChunk.putByte(index++, (byte) (0x80 | codePoint >> 12 & 0x3f));
                    valueChunk.putByte(index++, (byte) (0x80 | codePoint >> 6 & 0x3f));
                    valueChunk.putByte(index++, (byte) (0x80 | codePoint & 0x3f));
                }
                i = next;
            }
        }
        valueCursor = i;
        decodedLength = index;
        valueRemaining -= index;
    }

    // decode whole base64 4-char -> 3-byte groups into valueChunk until the next group would not fit; the final
    // group (with '='/'==' padding -> 1-2 bytes) is decoded on the last chunk. VALUE_CHUNK is a multiple of 3
    // so a whole group always fits up to the buffer end
    private void refillBytesChunk()
    {
        valueConsumed = 0;
        CharSequence value = valueSource;
        int length = valueSourceLength;
        int i = valueCursor;
        int index = 0;
        boolean room = true;
        while (i < length && room)
        {
            if (index + 3 > VALUE_CHUNK)
            {
                room = false;
            }
            else
            {
                int avail = length - i;
                int s0 = base64Decode(value.charAt(i));
                int s1 = base64Decode(value.charAt(i + 1));
                if (avail == 2)
                {
                    // final group, two significant chars (one '=' or unpadded) -> 1 byte
                    valueChunk.putByte(index++, (byte) (s0 << 2 | s1 >> 4));
                }
                else if (avail == 3)
                {
                    // final group, three significant chars (one '=' or unpadded) -> 2 bytes
                    int s2 = base64Decode(value.charAt(i + 2));
                    valueChunk.putByte(index++, (byte) (s0 << 2 | s1 >> 4));
                    valueChunk.putByte(index++, (byte) (s1 << 4 | s2 >> 2));
                }
                else
                {
                    int s2 = base64Decode(value.charAt(i + 2));
                    int s3 = base64Decode(value.charAt(i + 3));
                    valueChunk.putByte(index++, (byte) (s0 << 2 | s1 >> 4));
                    valueChunk.putByte(index++, (byte) (s1 << 4 | s2 >> 2));
                    valueChunk.putByte(index++, (byte) (s2 << 6 | s3));
                }
                i += Math.min(avail, 4);
            }
        }
        valueCursor = i;
        decodedLength = index;
        valueRemaining -= index;
    }

    private int base64Decode(
        int ch)
    {
        int value;
        if (ch >= 'A' && ch <= 'Z')
        {
            value = ch - 'A';
        }
        else if (ch >= 'a' && ch <= 'z')
        {
            value = ch - 'a' + 26;
        }
        else if (ch >= '0' && ch <= '9')
        {
            value = ch - '0' + 52;
        }
        else if (ch == '+' || ch == '-')
        {
            value = 62;
        }
        else if (ch == '/' || ch == '_')
        {
            value = 63;
        }
        else
        {
            throw new ProtobufParsingException("invalid base64 character " + ch);
        }
        return value;
    }

    // the count of significant (non-padding) base64 characters: the whole length less any 1-2 trailing '='. The
    // final group's decoded width is then derived from how many significant chars remain (2 -> 1 byte, 3 -> 2)
    private static int base64SignificantLength(
        CharSequence value)
    {
        int length = value.length();
        int pad = 0;
        if (length > 0 && value.charAt(length - 1) == '=')
        {
            pad++;
            if (length > 1 && value.charAt(length - 2) == '=')
            {
                pad++;
            }
        }
        return length - pad;
    }

    // decoded byte count for a base64 string given its significant length: full groups yield 3 bytes, a 2-char
    // tail yields 1 byte, a 3-char tail yields 2 bytes — matching JDK Base64 decode
    private static int base64DecodedLength(
        CharSequence value,
        int significant)
    {
        int groups = significant / 4;
        int tail = significant - groups * 4;
        int extra = tail == 0 ? 0 : tail - 1;
        return groups * 3 + extra;
    }

    // the UTF-8 byte length of the char sequence, allocation-free, with the same surrogate-pair handling as the
    // encoder so the total reported up front matches the bytes later staged
    private static int utf8Length(
        CharSequence value)
    {
        int length = value.length();
        int bytes = 0;
        int i = 0;
        while (i < length)
        {
            int codePoint = value.charAt(i++);
            if (codePoint >= 0xd800 && codePoint <= 0xdbff && i < length)
            {
                char low = value.charAt(i);
                if (low >= 0xdc00 && low <= 0xdfff)
                {
                    codePoint = ((codePoint - 0xd800) << 10) + (low - 0xdc00) + 0x10000;
                    i++;
                }
            }
            bytes += codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
        }
        return bytes;
    }

    private static long parseLong(
        CharSequence value)
    {
        return Long.parseLong(value, 0, value.length(), 10);
    }

    private static long parseUnsignedLong(
        CharSequence value)
    {
        return Long.parseUnsignedLong(value, 0, value.length(), 10);
    }

    private int enumNumber(
        ProtobufField field,
        String name)
    {
        Integer number = field.enumeration() != null ? field.enumeration().number(name) : null;
        if (number == null)
        {
            throw new ProtobufParsingException("unknown enum value " + name);
        }
        return number;
    }

    private void expectStartObject(
        JsonEvent token)
    {
        if (token != JsonEvent.START_OBJECT)
        {
            throw new ProtobufParsingException("expected json object");
        }
    }

    private void enqueue(
        ProtobufEvent event,
        ProtobufField field,
        ProtobufMessage message)
    {
        int tail = (queueHead + queueSize) % queueEvent.length;
        queueEvent[tail] = event;
        queueField[tail] = field;
        queueMessage[tail] = message;
        queueSize++;
    }

    private void push(
        Kind kind,
        ProtobufMessage message,
        ProtobufField field,
        boolean group,
        boolean closesMapEntry)
    {
        depth++;
        if (depth == frames.length)
        {
            Frame[] grown = new Frame[frames.length * 2];
            System.arraycopy(frames, 0, grown, 0, frames.length);
            for (int i = frames.length; i < grown.length; i++)
            {
                grown[i] = new Frame();
            }
            frames = grown;
        }
        frames[depth].set(kind, message, field, group, closesMapEntry);
    }

    private static boolean map(
        ProtobufField field)
    {
        ProtobufMessage message = field.message();
        return field.repeated() && message != null && message.mapEntry();
    }

    private static final class Frame
    {
        private Kind kind;
        private boolean group;
        private boolean closesMapEntry;
        private ProtobufMessage message;
        private ProtobufField field;
        private ProtobufField pendingField;
        private int mapStep;

        private void set(
            Kind kind,
            ProtobufMessage message,
            ProtobufField field,
            boolean group,
            boolean closesMapEntry)
        {
            this.kind = kind;
            this.message = message;
            this.field = field;
            this.group = group;
            this.closesMapEntry = closesMapEntry;
            this.pendingField = null;
            this.mapStep = 0;
        }
    }
}
