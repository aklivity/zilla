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

import java.util.BitSet;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A {@link ProtobufGenerator} that adapts the wire write methods to a {@link JsonGeneratorEx}, rendering each
 * field by number against the descriptor scope into JSON with the proto3 mapping: a message is a JSON object
 * keyed by each field's proto3 json name, a {@code repeated} field a JSON array, a {@code map} a JSON object,
 * 64-bit and unsigned-64-bit integers JSON strings, {@code bytes} a base64 string, an {@code enum} its value
 * name (its number when unknown), {@code float}/{@code double} JSON numbers ({@code "NaN"}/{@code "Infinity"}/
 * {@code "-Infinity"} as strings). It fits seamlessly as the {@link ProtobufGenerator} a wire
 * {@link io.aklivity.zilla.runtime.common.protobuf.ProtobufSink} drives, so a wire
 * {@link io.aklivity.zilla.runtime.common.protobuf.ProtobufParser} (or a whole pipeline) renders to JSON with
 * no intermediate buffering.
 * <p>
 * Numbers are formatted into a reused {@link StringBuilder} and emitted via {@code writeNumber}/{@code write}
 * to avoid the generator's internal {@code Integer.toString}/{@code Long.toString} allocation. The root object
 * opens on the first write and is finalized by {@link #flush()} (the protobuf root carries no end event); the
 * JSON output buffer must hold the whole document — protobuf input may stream across windows, the JSON output
 * is not split.
 */
public final class ProtobufJsonGeneratorImpl implements ProtobufGenerator
{
    private static final char[] BASE64 =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private final JsonGeneratorEx json;
    private final ProtobufSchema schema;
    private final String messageName;
    private final boolean protoFieldNames;
    private final boolean includeDefaults;
    private final StringBuilder scratch;
    private final UnsafeBuffer byteView;

    private Scope[] scopes;
    private int depth;
    private boolean rootOpened;

    private ProtobufField scalarField;
    private boolean scalarKey;

    private boolean segmentActive;
    private boolean segmentOpened;
    private int consumed;
    private boolean flushed;

    public ProtobufJsonGeneratorImpl(
        JsonGeneratorEx json,
        ProtobufSchema schema,
        String messageName)
    {
        this(json, schema, messageName, false, false);
    }

    public ProtobufJsonGeneratorImpl(
        JsonGeneratorEx json,
        ProtobufSchema schema,
        String messageName,
        boolean protoFieldNames,
        boolean includeDefaults)
    {
        this.json = json;
        this.schema = schema;
        this.messageName = messageName;
        this.protoFieldNames = protoFieldNames;
        this.includeDefaults = includeDefaults;
        this.scratch = new StringBuilder();
        this.byteView = new UnsafeBuffer();
        this.scopes = new Scope[8];
        for (int i = 0; i < scopes.length; i++)
        {
            scopes[i] = new Scope();
        }
    }

    @Override
    public ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit)
    {
        json.wrap(buffer, offset, limit);
        if (flushed)
        {
            // continuation of a suspended document: the open scopes, the in-flight value, and the underlying
            // json's open string persist across the drain — the chunks concatenate into one document
            flushed = false;
        }
        else
        {
            depth = -1;
            rootOpened = false;
            segmentActive = false;
            segmentOpened = false;
        }
        consumed = 0;
        return this;
    }

    @Override
    public int length()
    {
        return json.length();
    }

    @Override
    public int consumed()
    {
        return consumed;
    }

    @Override
    public int remaining()
    {
        return json.remaining();
    }

    @Override
    public ProtobufGenerator writeInt32(
        int field,
        int value)
    {
        prepare(field);
        if (scalarKey)
        {
            captureKey(Integer.toString(value));
        }
        else
        {
            scratch.setLength(0);
            scratch.append(value);
            json.writeNumber(scratch);
        }
        return this;
    }

    @Override
    public ProtobufGenerator writeInt64(
        int field,
        long value)
    {
        prepare(field);
        if (scalarKey)
        {
            captureKey(Long.toString(value));
        }
        else
        {
            scratch.setLength(0);
            scratch.append(value);
            json.write(scratch);
        }
        return this;
    }

    @Override
    public ProtobufGenerator writeUInt32(
        int field,
        int value)
    {
        long unsigned = value & 0xffffffffL;
        prepare(field);
        if (scalarKey)
        {
            captureKey(Long.toString(unsigned));
        }
        else
        {
            scratch.setLength(0);
            scratch.append(unsigned);
            json.writeNumber(scratch);
        }
        return this;
    }

    @Override
    public ProtobufGenerator writeUInt64(
        int field,
        long value)
    {
        prepare(field);
        scratch.setLength(0);
        appendUnsigned(value);
        if (scalarKey)
        {
            captureKey(scratch.toString());
        }
        else
        {
            json.write(scratch);
        }
        return this;
    }

    @Override
    public ProtobufGenerator writeSInt32(
        int field,
        int value)
    {
        return writeInt32(field, value);
    }

    @Override
    public ProtobufGenerator writeSInt64(
        int field,
        long value)
    {
        return writeInt64(field, value);
    }

    @Override
    public ProtobufGenerator writeFixed32(
        int field,
        int value)
    {
        return writeUInt32(field, value);
    }

    @Override
    public ProtobufGenerator writeFixed64(
        int field,
        long value)
    {
        return writeUInt64(field, value);
    }

    @Override
    public ProtobufGenerator writeSFixed32(
        int field,
        int value)
    {
        return writeInt32(field, value);
    }

    @Override
    public ProtobufGenerator writeSFixed64(
        int field,
        long value)
    {
        return writeInt64(field, value);
    }

    @Override
    public ProtobufGenerator writeFloat(
        int field,
        float value)
    {
        prepare(field);
        if (Float.isFinite(value))
        {
            scratch.setLength(0);
            scratch.append(value);
            json.writeNumber(scratch);
        }
        else
        {
            json.write(nonFinite(value));
        }
        return this;
    }

    @Override
    public ProtobufGenerator writeDouble(
        int field,
        double value)
    {
        prepare(field);
        if (Double.isFinite(value))
        {
            scratch.setLength(0);
            scratch.append(value);
            json.writeNumber(scratch);
        }
        else
        {
            json.write(nonFinite(value));
        }
        return this;
    }

    @Override
    public ProtobufGenerator writeBool(
        int field,
        boolean value)
    {
        prepare(field);
        if (scalarKey)
        {
            captureKey(value ? "true" : "false");
        }
        else
        {
            json.write(value);
        }
        return this;
    }

    @Override
    public ProtobufGenerator writeEnum(
        int field,
        int number)
    {
        prepare(field);
        ProtobufEnum enumeration = scalarField.enumeration();
        String name = enumeration != null ? enumeration.name(number) : null;
        if (name != null)
        {
            json.write(name);
        }
        else
        {
            scratch.setLength(0);
            scratch.append(number);
            json.writeNumber(scratch);
        }
        return this;
    }

    @Override
    public ProtobufGenerator writeString(
        int field,
        String value)
    {
        prepare(field);
        if (scalarKey)
        {
            captureKey(value);
        }
        else
        {
            json.write(value);
        }
        return this;
    }

    @Override
    public ProtobufGenerator writeBytes(
        int field,
        byte[] value)
    {
        byteView.wrap(value);
        return writeBytes(field, byteView, 0, value.length);
    }

    @Override
    public ProtobufGenerator writeBytes(
        int field,
        DirectBuffer value,
        int offset,
        int length)
    {
        return writeSegment(field, value, offset, length, 0);
    }

    @Override
    public ProtobufGenerator writeMessage(
        int field,
        DirectBuffer message,
        int offset,
        int length)
    {
        throw new ProtobufException("writeMessage unsupported for json");
    }

    @Override
    public ProtobufGenerator writeSegment(
        int field,
        DirectBuffer value,
        int offset,
        int length,
        int deferred)
    {
        if (!segmentActive)
        {
            prepare(field);
            segmentActive = true;
            segmentOpened = false;
        }
        if (scalarKey)
        {
            writeKeySegment(value, offset, length, deferred);
        }
        else if (scalarField.type() == ProtobufType.STRING)
        {
            writeStringSegment(value, offset, length, deferred);
        }
        else
        {
            writeBytesSegment(value, offset, length, deferred);
        }
        return this;
    }

    @Override
    public ProtobufGenerator startMessage(
        int field,
        int length)
    {
        start(field, false);
        return this;
    }

    @Override
    public ProtobufGenerator endMessage()
    {
        end();
        return this;
    }

    @Override
    public ProtobufGenerator startGroup(
        int field)
    {
        start(field, true);
        return this;
    }

    @Override
    public ProtobufGenerator endGroup()
    {
        end();
        return this;
    }

    @Override
    public ProtobufGenerator writeRaw(
        DirectBuffer source,
        int offset,
        int length)
    {
        throw new ProtobufException("writeRaw unsupported for json");
    }

    @Override
    public ProtobufGenerator writeValue(
        int field,
        ProtobufWireType wireType,
        DirectBuffer value,
        int offset,
        int length)
    {
        throw new ProtobufException("writeValue unsupported for json");
    }

    @Override
    public ProtobufGenerator flush()
    {
        if (segmentActive)
        {
            // a value is in flight: the bounded output filled mid-value, so leave the open string and scopes
            // untouched — this chunk drains and the next window resumes the same document by concatenation
            flushed = true;
        }
        else
        {
            // an empty message produced no field write, so open the root object here before closing it
            ensureRoot();
            while (depth >= 0)
            {
                Scope scope = scopes[depth];
                if (scope.openField != -1)
                {
                    closeContainer(scope);
                }
                emitDefaults(scope);
                depth--;
                if (!scope.mapEntry)
                {
                    json.writeEnd();
                }
            }
            rootOpened = false;
        }
        return this;
    }

    private void ensureRoot()
    {
        if (!rootOpened)
        {
            rootOpened = true;
            json.writeStartObject();
            depth = 0;
            scopes[0].set(schema.message(messageName), false);
        }
    }

    private void prepare(
        int number)
    {
        ensureRoot();
        Scope scope = scopes[depth];
        if (scope.mapEntry)
        {
            scalarField = scope.message.field(number);
            scalarKey = number == 1;
            if (!scalarKey)
            {
                json.writeKey(scope.pendingKey);
            }
        }
        else
        {
            ProtobufField field = scope.message.field(number);
            scalarField = field;
            scalarKey = false;
            scope.written.set(number);
            if (scope.openField != -1 && scope.openField != number)
            {
                closeContainer(scope);
            }
            if (field.repeated())
            {
                if (scope.openField != number)
                {
                    json.writeKey(key(field));
                    json.writeStartArray();
                    scope.openField = number;
                }
            }
            else
            {
                json.writeKey(key(field));
            }
        }
    }

    private void start(
        int number,
        boolean group)
    {
        ensureRoot();
        Scope scope = scopes[depth];
        if (scope.mapEntry)
        {
            json.writeKey(scope.pendingKey);
            json.writeStartObject();
            pushScope(scope.message.field(number).message(), false);
        }
        else
        {
            ProtobufField field = scope.message.field(number);
            scope.written.set(number);
            if (scope.openField != -1 && scope.openField != number)
            {
                closeContainer(scope);
            }
            if (map(field))
            {
                if (scope.openField != number)
                {
                    json.writeKey(key(field));
                    json.writeStartObject();
                    scope.openField = number;
                }
                pushScope(field.message(), true);
            }
            else if (field.repeated())
            {
                if (scope.openField != number)
                {
                    json.writeKey(key(field));
                    json.writeStartArray();
                    scope.openField = number;
                }
                json.writeStartObject();
                pushScope(field.message(), false);
            }
            else
            {
                json.writeKey(key(field));
                json.writeStartObject();
                pushScope(field.message(), false);
            }
        }
    }

    private void end()
    {
        Scope scope = scopes[depth];
        if (scope.openField != -1)
        {
            closeContainer(scope);
        }
        emitDefaults(scope);
        depth--;
        if (!scope.mapEntry)
        {
            json.writeEnd();
        }
    }

    private String key(
        ProtobufField field)
    {
        return protoFieldNames ? field.name() : field.jsonName();
    }

    private void emitDefaults(
        Scope scope)
    {
        if (includeDefaults && !scope.mapEntry && scope.message != null)
        {
            for (ProtobufField field : scope.message.sortedFields())
            {
                int number = field.number();
                boolean omit = scope.written.get(number) ||
                    field.oneofName() != null ||
                    field.proto3Optional() ||
                    !field.repeated() && (field.type() == ProtobufType.MESSAGE || field.type() == ProtobufType.GROUP);
                if (!omit)
                {
                    json.writeKey(key(field));
                    if (map(field))
                    {
                        json.writeStartObject();
                        json.writeEnd();
                    }
                    else if (field.repeated())
                    {
                        json.writeStartArray();
                        json.writeEnd();
                    }
                    else
                    {
                        emitScalarDefault(field);
                    }
                }
            }
        }
    }

    private void emitScalarDefault(
        ProtobufField field)
    {
        String value = field.defaultValue();
        switch (field.type())
        {
        case INT32:
        case UINT32:
        case SINT32:
        case FIXED32:
        case SFIXED32:
            json.writeNumber(value != null ? value : "0");
            break;
        case INT64:
        case UINT64:
        case SINT64:
        case FIXED64:
        case SFIXED64:
            json.write(value != null ? value : "0");
            break;
        case DOUBLE:
        case FLOAT:
            json.writeNumber(value != null ? value : "0.0");
            break;
        case BOOL:
            json.write(value != null && Boolean.parseBoolean(value));
            break;
        case STRING:
            json.write(value != null ? value : "");
            break;
        case BYTES:
            json.write(value != null ? value : "");
            break;
        case ENUM:
            emitEnumDefault(field, value);
            break;
        default:
            json.write("");
            break;
        }
    }

    private void emitEnumDefault(
        ProtobufField field,
        String value)
    {
        ProtobufEnum enumeration = field.enumeration();
        String name = value != null ? value : enumeration != null ? enumeration.name(0) : null;
        if (name != null)
        {
            json.write(name);
        }
        else
        {
            json.writeNumber("0");
        }
    }

    // A map key is small and renders to no output until its value follows, so it is accumulated whole across
    // its (rare) fragments and captured once complete — consuming every offered byte, so the key never suspends.
    private void writeKeySegment(
        DirectBuffer value,
        int offset,
        int length,
        int deferred)
    {
        if (!segmentOpened)
        {
            scratch.setLength(0);
            segmentOpened = true;
        }
        appendUtf8(value, offset, length);
        consumed += length;
        if (deferred == 0)
        {
            captureKey(scratch.toString());
            segmentActive = false;
        }
    }

    // The source slice is UTF-8: decode it to chars, drive the consumption-driven json string write (bounded,
    // escaping, quoting), then map the chars json took back to source bytes (a code-point boundary, so exact)
    // and consume only those — the unconsumed remainder streams in on resume with the string still open.
    private void writeStringSegment(
        DirectBuffer value,
        int offset,
        int length,
        int deferred)
    {
        scratch.setLength(0);
        appendUtf8(value, offset, length);
        Completion completion = deferred == 0 ? Completion.COMPLETE : Completion.INCOMPLETE;
        int before = json.consumed();
        json.write(scratch, completion);
        int charsWritten = json.consumed() - before;
        segmentOpened = true;
        consumed += utf8ByteLength(scratch, 0, charsWritten);
        if (charsWritten == scratch.length() && deferred == 0)
        {
            segmentActive = false;
        }
    }

    // The source bytes are base64-encoded a whole 3-byte group at a time so a 4-char group never splits across
    // a window. The source aligns non-final bytes chunks to whole 3-byte groups, so a sub-group tail only ever
    // appears on the final delivery (deferred == 0), where it is padded and emitted; a non-final chunk
    // (deferred > 0) is always a whole number of groups. Whole groups that fit the output bound are emitted and
    // their source bytes consumed; any groups that do not fit are left unconsumed for output back-pressure. The
    // chars go through the same consumption-driven json string write for quoting.
    private void writeBytesSegment(
        DirectBuffer value,
        int offset,
        int length,
        int deferred)
    {
        int reserve = (segmentOpened ? 0 : 1) + (deferred == 0 ? 1 : 0);
        int groupsFit = Math.max(0, json.remaining() - reserve) / 4;
        int wholeGroups = Math.min(groupsFit, length / 3);

        scratch.setLength(0);
        int taken = 0;
        for (int g = 0; g < wholeGroups; g++)
        {
            int b0 = value.getByte(offset + taken++) & 0xff;
            int b1 = value.getByte(offset + taken++) & 0xff;
            int b2 = value.getByte(offset + taken++) & 0xff;
            appendBase64Group(b0, b1, b2);
        }

        int rem = length - taken;
        boolean complete = false;
        if (deferred == 0 && rem > 0 && rem < 3 && wholeGroups < groupsFit)
        {
            // the final delivery's 1-2 byte tail forms one padded group when it still fits the bound
            int b0 = value.getByte(offset + taken++) & 0xff;
            int b1 = rem == 2 ? value.getByte(offset + taken++) & 0xff : -1;
            appendBase64Group(b0, b1, -1);
            complete = true;
        }
        else if (deferred == 0 && rem == 0 && wholeGroups == length / 3)
        {
            complete = true;
        }

        Completion completion = complete ? Completion.COMPLETE : Completion.INCOMPLETE;
        if (scratch.length() > 0 || complete)
        {
            json.write(scratch, completion);
            segmentOpened = true;
        }
        // consume only the source bytes whose whole base64 groups were emitted; any groups that did not fit the
        // output bound are left unconsumed for the source to re-present, so the adapter holds no carry buffer
        consumed += taken;

        if (complete)
        {
            segmentActive = false;
        }
    }

    private void appendBase64Group(
        int b0,
        int b1,
        int b2)
    {
        if (b1 < 0)
        {
            int word = b0 << 16;
            scratch.append(BASE64[word >> 18 & 0x3f]);
            scratch.append(BASE64[word >> 12 & 0x3f]);
            scratch.append('=');
            scratch.append('=');
        }
        else if (b2 < 0)
        {
            int word = b0 << 16 | b1 << 8;
            scratch.append(BASE64[word >> 18 & 0x3f]);
            scratch.append(BASE64[word >> 12 & 0x3f]);
            scratch.append(BASE64[word >> 6 & 0x3f]);
            scratch.append('=');
        }
        else
        {
            int word = b0 << 16 | b1 << 8 | b2;
            scratch.append(BASE64[word >> 18 & 0x3f]);
            scratch.append(BASE64[word >> 12 & 0x3f]);
            scratch.append(BASE64[word >> 6 & 0x3f]);
            scratch.append(BASE64[word & 0x3f]);
        }
    }

    private static int utf8ByteLength(
        CharSequence value,
        int fromCharIndex,
        int toCharIndex)
    {
        int bytes = 0;
        int index = fromCharIndex;
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

    private void captureKey(
        String key)
    {
        scopes[depth].pendingKey = key;
    }

    private void closeContainer(
        Scope scope)
    {
        json.writeEnd();
        scope.openField = -1;
    }

    private void pushScope(
        ProtobufMessage message,
        boolean mapEntry)
    {
        depth++;
        if (depth == scopes.length)
        {
            Scope[] grown = new Scope[scopes.length * 2];
            System.arraycopy(scopes, 0, grown, 0, scopes.length);
            for (int i = scopes.length; i < grown.length; i++)
            {
                grown[i] = new Scope();
            }
            scopes = grown;
        }
        scopes[depth].set(message, mapEntry);
    }

    private void appendUnsigned(
        long value)
    {
        if (value >= 0)
        {
            scratch.append(value);
        }
        else
        {
            long quotient = (value >>> 1) / 5;
            long remainder = value - quotient * 10;
            scratch.append(quotient);
            scratch.append((char) ('0' + remainder));
        }
    }

    private void appendUtf8(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        int index = offset;
        int end = offset + length;
        while (index < end)
        {
            int b0 = buffer.getByte(index) & 0xff;
            int codePoint;
            int count;
            if (b0 < 0x80)
            {
                codePoint = b0;
                count = 1;
            }
            else if ((b0 & 0xe0) == 0xc0)
            {
                codePoint = b0 & 0x1f;
                count = 2;
            }
            else if ((b0 & 0xf0) == 0xe0)
            {
                codePoint = b0 & 0x0f;
                count = 3;
            }
            else
            {
                codePoint = b0 & 0x07;
                count = 4;
            }
            for (int k = 1; k < count; k++)
            {
                codePoint = (codePoint << 6) | (buffer.getByte(index + k) & 0x3f);
            }
            index += count;
            scratch.appendCodePoint(codePoint);
        }
    }

    private static boolean map(
        ProtobufField field)
    {
        ProtobufMessage message = field.message();
        return field.repeated() && message != null && message.mapEntry();
    }

    private static String nonFinite(
        double value)
    {
        String text;
        if (Double.isNaN(value))
        {
            text = "NaN";
        }
        else
        {
            text = value > 0 ? "Infinity" : "-Infinity";
        }
        return text;
    }

    private static final class Scope
    {
        private final BitSet written = new BitSet();

        private ProtobufMessage message;
        private boolean mapEntry;
        private int openField;
        private String pendingKey;

        private void set(
            ProtobufMessage message,
            boolean mapEntry)
        {
            this.message = message;
            this.mapEntry = mapEntry;
            this.openField = -1;
            this.pendingKey = null;
            this.written.clear();
        }
    }
}
