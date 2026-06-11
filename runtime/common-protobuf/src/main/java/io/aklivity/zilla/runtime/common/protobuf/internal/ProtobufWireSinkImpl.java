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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import java.util.ArrayList;
import java.util.List;

import org.agrona.ExpandableArrayBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A terminal sink that writes the event stream back out as Protobuf wire against a target schema,
 * mapping each event's field by name into the target message. When the target schema is the read
 * schema this is a straight re-encode; when it differs (renamed/renumbered/dropped fields) this is a
 * schema transformation. Fields absent in the target are dropped (with their subtrees); nested
 * messages are length-prefixed via per-depth scratch.
 */
public final class ProtobufWireSinkImpl implements ProtobufSink
{
    private final ProtobufSchema schema;
    private final String messageName;
    private final ProtobufWriter root;
    private final List<ExpandableArrayBuffer> scratch;
    private final List<ProtobufWriter> writers;
    private final List<Scope> scopes;

    private int depth;
    private ProtobufField pending;

    public ProtobufWireSinkImpl(
        ProtobufGenerator generator,
        ProtobufSchema schema,
        String messageName)
    {
        this.schema = schema;
        this.messageName = messageName;
        this.root = ((ProtobufGeneratorImpl) generator).writer();
        this.scratch = new ArrayList<>();
        this.writers = new ArrayList<>();
        this.scopes = new ArrayList<>();
        this.depth = -1;
    }

    @Override
    public ProtobufPipeline.Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.PENDING;
        switch (event)
        {
        case START_MESSAGE:
            onStartMessage();
            break;
        case FIELD:
            ProtobufField mapped = mapField(source.field());
            pending = mapped;
            break;
        case VALUE:
            Scope value = scopes.get(depth);
            if (value.writer != null && pending != null)
            {
                writeScalar(pending, source, value.writer);
            }
            break;
        case END_MESSAGE:
            status = onEndMessage();
            break;
        default:
            break;
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = -1;
        pending = null;
    }

    private void onStartMessage()
    {
        depth++;
        Scope scope = scope(depth);
        if (depth == 0)
        {
            scope.set(schema.message(messageName), root, null, null);
        }
        else
        {
            Scope parent = scope(depth - 1);
            if (parent.writer == null || pending == null || !pending.composite())
            {
                scope.set(null, null, null, null);
            }
            else
            {
                ExpandableArrayBuffer buffer = scratch(depth);
                ProtobufWriter writer = acquire(depth).wrap(buffer, 0);
                scope.set(schema.resolveMessage(pending), writer, pending, buffer);
            }
        }
    }

    private ProtobufPipeline.Status onEndMessage()
    {
        Scope scope = scope(depth);
        ProtobufPipeline.Status status = ProtobufPipeline.Status.PENDING;
        if (depth == 0)
        {
            status = ProtobufPipeline.Status.COMPLETE;
        }
        else if (scope.writer != null && scope.field != null)
        {
            ProtobufWriter parent = scope(depth - 1).writer;
            parent.writeTag(scope.field.number(), ProtobufWireType.LEN);
            parent.writeBytes(scope.buffer, 0, scope.writer.length());
        }
        depth--;
        return status;
    }

    private ProtobufField mapField(
        ProtobufField field)
    {
        Scope scope = scope(depth);
        return scope.writer != null && scope.message != null ? scope.message.field(field.name()) : null;
    }

    private Scope scope(
        int depth)
    {
        while (scopes.size() <= depth)
        {
            scopes.add(new Scope());
        }
        return scopes.get(depth);
    }

    private void writeScalar(
        ProtobufField field,
        ProtobufSource source,
        ProtobufWriter writer)
    {
        switch (field.type())
        {
        case INT32:
        case INT64:
        case UINT64:
        case BOOL:
        case ENUM:
            writer.writeTag(field.number(), ProtobufWireType.VARINT);
            writer.writeVarint64(source.longValue());
            break;
        case UINT32:
            writer.writeTag(field.number(), ProtobufWireType.VARINT);
            writer.writeVarint64(source.longValue() & 0xffffffffL);
            break;
        case SINT32:
            writer.writeTag(field.number(), ProtobufWireType.VARINT);
            writer.writeZigzag32((int) source.longValue());
            break;
        case SINT64:
            writer.writeTag(field.number(), ProtobufWireType.VARINT);
            writer.writeZigzag64(source.longValue());
            break;
        case FIXED32:
        case SFIXED32:
            writer.writeTag(field.number(), ProtobufWireType.I32);
            writer.writeFixed32((int) source.longValue());
            break;
        case FIXED64:
        case SFIXED64:
            writer.writeTag(field.number(), ProtobufWireType.I64);
            writer.writeFixed64(source.longValue());
            break;
        case DOUBLE:
            writer.writeTag(field.number(), ProtobufWireType.I64);
            writer.writeFixed64(Double.doubleToLongBits(source.doubleValue()));
            break;
        case FLOAT:
            writer.writeTag(field.number(), ProtobufWireType.I32);
            writer.writeFixed32(Float.floatToIntBits(source.floatValue()));
            break;
        case STRING:
        case BYTES:
            writer.writeTag(field.number(), ProtobufWireType.LEN);
            writer.writeBytes(source.buffer(), source.offset(), source.length());
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private ExpandableArrayBuffer scratch(
        int depth)
    {
        acquire(depth);
        return scratch.get(depth);
    }

    private ProtobufWriter acquire(
        int depth)
    {
        while (scratch.size() <= depth)
        {
            scratch.add(new ExpandableArrayBuffer());
            writers.add(new ProtobufWriter());
        }
        return writers.get(depth);
    }

    private static final class Scope
    {
        private ProtobufMessage message;
        private ProtobufWriter writer;
        private ProtobufField field;
        private ExpandableArrayBuffer buffer;

        private void set(
            ProtobufMessage message,
            ProtobufWriter writer,
            ProtobufField field,
            ExpandableArrayBuffer buffer)
        {
            this.message = message;
            this.writer = writer;
            this.field = field;
            this.buffer = buffer;
        }
    }
}
