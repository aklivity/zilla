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

import static io.aklivity.zilla.runtime.common.avro.internal.json.AvroJsonUnion.branchName;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

import io.aklivity.zilla.runtime.common.avro.AvroField;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;

/**
 * <b>Avro → JSON</b> adapter: a schema-bound {@link AvroGenerator} that maps each positional Avro write onto
 * the wrapped {@link JsonGeneratorEx}, so it plugs in wherever an {@code AvroGenerator} does — most naturally
 * behind an {@link io.aklivity.zilla.runtime.common.avro.AvroSink#of(AvroGenerator) AvroSink} terminating an
 * {@link io.aklivity.zilla.runtime.common.avro.AvroPipeline}. Avro is positional and carries no field names
 * on the wire, so this generator walks the compiled {@link AvroSchema} in lockstep with the writes to recover
 * record field names as JSON object keys — the same reason {@link io.aklivity.zilla.runtime.common.avro.Avro
 * #generator Avro.generator} is schema-bound.
 * <p>
 * Type mapping (see {@link io.aklivity.zilla.runtime.common.avro.json.AvroJson}): records and maps become JSON
 * objects, arrays become JSON arrays,
 * {@code bytes}/{@code fixed} become base64 strings, enums become their symbol string, and a union value
 * becomes {@code null} or the single-entry {@code {"<branch>": value}} wrapper.
 * <p>
 * <b>Streaming.</b> {@link #length()} and {@link #remaining()} surface the JSON generator's bound (with
 * headroom reserved so a scalar's text expansion stays within the limit the driving sink assumes), so the
 * driving {@link io.aklivity.zilla.runtime.common.avro.AvroSink} drains and resumes across the datum the same
 * way it does for Avro binary output. A {@code string}/{@code bytes}/{@code fixed} value streamed in across
 * input windows via {@link #writeSegment} is coalesced and emitted on {@link #flush()}.
 * <p>
 * <b>Allocation.</b> The hot path allocates nothing per message: record field lists, union branch lists, and
 * enum symbol lists are resolved once per schema node and cached; field names and branch keys are cached
 * schema strings; {@code string} content and base64 are written through a reused {@link CharText} view; the
 * frame stack and coalescing buffer are reused and grow only past the largest value seen.
 */
public final class AvroJsonGeneratorImpl implements AvroGenerator
{
    private static final int RECORD = 0;
    private static final int ARRAY = 1;
    private static final int MAP = 2;
    private static final int UNION_WRAP = 3;

    // headroom over the driving sink's fixed atomic-write threshold for a scalar's JSON text expansion
    private static final int RESERVE = 24;

    private final JsonGeneratorEx json;
    private final AvroType rootType;
    private final Map<AvroType, List<AvroField>> fieldsByType;
    private final Map<AvroType, List<AvroType>> branchesByType;
    private final Map<AvroType, List<String>> symbolsByType;
    private final CharText text;

    private Frame[] stack;
    private int top;
    private AvroType valueType;
    private boolean datumComplete;
    private byte[] coalesced;
    private int coalescedLength;
    private boolean valueOpen;
    private AvroKind valueKind;

    public AvroJsonGeneratorImpl(
        AvroSchema schema,
        JsonGeneratorEx json)
    {
        this.json = json;
        this.rootType = schema.type();
        this.fieldsByType = new IdentityHashMap<>();
        this.branchesByType = new IdentityHashMap<>();
        this.symbolsByType = new IdentityHashMap<>();
        this.text = new CharText(64);
        this.stack = new Frame[16];
        this.coalesced = new byte[64];
        this.datumComplete = true;
    }

    @Override
    public AvroGenerator wrap(
        MutableDirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (datumComplete)
        {
            top = 0;
            valueType = rootType;
            valueOpen = false;
            coalescedLength = 0;
            datumComplete = false;
            json.reset();
        }
        json.wrap(buffer, offset, limit);
        return this;
    }

    @Override
    public int length()
    {
        return json.length();
    }

    @Override
    public int remaining()
    {
        return Math.max(0, json.remaining() - RESERVE);
    }

    @Override
    public void writeStartRecord()
    {
        value();
        AvroType type = valueType;
        json.writeStartObject();
        Frame frame = push(RECORD, type);
        frame.fields = fields(type);
        frame.fieldIndex = 0;
    }

    @Override
    public void writeStartArray()
    {
        value();
        AvroType type = valueType;
        json.writeStartArray();
        push(ARRAY, type).element = type.items();
    }

