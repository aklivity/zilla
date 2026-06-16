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

import java.util.Base64;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import org.agrona.ExpandableArrayBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
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
 * straight into a reused buffer — integers via {@code Long.parseLong(CharSequence, …)}, strings UTF-8-encoded
 * into the value buffer — so no per-value {@code String}/{@code byte[]} is materialized (floats, enum names by
 * string, and {@code bytes} base64 still round-trip through a {@code String}).
 */
public final class ProtobufJsonParserImpl implements ProtobufParser
{
    private static final int ESTIMATE = 1 << 16;

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
    private final UnsafeBufferEx estimateView;
    private final ExpandableArrayBuffer valueBuffer;
    private final UnsafeBufferEx valueView;

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
    private int valueLength;

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
        this.parser = parser;
        this.schema = schema;
        this.messageName = messageName;
        this.estimateView = new UnsafeBufferEx(new byte[ESTIMATE]);
        this.valueBuffer = new ExpandableArrayBuffer();
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
    public ProtobufParser wrap(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        if (schema.message(messageName) == null)
        {
            throw new ProtobufException("unknown message " + messageName);
        }
        this.last = last;
        depth = -1;
        queueHead = 0;
        queueSize = 0;
        primed = false;
        finished = false;
        skipping = false;
        currentEvent = null;
        currentField = null;
        currentMessage = null;
        // a fresh document rewinds the reused JSON parser to DOC_START; window swaps (resume) do not
        parser.reset();
        parser.wrap(buffer, offset, length);
        return this;
    }

    @Override
    public ProtobufParser resume(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        this.last = last;
        parser.wrap(buffer, offset, length);
        return this;
    }

    @Override
    public boolean hasNext()
    {
        return queueSize > 0 || !finished;
    }

    @Override
    public long position()
    {
        return parser.getLocation().getStreamOffset();
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
    public DirectBufferEx segment()
    {
        DirectBufferEx segment;
        if (currentEvent == ProtobufEvent.START_MESSAGE || currentEvent == ProtobufEvent.START_GROUP)
        {
            segment = estimateView;
        }
        else
        {
            valueView.wrap(valueBuffer, 0, valueLength);
            segment = valueView;
        }
        return segment;
    }

    @Override
    public int deferredBytes()
    {
        return 0;
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
        if (!primed)
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
            throw new ProtobufException("expected json object");
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
                throw new ProtobufException("expected json key or object end");
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
                decodeValue(field, token);
                enqueue(ProtobufEvent.VALUE, field, null);
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
                decodeKey(keyField);
                enqueue(ProtobufEvent.VALUE, keyField, null);
                frame.mapStep = 1;
                progress = true;
            }
            else
            {
                throw new ProtobufException("expected map key or object end");
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
                    decodeValue(valueField, token);
                    enqueue(ProtobufEvent.VALUE, valueField, null);
                    enqueue(ProtobufEvent.END_MESSAGE, null, null);
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
                throw new ProtobufException("expected json array");
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
            decodeValue(field, token);
            enqueue(ProtobufEvent.VALUE, field, null);
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
            throw new ProtobufException("truncated json");
        }
        return false;
    }

    private JsonEvent pull()
    {
        return parser.hasNextEvent() ? parser.nextEvent() : null;
    }

    private void decodeValue(
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
            floatValue = Float.parseFloat(parser.getStringView().toString());
            break;
        case DOUBLE:
            doubleValue = Double.parseDouble(parser.getStringView().toString());
            break;
        case ENUM:
            longValue = token == JsonEvent.VALUE_STRING
                ? enumNumber(field, parser.getStringView().toString())
                : parseLong(parser.getStringView());
            break;
        case STRING:
            putUtf8(parser.getStringView());
            break;
        case BYTES:
            putBytes(decodeBase64(parser.getStringView().toString()));
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private void decodeKey(
        ProtobufField keyField)
    {
        CharSequence text = parser.getStringView();
        switch (keyField.type())
        {
        case STRING:
            putUtf8(text);
            break;
        case BOOL:
            longValue = "true".contentEquals(text) ? 1L : 0L;
            break;
        case UINT32:
        case FIXED32:
            longValue = parseLong(text) & 0xffffffffL;
            break;
        case UINT64:
        case FIXED64:
            longValue = parseUnsignedLong(text);
            break;
        default:
            longValue = parseLong(text);
            break;
        }
    }

    private void putUtf8(
        CharSequence value)
    {
        int length = value.length();
        int index = 0;
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
            if (codePoint < 0x80)
            {
                valueBuffer.putByte(index++, (byte) codePoint);
            }
            else if (codePoint < 0x800)
            {
                valueBuffer.putByte(index++, (byte) (0xc0 | codePoint >> 6));
                valueBuffer.putByte(index++, (byte) (0x80 | codePoint & 0x3f));
            }
            else if (codePoint < 0x10000)
            {
                valueBuffer.putByte(index++, (byte) (0xe0 | codePoint >> 12));
                valueBuffer.putByte(index++, (byte) (0x80 | codePoint >> 6 & 0x3f));
                valueBuffer.putByte(index++, (byte) (0x80 | codePoint & 0x3f));
            }
            else
            {
                valueBuffer.putByte(index++, (byte) (0xf0 | codePoint >> 18));
                valueBuffer.putByte(index++, (byte) (0x80 | codePoint >> 12 & 0x3f));
                valueBuffer.putByte(index++, (byte) (0x80 | codePoint >> 6 & 0x3f));
                valueBuffer.putByte(index++, (byte) (0x80 | codePoint & 0x3f));
            }
        }
        valueLength = index;
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
            throw new ProtobufException("unknown enum value " + name);
        }
        return number;
    }

    private void putBytes(
        byte[] bytes)
    {
        valueBuffer.putBytes(0, bytes);
        valueLength = bytes.length;
    }

    private void expectStartObject(
        JsonEvent token)
    {
        if (token != JsonEvent.START_OBJECT)
        {
            throw new ProtobufException("expected json object");
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

    private static byte[] decodeBase64(
        String value)
    {
        byte[] bytes;
        try
        {
            bytes = Base64.getDecoder().decode(value);
        }
        catch (IllegalArgumentException ex)
        {
            bytes = Base64.getUrlDecoder().decode(value);
        }
        return bytes;
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
