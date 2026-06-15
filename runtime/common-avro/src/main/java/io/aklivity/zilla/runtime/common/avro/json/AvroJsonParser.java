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

import static java.nio.charset.StandardCharsets.UTF_8;

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
 * <b>JSON → Avro</b> driver: an {@link AvroParser} that pulls JSON events from a {@link JsonParserEx} and,
 * walking the compiled {@link AvroSchema} in lockstep, emits the Avro {@link AvroEvent} stream a terminal
 * {@link io.aklivity.zilla.runtime.common.avro.AvroSink} consumes to write Avro binary. The schema walk is a
 * pushdown automaton — one {@link #nextEvent} yields one event — so no document is buffered. JSON that does
 * not match the schema (wrong scalar type, an unexpected field key, an unknown union branch, a {@code fixed}
 * of the wrong size) raises {@link AvroValidationException}, which the pipeline reports as a clean reject.
 * <p>
 * Records expect their fields in schema order (the order the Avro JSON encoding emits); unions expect the
 * Avro JSON encoding ({@code null} or {@code {"<branch>": value}}); {@code bytes}/{@code fixed} expect a
 * base64 string. Reuse a single instance per worker thread; not thread-safe.
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
    private Event pending;
    private boolean havePending;

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
    }

    @Override
    public void reset()
    {
        top = 0;
        state = NOT_STARTED;
        havePending = false;
    }

    @Override
    public void wrap(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last)
    {
        json.wrap(buffer, offset, length);
        havePending = false;
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
        while (event == null)
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
        AvroEvent event;
        if (frame.phase == 0)
        {
            expect(Event.START_OBJECT);
            frame.phase = 1;
            currentType = type;
            event = AvroEvent.START_RECORD;
        }
        else if (frame.fieldIndex < type.fields().size())
        {
            AvroField field = type.fields().get(frame.fieldIndex);
            if (peek() != Event.KEY_NAME)
            {
                throw reject("expected field name " + field.name());
            }
            if (!json.getString().equals(field.name()))
            {
                throw reject("expected field " + field.name() + " but found " + json.getString());
            }
            consume();
            fieldName = field.name();
            currentType = field.type();
            frame.fieldIndex++;
            push(field.type(), false);
            event = AvroEvent.FIELD_NAME;
        }
        else
        {
            expect(Event.END_OBJECT);
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
        AvroEvent event;
        if (frame.phase == 0)
        {
            expect(Event.START_ARRAY);
            frame.phase = 1;
            currentType = type;
            event = AvroEvent.START_ARRAY;
        }
        else if (peek() == Event.END_ARRAY)
        {
            consume();
            popFrame();
            currentType = type;
            event = AvroEvent.END_ARRAY;
        }
        else
        {
            push(type.items(), false);
            event = null;
        }
        return event;
    }

    private AvroEvent stepMap(
        Frame frame,
        AvroType type)
    {
        AvroEvent event;
        if (frame.phase == 0)
        {
            expect(Event.START_OBJECT);
            frame.phase = 1;
            currentType = type;
            event = AvroEvent.START_MAP;
        }
        else if (frame.phase == 1)
        {
            if (peek() == Event.END_OBJECT)
            {
                consume();
                popFrame();
                currentType = type;
                event = AvroEvent.END_MAP;
            }
            else
            {
                if (peek() != Event.KEY_NAME)
                {
                    throw reject("expected map key");
                }
                keyName = json.getString();
                segmentString(keyName);
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
            event = null;
        }
        return event;
    }

    private AvroEvent stepUnion(
        Frame frame,
        AvroType type)
    {
        int index;
        boolean wrapped;
        Event event = peek();
        if (event == Event.VALUE_NULL)
        {
            index = AvroJson.nullBranchIndex(type);
            if (index < 0)
            {
                throw reject("union has no null branch");
            }
            wrapped = false;
        }
        else if (event == Event.START_OBJECT)
        {
            consume();
            if (peek() != Event.KEY_NAME)
            {
                throw reject("expected union branch name");
            }
            index = AvroJson.branchIndex(type, json.getString());
            if (index < 0)
            {
                throw reject("unknown union branch " + json.getString());
            }
            consume();
            wrapped = true;
        }
        else
        {
            throw reject("expected union value");
        }
        intValue = index;
        AvroType branch = type.branches().get(index);
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
        switch (type.kind())
        {
        case NULL:
            expect(Event.VALUE_NULL);
            event = AvroEvent.NULL;
            break;
        case BOOLEAN:
            event = scalarBoolean();
            break;
        case INT:
            expectNumber();
            intValue = json.getBigDecimal().intValue();
            consume();
            event = AvroEvent.INT;
            break;
        case LONG:
            expectNumber();
            longValue = json.getBigDecimal().longValue();
            consume();
            event = AvroEvent.LONG;
            break;
        case FLOAT:
            expectNumber();
            floatValue = json.getBigDecimal().floatValue();
            consume();
            event = AvroEvent.FLOAT;
            break;
        case DOUBLE:
            expectNumber();
            doubleValue = json.getBigDecimal().doubleValue();
            consume();
            event = AvroEvent.DOUBLE;
            break;
        case STRING:
            expectText();
            stringValue = json.getString();
            segmentString(stringValue);
            consume();
            event = AvroEvent.STRING;
            break;
        case ENUM:
            event = scalarEnum(type);
            break;
        case BYTES:
            expectText();
            segmentBytes(decode(json.getString()), 0);
            consume();
            event = AvroEvent.BYTES;
            break;
        case FIXED:
            expectText();
            segmentBytes(decode(json.getString()), type.size());
            consume();
            event = AvroEvent.FIXED;
            break;
        default:
            throw reject("unsupported scalar " + type.kind());
        }
        currentType = type;
        popFrame();
        return event;
    }

    private AvroEvent scalarBoolean()
    {
        Event event = peek();
        if (event == Event.VALUE_TRUE)
        {
            booleanValue = true;
        }
        else if (event == Event.VALUE_FALSE)
        {
            booleanValue = false;
        }
        else
        {
            throw reject("expected boolean");
        }
        consume();
        return AvroEvent.BOOLEAN;
    }

    private AvroEvent scalarEnum(
        AvroType type)
    {
        expectText();
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

    private void segmentString(
        String value)
    {
        segment.wrap(value.getBytes(UTF_8));
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
        byte[] bytes;
        try
        {
            bytes = Base64.getDecoder().decode(value);
        }
        catch (IllegalArgumentException ex)
        {
            throw reject("invalid base64 value");
        }
        return bytes;
    }

    private void expect(
        Event expected)
    {
        if (peek() != expected)
        {
            throw reject("expected " + expected);
        }
        consume();
    }

    private void expectNumber()
    {
        if (peek() != Event.VALUE_NUMBER)
        {
            throw reject("expected number");
        }
    }

    private void expectText()
    {
        if (peek() != Event.VALUE_STRING)
        {
            throw reject("expected string");
        }
    }

    private Event peek()
    {
        if (!havePending)
        {
            if (!json.hasNext())
            {
                throw reject("unexpected end of JSON input");
            }
            pending = json.next();
            havePending = true;
        }
        return pending;
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
            expect(Event.END_OBJECT);
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
