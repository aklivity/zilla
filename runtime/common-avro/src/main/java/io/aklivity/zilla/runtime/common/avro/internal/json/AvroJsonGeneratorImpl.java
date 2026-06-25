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
import static io.aklivity.zilla.runtime.common.avro.internal.json.AvroJsonUnion.nullableSingle;

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
 * way it does for Avro binary output. A {@code string}/{@code bytes}/{@code fixed} value too large for the
 * output window is streamed across windows by {@link #writeSegment} itself — consumption-driven, reporting
 * only the source bytes whose output fit — so the adapter holds no per-value carry buffer and {@link #flush()}
 * has nothing left to emit.
 * <p>
 * <b>Allocation.</b> The hot path allocates nothing per message: record field lists, union branch lists, and
 * enum symbol lists are resolved once per schema node and cached; field names and branch keys are cached
 * schema strings; {@code string} content and base64 are written through a reused {@link CharText} view; the
 * frame stack is reused and grows only past the largest value seen.
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
    private final boolean canonical;
    private final AvroType rootType;
    private final Map<AvroType, List<AvroField>> fieldsByType;
    private final Map<AvroType, List<AvroType>> branchesByType;
    private final Map<AvroType, List<String>> symbolsByType;
    private final CharText text;
    // a reused StringBuilder: append(double)/append(float) formats through FloatingDecimal's thread-local
    // buffer (no per-value String, unlike json.write(double)'s Double.toString), then writeNumber takes the chars
    private final StringBuilder scratch;

    private Frame[] stack;
    private int top;
    private AvroType valueType;
    private boolean datumComplete;
    private boolean valueOpen;
    private boolean valueQuoteOpen;
    private AvroKind valueKind;
    private int keyAt;
    private boolean keyDrained;

    public AvroJsonGeneratorImpl(
        AvroSchema schema,
        JsonGeneratorEx json)
    {
        this(schema, json, false);
    }

    public AvroJsonGeneratorImpl(
        AvroSchema schema,
        JsonGeneratorEx json,
        boolean canonical)
    {
        this.json = json;
        this.canonical = canonical;
        this.rootType = schema.type();
        this.fieldsByType = new IdentityHashMap<>();
        this.branchesByType = new IdentityHashMap<>();
        this.symbolsByType = new IdentityHashMap<>();
        this.text = new CharText(64);
        this.scratch = new StringBuilder();
        this.stack = new Frame[16];
        this.datumComplete = true;
    }

    @Override
    public boolean identity()
    {
        return false;
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
            valueQuoteOpen = false;
            datumComplete = false;
            keyAt = 0;
            keyDrained = false;
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
        return Math.max(0, json.remaining() - RESERVE - pendingKeyWidth());
    }

    private int pendingKeyWidth()
    {
        int width = 0;
        if (top > 0)
        {
            Frame frame = stack[top - 1];
            if (frame.kind == RECORD && frame.fieldIndex < frame.fields.size())
            {
                String name = frame.fields.get(frame.fieldIndex).name();
                width = name.length() + 3 + (frame.fieldIndex > 0 ? 1 : 0);
            }
        }
        return width;
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
        List<AvroType> branches = branches(valueType);
        AvroType branch = branches.get(index);
        boolean wrapped = branch.kind() != AvroKind.NULL && !(canonical && nullableSingle(branches));
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
        scratch.setLength(0);
        scratch.append((double) value);
        json.writeNumber(scratch);
        complete();
    }

    @Override
    public void writeDouble(
        double value)
    {
        value();
        scratch.setLength(0);
        scratch.append(value);
        json.writeNumber(scratch);
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
            if (drainKey())
            {
                return 0;
            }
            value();
            valueKind = valueType.kind();
            valueOpen = true;
            valueQuoteOpen = false;
        }
        return valueKind == AvroKind.STRING
            ? writeStringSegment(source, offset, length, deferred)
            : writeBinarySegment(source, offset, length, deferred);
    }

    @Override
    public void flush()
    {
        // a segmented value is finalized in writeSegment on its final fragment — the JSON string is closed and the
        // value completed there — so once the driving sink has consumed the whole value, nothing remains to emit
    }

    // Emits a record field key whose prefix exceeds the output window across windows, mirroring the way
    // JsonSinkImpl.writeKeyName fragments a key from its source: the schema field name is re-read from keyAt and
    // driven through the bounded json.writeKey(view, COMPLETE), which appends only what fits and closes the key
    // once its final char lands. Returns true while the key is still in flight so writeSegment defers the value
    // (consuming nothing) and the driving sink suspends, re-presenting the value's bytes on resume; the name is a
    // stable schema string, so only the integer cursor crosses the window — no value bytes are buffered here.
    private boolean drainKey()
    {
        boolean draining = false;
        if (!keyDrained && top > 0 && stack[top - 1].kind == RECORD)
        {
            Frame frame = stack[top - 1];
            if (frame.fieldIndex < frame.fields.size())
            {
                String name = frame.fields.get(frame.fieldIndex).name();
                int prefix = (frame.fieldIndex > 0 ? 1 : 0) + 1 + name.length() + 2;
                if (keyAt > 0 || prefix > json.remaining())
                {
                    int before = json.consumed();
                    json.writeKey(name.subSequence(keyAt, name.length()), Completion.COMPLETE);
                    keyAt += json.consumed() - before;
                    if (keyAt == name.length())
                    {
                        keyDrained = true;
                        keyAt = 0;
                    }
                    else
                    {
                        draining = true;
                    }
                }
            }
        }
        return draining;
    }

    private void writeBinary(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        value();
        text.base64(buffer, offset, length);
        json.write(text, Completion.COMPLETE);
        complete();
    }

    // The UTF-8 source slice is decoded to chars and driven through the consumption-driven json string write
    // (bounded, escaping, quoting); the chars json took map back to a whole-code-point prefix of the source, so
    // exactly those source bytes are reported consumed and the unconsumed remainder streams in on resume.
    private int writeStringSegment(
        DirectBufferEx source,
        int offset,
        int length,
        int deferred)
    {
        text.utf8(source, offset, length);
        Completion completion = deferred == 0 ? Completion.COMPLETE : Completion.INCOMPLETE;
        int before = json.consumed();
        json.write(text, completion);
        int charsWritten = json.consumed() - before;
        valueQuoteOpen = true;
        int consumed = utf8ByteLength(text, charsWritten);
        if (charsWritten == text.length() && deferred == 0)
        {
            valueOpen = false;
            complete();
        }
        return consumed;
    }

    // The source bytes are base64-encoded a whole 3-byte group at a time so a 4-char group never splits a window:
    // whole groups that fit the output bound are emitted and only those source bytes reported consumed, leaving a
    // 1-2 byte sub-group tail unconsumed for the source to re-present — except on the final delivery, where the
    // tail is padded and emitted. No adapter-side carry buffer is held; output back-pressure carries the remainder.
    private int writeBinarySegment(
        DirectBufferEx source,
        int offset,
        int length,
        int deferred)
    {
        int reserve = (valueQuoteOpen ? 0 : 1) + (deferred == 0 ? 1 : 0);
        int groupsFit = Math.max(0, json.remaining() - reserve) / 4;
        int wholeGroups = Math.min(groupsFit, length / 3);
        int taken = wholeGroups * 3;
        int rem = length - taken;
        boolean complete = false;
        if (deferred == 0 && rem > 0 && rem < 3 && wholeGroups < groupsFit)
        {
            // the final delivery's 1-2 byte tail forms one padded group when it still fits the bound
            taken += rem;
            complete = true;
        }
        else if (deferred == 0 && rem == 0 && wholeGroups == length / 3)
        {
            complete = true;
        }
        Completion completion = complete ? Completion.COMPLETE : Completion.INCOMPLETE;
        text.base64(source, offset, taken);
        if (text.length() > 0 || complete)
        {
            json.write(text, completion);
            valueQuoteOpen = true;
        }
        if (complete)
        {
            valueOpen = false;
            complete();
        }
        return taken;
    }

    private static int utf8ByteLength(
        CharSequence value,
        int toCharIndex)
    {
        int bytes = 0;
        int index = 0;
        while (index < toCharIndex)
        {
            int codePoint = Character.codePointAt(value, index);
            index += Character.charCount(codePoint);
            if (codePoint < 0x80)
            {
                bytes += 1;
            }
            else if (codePoint < 0x800)
            {
                bytes += 2;
            }
            else if (codePoint < 0x10000)
            {
                bytes += 3;
            }
            else
            {
                bytes += 4;
            }
        }
        return bytes;
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
                if (!keyDrained)
                {
                    json.writeKey(field.name());
                }
                keyDrained = false;
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