    @Override
    public void writeStartMap()
    {
        value();
        AvroType type = valueType;
        json.writeStartObject();
        push(MAP, type).element = type.values();
    }

    @Override
    public void writeEnd()
    {
        json.writeEnd();
        top--;
        complete();
    }

    @Override
    public void writeKey(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        text.utf8(buffer, offset, length);
        json.writeKey(text);
    }

    @Override
    public void writeIndex(
        int index)
    {
        value();
        AvroType branch = branches(valueType).get(index);
        boolean wrapped = branch.kind() != AvroKind.NULL;
        if (wrapped)
        {
            json.writeStartObject();
            json.writeKey(branchName(branch));
        }
        Frame frame = push(UNION_WRAP, valueType);
        frame.element = branch;
        frame.wrapped = wrapped;
    }

    @Override
    public void writeNull()
    {
        value();
        json.writeNull();
        complete();
    }

    @Override
    public void writeBoolean(
        boolean value)
    {
        value();
        json.write(value);
        complete();
    }

    @Override
    public void writeInt(
        int value)
    {
        value();
        text.number(value);
        json.writeNumber(text);
        complete();
    }

    @Override
    public void writeLong(
        long value)
    {
        value();
        text.number(value);
        json.writeNumber(text);
        complete();
    }

    @Override
    public void writeFloat(
        float value)
    {
        value();
        json.write((double) value);
        complete();
    }

    @Override
    public void writeDouble(
        double value)
    {
        value();
        json.write(value);
        complete();
    }

    @Override
    public void writeString(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        value();
        text.utf8(buffer, offset, length);
        json.write(text, Completion.COMPLETE);
        complete();
    }

    @Override
    public void writeBytes(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        writeBinary(buffer, offset, length);
    }

    @Override
    public void writeFixed(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        writeBinary(buffer, offset, length);
    }

    @Override
    public void writeEnum(
        int index)
    {
        value();
        json.write(symbols(valueType).get(index));
        complete();
    }

    @Override
    public void writeRaw(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        throw new UnsupportedOperationException("verbatim segment passthrough is not supported for JSON");
    }

    @Override
    public int writeSegment(
        DirectBufferEx source,
        int offset,
        int length,
        int deferred)
    {
        if (!valueOpen)
        {
            value();
            valueKind = valueType.kind();
            valueOpen = true;
        }
        coalesce(source, offset, length);
        return length;
    }

    @Override
    public void flush()
    {
        if (valueKind == AvroKind.STRING)
        {
            text.utf8(coalesced, coalescedLength);
        }
        else
        {
            text.base64(coalesced, coalescedLength);
        }
        json.write(text, Completion.COMPLETE);
        coalescedLength = 0;
        valueOpen = false;
        complete();
    }

    private void writeBinary(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        value();
        coalescedLength = 0;
        coalesce(buffer, offset, length);
        text.base64(coalesced, coalescedLength);
        json.write(text, Completion.COMPLETE);
        coalescedLength = 0;
        complete();
    }

    private void value()
    {
        if (top == 0)
        {
            valueType = rootType;
        }
        else
        {
            Frame frame = stack[top - 1];
            switch (frame.kind)
            {
            case RECORD:
                AvroField field = frame.fields.get(frame.fieldIndex);
                json.writeKey(field.name());
                valueType = field.type();
                frame.fieldIndex++;
                break;
            case ARRAY:
            case MAP:
            case UNION_WRAP:
            default:
                valueType = frame.element;
                break;
            }
        }
    }

    private void complete()
    {
        if (top > 0 && stack[top - 1].kind == UNION_WRAP)
        {
            Frame frame = stack[top - 1];
            if (frame.wrapped)
            {
                json.writeEnd();
            }
            top--;
        }
        if (top == 0)
        {
            datumComplete = true;
        }
    }

    private void coalesce(
        DirectBufferEx source,
        int offset,
        int length)
    {
        if (coalescedLength + length > coalesced.length)
        {
            byte[] grown = new byte[Math.max(coalesced.length * 2, coalescedLength + length)];
            System.arraycopy(coalesced, 0, grown, 0, coalescedLength);
            coalesced = grown;
        }
        source.getBytes(offset, coalesced, coalescedLength, length);
        coalescedLength += length;
    }

    private Frame push(
        int kind,
        AvroType type)
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
        frame.kind = kind;
        frame.type = type;
        top++;
        return frame;
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
        private int kind;
        private AvroType type;
        private List<AvroField> fields;
        private int fieldIndex;
        private AvroType element;
        private boolean wrapped;
    }
}
