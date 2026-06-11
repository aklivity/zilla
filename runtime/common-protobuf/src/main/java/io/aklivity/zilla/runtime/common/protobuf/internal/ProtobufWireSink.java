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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
public final class ProtobufWireSink implements ProtobufSink
{
    private final ProtobufSchema schema;
    private final String messageName;
    private final ProtobufWriter root;
    private final List<ExpandableArrayBuffer> scratch;
    private final List<ProtobufWriter> writers;
    private final Deque<Scope> scopes;

    private ProtobufField pending;

    public ProtobufWireSink(
        ProtobufGenerator generator,
        ProtobufSchema schema,
        String messageName)
    {
        this.schema = schema;
        this.messageName = messageName;
        this.root = ((ProtobufGeneratorImpl) generator).writer();
        this.scratch = new ArrayList<>();
        this.writers = new ArrayList<>();
        this.scopes = new ArrayDeque<>();
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
            Scope value = scopes.peek();
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
        scopes.clear();
        pending = null;
    }

    private void onStartMessage()
    {
        if (scopes.isEmpty())
        {
            scopes.push(new Scope(schema.message(messageName), root, null, null));
        }
        else
        {
            Scope parent = scopes.peek();
            if (parent.writer == null || pending == null || !pending.composite())
            {
                scopes.push(new Scope(null, null, null, null));
            }
            else
            {
                int depth = scopes.size();
                ExpandableArrayBuffer buffer = scratch(depth);
                ProtobufWriter writer = acquire(depth).wrap(buffer, 0);
                scopes.push(new Scope(schema.resolveMessage(pending), writer, pending, buffer));
            }
        }
    }

    private ProtobufPipeline.Status onEndMessage()
    {
        Scope scope = scopes.pop();
        ProtobufPipeline.Status status = ProtobufPipeline.Status.PENDING;
        if (scopes.isEmpty())
        {
            status = ProtobufPipeline.Status.COMPLETE;
        }
        else if (scope.writer != null && scope.field != null)
        {
            ProtobufWriter parent = scopes.peek().writer;
            parent.writeTag(scope.field.number(), ProtobufWireType.LEN);
            parent.writeBytes(scope.buffer, 0, scope.writer.length());
        }
        return status;
    }

    private ProtobufField mapField(
        ProtobufField field)
    {
        Scope scope = scopes.peek();
        return scope.writer != null && scope.message != null ? scope.message.field(field.name()) : null;
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
        private final ProtobufMessage message;
        private final ProtobufWriter writer;
        private final ProtobufField field;
        private final ExpandableArrayBuffer buffer;

        private Scope(
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
