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
 * mapping each event's field by name into the target message (fields absent in the target are dropped
 * with their subtrees). When the whole message fits the generator's limit it is encoded minimally —
 * nested messages length-prefixed via per-depth scratch (canonical). When it does not fit, the sink
 * switches to a chunking encode: nested messages use the generator's padded length slots, and at a
 * field boundary where the output nears its limit every open level is closed, the buffer is reported
 * drainable via {@link ProtobufPipeline.Status#SUSPENDED}, and on the resumed feed each level is
 * reopened against the fresh buffer — the resulting records reassemble by message-merge semantics. The
 * fits choice is made at the root from the input length, so an observer only sees the chunked form when
 * a message is genuinely too large to buffer.
 */
public final class ProtobufTypedSinkImpl implements ProtobufSink
{
    private static final int HEADROOM = 16;

    private final ProtobufSchema schema;
    private final String messageName;
    private final ProtobufGenerator generator;
    private final ProtobufWriter root;
    private final List<ExpandableArrayBuffer> scratch;
    private final List<ProtobufWriter> writers;
    private final List<Scope> scopes;

    private int depth;
    private ProtobufField pending;
    private boolean chunked;
    private boolean reopen;

    public ProtobufTypedSinkImpl(
        ProtobufGenerator generator,
        ProtobufSchema schema,
        String messageName)
    {
        this.schema = schema;
        this.messageName = messageName;
        this.generator = generator;
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
        if (reopen)
        {
            reopen();
            reopen = false;
        }

        ProtobufPipeline.Status status = ProtobufPipeline.Status.RESUMABLE;
        switch (event)
        {
        case START_MESSAGE:
            onStartMessage(source);
            break;
        case START_GROUP:
            onStartGroup();
            break;
        case FIELD:
            pending = mapField(source.field());
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
        case END_GROUP:
            onEndGroup();
            break;
        default:
            break;
        }

        if (chunked && status == ProtobufPipeline.Status.RESUMABLE && depth >= 0 &&
            generator.length() > 0 && generator.remaining() < HEADROOM)
        {
            closeAll();
            reopen = true;
            status = ProtobufPipeline.Status.SUSPENDED;
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = -1;
        pending = null;
        chunked = false;
        reopen = false;
    }

    private void onStartMessage(
        ProtobufSource source)
    {
        depth++;
        Scope scope = scope(depth);
        if (depth == 0)
        {
            chunked = source.length() > generator.remaining();
            scope.set(schema.message(messageName), root, null, null);
        }
        else
        {
            Scope parent = scope(depth - 1);
            if (parent.writer == null || pending == null || !pending.composite())
            {
                scope.set(null, null, null, null);
            }
            else if (chunked)
            {
                generator.startMessage(pending.number());
                scope.set(schema.resolveMessage(pending), root, pending, null);
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
        ProtobufPipeline.Status status = ProtobufPipeline.Status.RESUMABLE;
        if (depth == 0)
        {
            status = ProtobufPipeline.Status.COMPLETE;
        }
        else if (scope.writer != null && scope.field != null)
        {
            if (chunked)
            {
                generator.endMessage();
            }
            else
            {
                ProtobufWriter parent = scope(depth - 1).writer;
                parent.writeTag(scope.field.number(), ProtobufWireType.LEN);
                parent.writeBytes(scope.buffer, 0, scope.writer.length());
            }
        }
        depth--;
        return status;
    }

    private void onStartGroup()
    {
        depth++;
        Scope scope = scope(depth);
        Scope parent = scope(depth - 1);
        if (parent.writer == null || pending == null || !pending.composite())
        {
            scope.set(null, null, null, null);
        }
        else if (chunked)
        {
            generator.startGroup(pending.number());
            scope.setGroup(schema.resolveMessage(pending), root, pending);
        }
        else
        {
            parent.writer.writeTag(pending.number(), ProtobufWireType.SGROUP);
            scope.setGroup(schema.resolveMessage(pending), parent.writer, pending);
        }
    }

    private void onEndGroup()
    {
        Scope scope = scope(depth);
        if (scope.writer != null && scope.field != null)
        {
            if (chunked)
            {
                generator.endGroup();
            }
            else
            {
                scope.writer.writeTag(scope.field.number(), ProtobufWireType.EGROUP);
            }
        }
        depth--;
    }

    // Closes every open level in the chunked encode (innermost first) so the buffer is a complete,
    // drainable chunk; the scopes stay open so reopen() can re-emit their headers after the drain.
    private void closeAll()
    {
        for (int d = depth; d >= 1; d--)
        {
            Scope scope = scopes.get(d);
            if (scope.writer != null && scope.field != null)
            {
                if (scope.group)
                {
                    generator.endGroup();
                }
                else
                {
                    generator.endMessage();
                }
            }
        }
    }

    // Re-emits each still-open level's header (outermost first) into the freshly wrapped buffer so the
    // next chunk carries its own record for that field.
    private void reopen()
    {
        for (int d = 1; d <= depth; d++)
        {
            Scope scope = scopes.get(d);
            if (scope.writer != null && scope.field != null)
            {
                if (scope.group)
                {
                    generator.startGroup(scope.field.number());
                }
                else
                {
                    generator.startMessage(scope.field.number());
                }
            }
        }
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
        private boolean group;

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
            this.group = false;
        }

        private void setGroup(
            ProtobufMessage message,
            ProtobufWriter writer,
            ProtobufField field)
        {
            this.message = message;
            this.writer = writer;
            this.field = field;
            this.buffer = null;
            this.group = true;
        }
    }
}
