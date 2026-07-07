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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
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

    private static final int RESERVE = 2;

    private final JsonGeneratorEx json;
    private final ProtobufSchema schema;
    private final String messageName;
    private final boolean protoFieldNames;
    private final boolean includeDefaults;
    private final StringBuilder scratch;
    private final UnsafeBufferEx byteView;

    private Scope[] scopes;
    private int depth;
    private boolean rootOpened;

    private ProtobufField scalarField;
    private boolean scalarKey;

    private boolean segmentActive;
    private boolean segmentOpened;
    private boolean segmentDeferred;
    private int consumed;
    private boolean flushed;

    private int keyAt;
    private boolean keyDrained;

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
        this.byteView = new UnsafeBufferEx();
        this.scopes = new Scope[8];
        for (int i = 0; i < scopes.length; i++)
        {
            scopes[i] = new Scope();
        }
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public ProtobufGenerator wrap(
        MutableDirectBufferEx buffer,
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
            segmentDeferred = false;
            keyAt = 0;
            keyDrained = false;
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
        return Math.max(0, json.remaining() - RESERVE - pendingKeyWidth());
    }

    private int pendingKeyWidth()
    {
        int width = 0;
        if (rootOpened && depth >= 0)
        {
            Scope scope = scopes[depth];
            if (!scope.mapEntry && scope.message != null)
            {
                int separator = scope.openField != -1 || !scope.written.isEmpty() ? 1 : 0;
                for (ProtobufField field : scope.message.sortedFields())
                {
                    if (!scope.written.get(field.number()))
                    {
                        width = Math.max(width, key(field).length() + 3 + separator);
                    }
                }
            }
        }
        return width;
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
        DirectBufferEx value,
        int offset,
        int length)
    {
        return writeSegment(field, value, offset, length, 0);
    }

    @Override
    public ProtobufGenerator writeMessage(
        int field,
        DirectBufferEx message,
        int offset,
        int length)
    {
        throw new ProtobufException("writeMessage unsupported for json");
    }

    @Override
    public ProtobufGenerator writeSegment(
        int field,
        DirectBufferEx value,
        int offset,
        int length,
        int deferred)
    {
        if (!segmentActive)
        {
            if (json.length() > 0 && segmentKeyWidth(field) > json.remaining())
            {
                segmentDeferred = true;
                return this;
            }
            if (drainKey(field))
            {
                return this;
            }
            prepare(field);
            segmentActive = true;
            segmentOpened = false;
            segmentDeferred = false;
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
    public boolean startMessage(
        int field,
        int length)
    {
        return start(field, false);
    }

    @Override
    public boolean endMessage()
    {
        return end();
    }

    @Override
    public boolean startGroup(
        int field)
    {
        return start(field, true);
    }

    @Override
    public boolean endGroup()
    {
        return end();
    }

    @Override
    public ProtobufGenerator writeRaw(
        DirectBufferEx source,
        int offset,
        int length)
    {
        throw new ProtobufException("writeRaw unsupported for json");
    }

    @Override
    public ProtobufGenerator writeValue(
        int field,
        ProtobufWireType wireType,
        DirectBufferEx value,
        int offset,
        int length)
    {
        throw new ProtobufException("writeValue unsupported for json");
    }

    @Override
    public boolean flush()
    {
        boolean complete = true;
        if (segmentActive || segmentDeferred || keyAt > 0)
        {
            flushed = true;
        }
        else
        {
            // an empty message produced no field write, so open the root object here before closing it
            complete = ensureRoot();
            while (complete && depth >= 0)
            {
                complete = closeLevel(scopes[depth]);
            }
            if (complete)
            {
                rootOpened = false;
            }
        }
        return complete;
    }

    // Closes one scope level atomically: any still-open repeated/map field, then defaults, then (unless this
    // level is a map entry, which owns no brace of its own) the level's own closing brace — decrementing
    // depth only once every one of those has actually happened. A step that cannot fit leaves depth (and
    // whatever that step's own state tracks, e.g. scope.openField) exactly as it was, so a later retry of
    // end()/flush() re-enters at this same level and continues from there rather than re-doing completed work.
    private boolean closeLevel(
        Scope scope)
    {
        boolean complete = true;
        if (scope.openField != -1)
        {
            complete = closeContainer(scope);
        }
        if (complete)
        {
            complete = emitDefaults(scope);
        }
        if (complete && !scope.mapEntry)
        {
            complete = writeEndChecked();
        }
        if (complete)
        {
            depth--;
        }
        return complete;
    }

    private boolean ensureRoot()
    {
        if (!rootOpened && writeStartObjectChecked())
        {
            rootOpened = true;
            depth = 0;
            scopes[0].set(schema.message(messageName), false);
        }
        return rootOpened;
    }

    // Precise (not conservative) room check for one key: separator + quotes + colon, exactly mirroring the
    // math segmentKeyWidth()/drainKey() already use for the same purpose. Checking a compound write's total
    // width up front — rather than attempting each part and reacting after the fact — is what lets an
    // unfragmentable sequence (a key immediately followed by its brace/bracket) stay all-or-nothing without
    // needing to remember, across a suspend, exactly which part of it already landed.
    private static int keyWidth(
        Scope scope,
        String name)
    {
        int separator = scope.mapEntry
            ? (scope.written.isEmpty() ? 0 : 1)
            : (scope.openField != -1 || !scope.written.isEmpty() ? 1 : 0);
        return name.length() + 3 + separator;
    }

    // The key-domain counterpart of the brace/bracket *Checked writers below: writeKey has its own
    // partial-write contract (it may legitimately emit only a prefix of a very long name), so completion is
    // checked via consumed() against the key's full length rather than a length() delta.
    private boolean writeKeyChecked(
        String name)
    {
        int before = json.consumed();
        json.writeKey(name);
        return json.consumed() - before == name.length();
    }

    private boolean writeStartObjectChecked()
    {
        return json.writeStartObjectEx();
    }

    private boolean writeStartArrayChecked()
    {
        return json.writeStartArrayEx();
    }

    private boolean writeEndChecked()
    {
        return json.writeEndEx();
    }

    private int segmentKeyWidth(
        int number)
    {
        int width = 0;
        if (rootOpened && depth >= 0)
        {
            Scope scope = scopes[depth];
            if (scope.mapEntry)
            {
                if (number != 1 && scope.pendingKey != null)
                {
                    int separator = scope.written.isEmpty() ? 0 : 1;
                    width = scope.pendingKey.length() + 3 + separator;
                }
            }
            else if (scope.message != null)
            {
                ProtobufField field = scope.message.field(number);
                if (field != null && !(field.repeated() && scope.openField == number))
                {
                    int separator = scope.openField != -1 || !scope.written.isEmpty() ? 1 : 0;
                    width = key(field).length() + 3 + separator;
                }
            }
        }
        return width;
    }

    // Emits a message field key whose prefix exceeds the output window across windows, mirroring the way
    // JsonSinkImpl.writeKeyName fragments a key from its source: the schema field key is re-read from keyAt and
    // driven through the bounded json.writeKey(view, COMPLETE), which appends only what fits and closes the key
    // once its final char lands. Returns true while the key is still in flight so writeSegment consumes nothing
    // and the driving sink suspends, re-presenting the value's bytes on resume; the key is a stable schema string,
    // so only the integer cursor crosses the window — no value bytes are buffered here.
    private boolean drainKey(
        int number)
    {
        boolean draining = false;
        if (!keyDrained && rootOpened && depth >= 0)
        {
            Scope scope = scopes[depth];
            if (!scope.mapEntry && scope.message != null)
            {
                ProtobufField field = scope.message.field(number);
                if (field != null && !(field.repeated() && scope.openField == number))
                {
                    String name = key(field);
                    int separator = scope.openField != -1 || !scope.written.isEmpty() ? 1 : 0;
                    int prefix = separator + 1 + name.length() + 2;
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
        }
        return draining;
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
                    if (!keyDrained)
                    {
                        json.writeKey(key(field));
                    }
                    keyDrained = false;
                    json.writeStartArray();
                    scope.openField = number;
                }
            }
            else
            {
                if (!keyDrained)
                {
                    json.writeKey(key(field));
                }
                keyDrained = false;
            }
        }
    }

    // Each branch checks its whole compound write's exact total width up front, then performs every part of
    // it unconditionally — safe because the total was already confirmed to fit. A branch that does not fit
    // writes nothing and leaves scope/depth untouched, so a later retry re-enters here and re-evaluates the
    // identical branch rather than resuming mid-sequence.
    private boolean start(
        int number,
        boolean group)
    {
        boolean written = false;
        if (ensureRoot())
        {
            Scope scope = scopes[depth];
            if (scope.mapEntry)
            {
                if (json.remaining() >= keyWidth(scope, scope.pendingKey) + 1)
                {
                    writeKeyChecked(scope.pendingKey);
                    writeStartObjectChecked();
                    pushScope(scope.message.field(number).message(), false);
                    written = true;
                }
            }
            else
            {
                ProtobufField field = scope.message.field(number);
                boolean closesPrior = scope.openField != -1 && scope.openField != number;
                int closeWidth = closesPrior ? 1 : 0;
                boolean opensNew = scope.openField != number;
                int openWidth = opensNew ? keyWidth(scope, key(field)) + 1 : 0;
                if (map(field))
                {
                    if (json.remaining() >= closeWidth + openWidth)
                    {
                        if (closesPrior)
                        {
                            closeContainer(scope);
                        }
                        if (opensNew)
                        {
                            writeKeyChecked(key(field));
                            writeStartObjectChecked();
                            scope.openField = number;
                        }
                        scope.written.set(number);
                        pushScope(field.message(), true);
                        written = true;
                    }
                }
                else if (field.repeated())
                {
                    if (json.remaining() >= closeWidth + openWidth + 1)
                    {
                        if (closesPrior)
                        {
                            closeContainer(scope);
                        }
                        if (opensNew)
                        {
                            writeKeyChecked(key(field));
                            writeStartArrayChecked();
                            scope.openField = number;
                        }
                        scope.written.set(number);
                        writeStartObjectChecked();
                        pushScope(field.message(), false);
                        written = true;
                    }
                }
                else
                {
                    if (json.remaining() >= closeWidth + keyWidth(scope, key(field)) + 1)
                    {
                        if (closesPrior)
                        {
                            closeContainer(scope);
                        }
                        scope.written.set(number);
                        writeKeyChecked(key(field));
                        writeStartObjectChecked();
                        pushScope(field.message(), false);
                        written = true;
                    }
                }
            }
        }
        return written;
    }

    private boolean end()
    {
        return closeLevel(scopes[depth]);
    }

    private String key(
        ProtobufField field)
    {
        return protoFieldNames ? field.name() : field.jsonName();
    }

    // Resumable across suspends: a field's default is only ever marked scope.written once its whole key
    // (+ braces, for a map/repeated default) has actually been written, so a retry skips every field this
    // already completed and re-attempts only the one it stopped on.
    private boolean emitDefaults(
        Scope scope)
    {
        boolean complete = true;
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
                    if (!tryEmitDefault(scope, field))
                    {
                        complete = false;
                        break;
                    }
                    scope.written.set(number);
                }
            }
        }
        return complete;
    }

    // A map/repeated default renders as an empty container ({} / []), an exact, all-or-nothing width; a
    // scalar default's key is checked the same way, but its value rides the existing bounded/truncating
    // json.write/json.writeNumber path (safe from overflow either way, just not itself resumed field-by-field
    // here — schema default values are short literals in practice).
    private boolean tryEmitDefault(
        Scope scope,
        ProtobufField field)
    {
        boolean written;
        if (map(field) || field.repeated())
        {
            written = json.remaining() >= keyWidth(scope, key(field)) + 2;
            if (written)
            {
                writeKeyChecked(key(field));
                if (map(field))
                {
                    writeStartObjectChecked();
                }
                else
                {
                    writeStartArrayChecked();
                }
                writeEndChecked();
            }
        }
        else
        {
            written = json.remaining() >= keyWidth(scope, key(field));
            if (written)
            {
                writeKeyChecked(key(field));
                emitScalarDefault(field);
            }
        }
        return written;
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
        DirectBufferEx value,
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
        DirectBufferEx value,
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
        DirectBufferEx value,
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

    private boolean closeContainer(
        Scope scope)
    {
        boolean written = writeEndChecked();
        if (written)
        {
            scope.openField = -1;
        }
        return written;
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
        DirectBufferEx buffer,
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
