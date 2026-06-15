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
package io.aklivity.zilla.runtime.common.avro.json;

import java.util.Base64;

import jakarta.json.stream.JsonParser.Event;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroField;
import io.aklivity.zilla.runtime.common.avro.AvroLocation;
import io.aklivity.zilla.runtime.common.avro.AvroParser;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * <b>JSON → Avro</b> adapter: an {@link AvroParser} that drives a {@link JsonParserEx} and, walking the
 * compiled {@link AvroSchema} in lockstep, presents the JSON pull events and accessors as the Avro
 * {@link AvroEvent} stream — so it plugs in wherever an {@code AvroParser} does, whether driving an
 * {@link io.aklivity.zilla.runtime.common.avro.AvroGenerator} directly or feeding an
 * {@link io.aklivity.zilla.runtime.common.avro.AvroSink} through an
 * {@link io.aklivity.zilla.runtime.common.avro.AvroPipeline}. The schema walk is a pushdown automaton — one
 * {@link #nextEvent} yields one event — so no document is buffered.
 * <p>
 * <b>Streaming.</b> Input may arrive in windows: {@link #wrap(DirectBuffer, int, int, boolean)} with
 * {@code last == false} re-presents the not-yet-consumed remainder plus newly arrived bytes; when the window
 * is exhausted mid-datum {@link #nextEvent} returns {@code null} and the pipeline reports
 * {@link io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status#STARVED}, resuming where it underflowed
 * on the next window. {@code last == true} makes an incomplete unit a truncation.
 * <p>
 * <b>Allocation.</b> Numbers read through the primitive {@code getInt}/{@code getLong} accessors and
 * structure carries cached schema strings, so the structural and numeric hot path allocates nothing per
 * message. {@code string} values are read through {@code JsonParserEx#getString} (the {@code jakarta.json}
 * pull contract), then encoded into a reused buffer; {@code bytes}/{@code fixed} base64 is decoded into a
 * reused buffer.
 * <p>
 * JSON that does not match the schema (wrong scalar type, an unexpected field key, an unknown union branch, a
 * {@code fixed} of the wrong size) raises {@link AvroValidationException}, reported as a clean reject. Reuse
 * a single instance per worker thread; not thread-safe.
 */
final class AvroJsonParser implements AvroParser, AvroLocation
{
    private static final int NOT_STARTED = 0;
    private static final int RUNNING = 1;
    private static final int DONE = 2;

    private final JsonParserEx json;
    private final AvroType rootType;
    private final UnsafeBuffer segment;

    private Frame[] stack;
    private int top;
    private int state;
    private Event pendingEvent;
    private boolean havePending;
    private boolean last;
    private boolean starved;
    private byte[] bytes;

    private boolean booleanValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private String stringValue;
    private String fieldName;
    private String keyName;
    private AvroType currentType;

    AvroJsonParser(
        AvroSchema schema,
        JsonParserEx json)
    {
        this.json = json;
        this.rootType = schema.type();
        this.segment = new UnsafeBuffer(0, 0);
        this.stack = new Frame[16];
        this.bytes = new byte[64];
    }

    @Override
    public void reset()
    {
        top = 0;
        state = NOT_STARTED;
        havePending = false;
        starved = false;
    }

    @Override
    public void wrap(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last)
    {
        json.wrap(buffer, offset, length);
        this.last = last;
        this.havePending = false;
        this.starved = false;
    }

    @Override
    public boolean hasNext()
    {
        return state != DONE;
    }

    @Override
    public AvroEvent nextEvent(
        Mode mode)
    {
        AvroEvent event;
        switch (state)
        {
        case NOT_STARTED:
            state = RUNNING;
            push(rootType, false);
            currentType = rootType;
            event = AvroEvent.START_MESSAGE;
            break;
        case RUNNING:
            if (top == 0)
            {
                state = DONE;
                currentType = null;
                event = AvroEvent.END_MESSAGE;
            }
            else
            {
                event = step();
            }
            break;
        default:
            event = null;
            break;
        }
        return event;
    }

    @Override
    public boolean getBoolean()
    {
        return booleanValue;
    }

    @Override
    public int getInt()
    {
        return intValue;
    }

    @Override
    public long getLong()
    {
        return longValue;
    }

    @Override
    public float getFloat()
    {
        return floatValue;
    }

    @Override
    public double getDouble()
    {
        return doubleValue;
    }

    @Override
    public String getString()
    {
        return stringValue;
    }

    @Override
    public String getField()
    {
        return fieldName;
    }

    @Override
    public String getKey()
    {
        return keyName;
    }

    @Override
    public DirectBuffer getSegment()
    {
        return segment;
    }

    @Override
    public int deferredBytes()
    {
        return 0;
    }

    @Override
    public AvroType type()
    {
        return currentType;
    }

    @Override
    public AvroLocation getLocation()
    {
        return this;
    }

    @Override
    public int depth()
    {
        return top;
    }

    @Override
    public long position()
    {
        return json.getLocation().getStreamOffset();
    }

    private AvroEvent step()
    {
        AvroEvent event = null;
        while (event == null && !starved)
        {
            Frame frame = stack[top - 1];
            AvroType type = frame.type;
            switch (type.kind())
            {
            case RECORD:
                event = stepRecord(frame, type);
                break;
            case ARRAY:
                event = stepArray(frame, type);
                break;
            case MAP:
                event = stepMap(frame, type);
                break;
            case UNION:
                event = stepUnion(frame, type);
                break;
            default:
                event = stepScalar(frame, type);
                break;
            }
        }
        return event;
    }

    private AvroEvent stepRecord(
        Frame frame,
        AvroType type)
    {
        AvroEvent event = null;
        if (frame.phase == 0)
        {
            if (accept(Event.START_OBJECT))
            {
                frame.phase = 1;
                currentType = type;
                event = AvroEvent.START_RECORD;
            }
        }
        else if (frame.fieldIndex < type.fields().size())
        {
            Event next = peek();
            if (next != null)
            {
                AvroField field = type.fields().get(frame.fieldIndex);
                if (next != Event.KEY_NAME || !json.getString().equals(field.name()))
                {
                    throw reject("expected field " + field.name());
                }
                consume();
                fieldName = field.name();
                currentType = field.type();
                frame.fieldIndex++;
                push(field.type(), false);
                event = AvroEvent.FIELD_NAME;
            }
        }
        else if (accept(Event.END_OBJECT))
        {
            popFrame();
            currentType = type;
            event = AvroEvent.END_RECORD;
        }
        return event;
    }

    private AvroEvent stepArray(
        Frame frame,
        AvroType type)
    {
        AvroEvent event = null;
        if (frame.phase == 0)
        {
            if (accept(Event.START_ARRAY))
            {
                frame.phase = 1;
                currentType = type;
                event = AvroEvent.START_ARRAY;
            }
        }
        else
        {
            Event next = peek();
            if (next == Event.END_ARRAY)
            {
                consume();
                popFrame();
                currentType = type;
                event = AvroEvent.END_ARRAY;
            }
            else if (next != null)
            {
                push(type.items(), false);
            }
        }
        return event;
    }

    private AvroEvent stepMap(
        Frame frame,
        AvroType type)
    {
        AvroEvent event = null;
        if (frame.phase == 0)
        {
            if (accept(Event.START_OBJECT))
            {
                frame.phase = 1;
                currentType = type;
                event = AvroEvent.START_MAP;
            }
        }
        else if (frame.phase == 1)
        {
            Event next = peek();
            if (next == Event.END_OBJECT)
            {
                consume();
                popFrame();
                currentType = type;
                event = AvroEvent.END_MAP;
            }
            else if (next != null)
            {
                if (next != Event.KEY_NAME)
                {
                    throw reject("expected map key");
                }
                keyName = json.getString();
                segmentUtf8(keyName);
                consume();
                frame.phase = 2;
                currentType = type.values();
                event = AvroEvent.MAP_KEY;
            }
        }
        else
        {
            frame.phase = 1;
            push(type.values(), false);
        }
        return event;
    }

    private AvroEvent stepUnion(
        Frame frame,
        AvroType type)
    {
        AvroEvent event = null;
        if (frame.phase == 0)
        {
            Event next = peek();
            if (next == Event.VALUE_NULL)
            {
                int index = AvroJson.nullBranchIndex(type);
                if (index < 0)
                {
                    throw reject("union has no null branch");
                }
                event = selectBranch(type, index, false);
            }
            else if (next == Event.START_OBJECT)
            {
                consume();
                frame.phase = 1;
            }
            else if (next != null)
            {
                throw reject("expected union value");
            }
        }
        else
        {
            Event next = peek();
            if (next != null)
            {
                if (next != Event.KEY_NAME)
                {
                    throw reject("expected union branch name");
                }
                int index = AvroJson.branchIndex(type, json.getString());
                if (index < 0)
                {
                    throw reject("unknown union branch " + json.getString());
                }
                consume();
                event = selectBranch(type, index, true);
            }
        }
        return event;
    }

    private AvroEvent selectBranch(
        AvroType union,
        int index,
        boolean wrapped)
    {
        intValue = index;
        AvroType branch = union.branches().get(index);
        currentType = branch;
        popFrame();
        push(branch, wrapped);
        return AvroEvent.UNION_BRANCH;
    }

    private AvroEvent stepScalar(
        Frame frame,
        AvroType type)
    {
        AvroEvent event;
        Event next = peek();
        if (next == null)
        {
            event = null;
        }
        else
        {
            event = readScalar(type, next);
            currentType = type;
            popFrame();
        }
        return event;
    }

    private AvroEvent readScalar(
        AvroType type,
        Event next)
    {
        AvroEvent event;
        switch (type.kind())
        {
        case NULL:
            require(next == Event.VALUE_NULL, "expected null");
            consume();
            event = AvroEvent.NULL;
            break;
        case BOOLEAN:
            require(next == Event.VALUE_TRUE || next == Event.VALUE_FALSE, "expected boolean");
            booleanValue = next == Event.VALUE_TRUE;
            consume();
            event = AvroEvent.BOOLEAN;
            break;
        case INT:
            require(next == Event.VALUE_NUMBER, "expected number");
            intValue = readInt();
            consume();
            event = AvroEvent.INT;
            break;
        case LONG:
            require(next == Event.VALUE_NUMBER, "expected number");
            longValue = readLong();
            consume();
            event = AvroEvent.LONG;
            break;
        case FLOAT:
            require(next == Event.VALUE_NUMBER, "expected number");
            floatValue = json.getBigDecimal().floatValue();
            consume();
            event = AvroEvent.FLOAT;
            break;
        case DOUBLE:
            require(next == Event.VALUE_NUMBER, "expected number");
            doubleValue = json.getBigDecimal().doubleValue();
            consume();
            event = AvroEvent.DOUBLE;
            break;
        case STRING:
            require(next == Event.VALUE_STRING, "expected string");
            stringValue = json.getString();
            segmentUtf8(stringValue);
            consume();
            event = AvroEvent.STRING;
            break;
        case ENUM:
            require(next == Event.VALUE_STRING, "expected enum symbol");
            event = readEnum(type);
            break;
        case BYTES:
            require(next == Event.VALUE_STRING, "expected base64 string");
            segmentBytes(decode(json.getString()), 0);
            consume();
            event = AvroEvent.BYTES;
            break;
        case FIXED:
            require(next == Event.VALUE_STRING, "expected base64 string");
            segmentBytes(decode(json.getString()), type.size());
            consume();
            event = AvroEvent.FIXED;
            break;
        default:
            throw reject("unsupported type " + type.kind());
        }
        return event;
    }

    private AvroEvent readEnum(
        AvroType type)
    {
        String symbol = json.getString();
        int ordinal = type.symbols().indexOf(symbol);
        if (ordinal < 0)
        {
            throw reject("unknown enum symbol " + symbol);
        }
        intValue = ordinal;
        stringValue = symbol;
        consume();
        return AvroEvent.ENUM;
    }

    private int readInt()
    {
        int value;
        try
        {
            value = json.getInt();
        }
        catch (IllegalStateException ex)
        {
            value = json.getBigDecimal().intValue();
        }
        return value;
    }

    private long readLong()
    {
        long value;
        try
        {
            value = json.getLong();
        }
        catch (IllegalStateException ex)
        {
            value = json.getBigDecimal().longValue();
        }
        return value;
    }

    private void segmentUtf8(
        String value)
    {
        int count = value.length();
        ensureBytes(count * 3);
        int index = 0;
        for (int i = 0; i < count; i++)
        {
            char ch = value.charAt(i);
            if (ch < 0x80)
            {
                bytes[index++] = (byte) ch;
            }
            else if (ch < 0x800)
            {
                bytes[index++] = (byte) (0xc0 | (ch >> 6));
                bytes[index++] = (byte) (0x80 | (ch & 0x3f));
            }
            else
            {
                bytes[index++] = (byte) (0xe0 | (ch >> 12));
                bytes[index++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
                bytes[index++] = (byte) (0x80 | (ch & 0x3f));
            }
        }
        segment.wrap(bytes, 0, index);
    }

    private void segmentBytes(
        byte[] value,
        int fixedSize)
    {
        if (fixedSize > 0 && value.length != fixedSize)
        {
            throw reject("fixed expected " + fixedSize + " bytes but found " + value.length);
        }
        segment.wrap(value);
    }

    private byte[] decode(
        String value)
    {
        byte[] decoded;
        try
        {
            decoded = Base64.getDecoder().decode(value);
        }
        catch (IllegalArgumentException ex)
        {
            throw reject("invalid base64 value");
        }
        return decoded;
    }

    private boolean accept(
        Event expected)
    {
        Event next = peek();
        boolean accepted = next == expected;
        if (next != null && !accepted)
        {
            throw reject("expected " + expected);
        }
        if (accepted)
        {
            consume();
        }
        return accepted;
    }

    private void require(
        boolean condition,
        String message)
    {
        if (!condition)
        {
            throw reject(message);
        }
    }

    private Event peek()
    {
        if (!havePending)
        {
            if (json.hasNext())
            {
                pendingEvent = json.next();
                havePending = true;
            }
            else if (last)
            {
                throw reject("unexpected end of JSON input");
            }
            else
            {
                starved = true;
            }
        }
        return havePending ? pendingEvent : null;
    }

    private void consume()
    {
        havePending = false;
    }

    private void push(
        AvroType type,
        boolean closeObject)
    {
        if (top == stack.length)
        {
            Frame[] grown = new Frame[stack.length * 2];
            System.arraycopy(stack, 0, grown, 0, stack.length);
            stack = grown;
        }
        Frame frame = stack[top];
        if (frame == null)
        {
            frame = new Frame();
            stack[top] = frame;
        }
        frame.type = type;
        frame.phase = 0;
        frame.fieldIndex = 0;
        frame.closeObject = closeObject;
        top++;
    }

    private void popFrame()
    {
        Frame frame = stack[top - 1];
        top--;
        if (frame.closeObject)
        {
            accept(Event.END_OBJECT);
        }
    }

    private void ensureBytes(
        int capacity)
    {
        if (capacity > bytes.length)
        {
            bytes = new byte[Math.max(bytes.length * 2, capacity)];
        }
    }

    private AvroValidationException reject(
        String message)
    {
        return new AvroValidationException(message);
    }

    private static final class Frame
    {
        private AvroType type;
        private int phase;
        private int fieldIndex;
        private boolean closeObject;
    }
}
