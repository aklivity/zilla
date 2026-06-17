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
package io.aklivity.zilla.runtime.common.avro.internal.json;

import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroField;
import io.aklivity.zilla.runtime.common.avro.AvroLocation;
import io.aklivity.zilla.runtime.common.avro.AvroParser;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * <b>JSON → Avro</b> adapter: an {@link AvroParser} that drives a {@link JsonParserEx} over its streaming
 * event surface ({@link JsonParserEx#nextEvent()}) and, walking the compiled {@link AvroSchema} in lockstep,
 * presents the JSON events and accessors as the Avro {@link AvroEvent} stream — so it plugs in wherever an
 * {@code AvroParser} does, whether driving an {@link io.aklivity.zilla.runtime.common.avro.AvroGenerator}
 * directly or feeding an {@link io.aklivity.zilla.runtime.common.avro.AvroSink} through an
 * {@link io.aklivity.zilla.runtime.common.avro.AvroPipeline}. The schema walk is a pushdown automaton — one
 * {@link #nextEvent} yields one event — so no document is buffered.
 * <p>
 * <b>Streaming.</b> Input may arrive in windows: {@link #wrap(DirectBuffer, int, int, boolean)} with
 * {@code last == false} re-presents the not-yet-consumed remainder plus newly arrived bytes; when the window
 * is exhausted mid-datum {@link #nextEvent} returns {@code null} and the pipeline reports
 * {@link io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status#STARVED}, resuming where it underflowed
 * on the next window. {@code last == true} makes an incomplete unit a truncation.
 * <p>
 * <b>Allocation.</b> The structural, string, and integer hot path allocates nothing per message: field
 * keys, {@code string} content, enum symbols, union branch names, and {@code int}/{@code long} lexemes are
 * all read through the zero-copy {@link JsonParserEx#getStringView()} view (compared via
 * {@code String#contentEquals}, parsed in place, or re-encoded into a reused buffer). Floating-point values
 * route through {@code getBigDecimal}, and {@code bytes}/{@code fixed} base64 through {@code getString} —
 * both of which materialize, matching the floating-point cost on the Avro → JSON side.
 * <p>
 * JSON that does not match the schema (wrong scalar type, an unexpected field key, an unknown union branch, a
 * {@code fixed} of the wrong size) raises {@link AvroValidationException}, reported as a clean reject. Reuse
 * a single instance per worker thread; not thread-safe.
 */
public final class AvroJsonParserImpl implements AvroParser, AvroLocation
{
    private static final int NOT_STARTED = 0;
    private static final int RUNNING = 1;
    private static final int DONE = 2;

    private final JsonParserEx json;
    private final AvroType rootType;
    private final UnsafeBuffer segment;
    private final UnsafeBuffer segmentView;
    private final Map<AvroType, List<AvroField>> fieldsByType;
    private final Map<AvroType, List<AvroType>> branchesByType;
    private final Map<AvroType, List<String>> symbolsByType;

    private Frame[] stack;
    private int top;
    private int state;
    private JsonEvent pendingEvent;
    private boolean havePending;
    private boolean last;
    private boolean starved;
    private byte[] bytes;

    private boolean booleanValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private CharSequence valueChars;
    private String fieldName;
    private CharSequence keyChars;
    private AvroType currentType;
    private int segmentConsumed;

    public AvroJsonParserImpl(
        AvroSchema schema,
        JsonParserEx json)
    {
        this.json = json;
        this.rootType = schema.type();
        this.segment = new UnsafeBuffer(0, 0);
        this.segmentView = new UnsafeBuffer(0, 0);
        this.fieldsByType = new IdentityHashMap<>();
        this.branchesByType = new IdentityHashMap<>();
        this.symbolsByType = new IdentityHashMap<>();
        this.stack = new Frame[16];
        this.bytes = new byte[64];
    }

    @Override
    public void reset()
    {
        json.reset();
        top = 0;
        state = NOT_STARTED;
        havePending = false;
        starved = false;
        segmentConsumed = 0;
    }

    @Override
    public void wrap(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last)
    {
        json.wrap(buffer, offset, length, last);
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
        return valueChars == null ? null : valueChars.toString();
    }

    @Override
    public String getField()
    {
        return fieldName;
    }

    @Override
    public String getKey()
    {
        return keyChars == null ? null : keyChars.toString();
    }

    @Override
    public DirectBuffer getSegment()
    {
        // expose the unconsumed remainder so a bounded-output suspend on the Avro side resumes correctly
        segmentView.wrap(segment, segmentConsumed, segment.capacity() - segmentConsumed);
        return segmentView;
    }

    @Override
    public void consumed(
        int sourceBytes)
    {
        segmentConsumed += sourceBytes;
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

    @Override
    public int remaining()
    {
        return json.remaining();
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
        List<AvroField> fields = fields(type);
        if (frame.phase == 0)
        {
            if (accept(JsonEvent.START_OBJECT))
            {
                frame.phase = 1;
                currentType = type;
                event = AvroEvent.START_RECORD;
            }
        }
        else if (frame.fieldIndex < fields.size())
        {
            JsonEvent next = peek();
            if (next != null)
            {
                AvroField field = fields.get(frame.fieldIndex);
                if (next != JsonEvent.KEY_NAME || !field.name().contentEquals(json.getStringView()))
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
        else if (accept(JsonEvent.END_OBJECT))
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
            if (accept(JsonEvent.START_ARRAY))
            {
                frame.phase = 1;
                currentType = type;
                event = AvroEvent.START_ARRAY;
            }
        }
        else
        {
            JsonEvent next = peek();
            if (next == JsonEvent.END_ARRAY)
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
            if (accept(JsonEvent.START_OBJECT))
            {
                frame.phase = 1;
                currentType = type;
                event = AvroEvent.START_MAP;
            }
        }
        else if (frame.phase == 1)
        {
            JsonEvent next = peek();
            if (next == JsonEvent.END_OBJECT)
            {
                consume();
                popFrame();
                currentType = type;
                event = AvroEvent.END_MAP;
            }
            else if (next != null)
            {
                if (next != JsonEvent.KEY_NAME)
                {
                    throw reject("expected map key");
                }
                CharSequence key = json.getStringView();
                keyChars = key;
                segmentUtf8(key);
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
        List<AvroType> branches = branches(type);
        if (frame.phase == 0)
        {
            JsonEvent next = peek();
            if (next == JsonEvent.VALUE_NULL)
            {
                int index = AvroJsonUnion.nullBranchIndex(branches);
                if (index < 0)
                {
                    throw reject("union has no null branch");
                }
                event = selectBranch(type, index, false);
            }
            else if (next == JsonEvent.START_OBJECT)
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
            JsonEvent next = peek();
            if (next != null)
            {
                if (next != JsonEvent.KEY_NAME)
                {
                    throw reject("expected union branch name");
                }
                int index = AvroJsonUnion.branchIndex(branches, json.getStringView());
                if (index < 0)
                {
                    throw reject("unknown union branch " + json.getStringView());
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
        AvroType branch = branches(union).get(index);
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
        JsonEvent next = peek();
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
        JsonEvent next)
    {
        AvroEvent event;
        switch (type.kind())
        {
        case NULL:
            require(next == JsonEvent.VALUE_NULL, "expected null");
            consume();
            event = AvroEvent.NULL;
            break;
        case BOOLEAN:
            require(next == JsonEvent.VALUE_TRUE || next == JsonEvent.VALUE_FALSE, "expected boolean");
            booleanValue = next == JsonEvent.VALUE_TRUE;
            consume();
            event = AvroEvent.BOOLEAN;
            break;
        case INT:
            require(next == JsonEvent.VALUE_NUMBER, "expected number");
            intValue = (int) parseLong(json.getStringView());
            consume();
            event = AvroEvent.INT;
            break;
        case LONG:
            require(next == JsonEvent.VALUE_NUMBER, "expected number");
            longValue = parseLong(json.getStringView());
            consume();
            event = AvroEvent.LONG;
            break;
        case FLOAT:
            require(next == JsonEvent.VALUE_NUMBER, "expected number");
            floatValue = json.getBigDecimal().floatValue();
            consume();
            event = AvroEvent.FLOAT;
            break;
        case DOUBLE:
            require(next == JsonEvent.VALUE_NUMBER, "expected number");
            doubleValue = json.getBigDecimal().doubleValue();
            consume();
            event = AvroEvent.DOUBLE;
            break;
        case STRING:
            require(next == JsonEvent.VALUE_STRING, "expected string");
            valueChars = json.getStringView();
            segmentUtf8(valueChars);
            consume();
            event = AvroEvent.STRING;
            break;
        case ENUM:
            require(next == JsonEvent.VALUE_STRING, "expected enum symbol");
            event = readEnum(type);
            break;
        case BYTES:
            require(next == JsonEvent.VALUE_STRING, "expected base64 string");
            segmentBytes(decode(json.getString()), 0);
            consume();
            event = AvroEvent.BYTES;
            break;
        case FIXED:
            require(next == JsonEvent.VALUE_STRING, "expected base64 string");
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
        CharSequence symbol = json.getStringView();
        List<String> symbols = symbols(type);
        int ordinal = -1;
        for (int i = 0; ordinal < 0 && i < symbols.size(); i++)
        {
            if (symbols.get(i).contentEquals(symbol))
            {
                ordinal = i;
            }
        }
        if (ordinal < 0)
        {
            throw reject("unknown enum symbol " + symbol);
        }
        intValue = ordinal;
        valueChars = symbol;
        consume();
        return AvroEvent.ENUM;
    }

    private long parseLong(
        CharSequence value)
    {
        int length = value.length();
        int index = 0;
        boolean negative = length > 0 && value.charAt(0) == '-';
        if (negative)
        {
            index = 1;
        }
        if (index == length)
        {
            throw reject("expected integer");
        }
        long magnitude = 0;
        for (; index < length; index++)
        {
            char ch = value.charAt(index);
            if (ch < '0' || ch > '9')
            {
                throw reject("expected integer");
            }
            magnitude = magnitude * 10 + (ch - '0');
        }
        return negative ? -magnitude : magnitude;
    }

    private void segmentUtf8(
        CharSequence value)
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
        segmentConsumed = 0;
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
        segmentConsumed = 0;
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
        JsonEvent expected)
    {
        JsonEvent next = peek();
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

    // Pulls the next JSON event, skipping the document-framing START_DOCUMENT so the schema walk sees only
    // structural and value events. Returns null (and arms STARVED) when the window is exhausted mid-datum
    // and more input will follow; a truncation under last == true is a reject.
    private JsonEvent peek()
    {
        while (!havePending && !starved)
        {
            if (json.hasNextEvent())
            {
                JsonEvent next = json.nextEvent();
                if (next != JsonEvent.START_DOCUMENT && next != JsonEvent.END_DOCUMENT)
                {
                    pendingEvent = next;
                    havePending = true;
                }
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
            accept(JsonEvent.END_OBJECT);
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

    private List<AvroField> fields(
        AvroType type)
    {
        return fieldsByType.computeIfAbsent(type, AvroType::fields);
    }

    private List<AvroType> branches(
        AvroType type)
    {
        return branchesByType.computeIfAbsent(type, AvroType::branches);
    }

    private List<String> symbols(
        AvroType type)
    {
        return symbolsByType.computeIfAbsent(type, AvroType::symbols);
    }

    private static final class Frame
    {
        private AvroType type;
        private int phase;
        private int fieldIndex;
        private boolean closeObject;
    }
}
