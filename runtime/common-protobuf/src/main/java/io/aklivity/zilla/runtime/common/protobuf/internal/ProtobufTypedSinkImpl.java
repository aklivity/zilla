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

/**
 * A terminal sink that writes the event stream back out as Protobuf wire against a target schema,
 * mapping each event's field by name into the target message (fields absent in the target are dropped
 * with their subtrees). It drives the public {@link ProtobufGenerator} API only. When the whole message
 * fits the generator's limit it is encoded minimally — each nested message body is built in a scratch
 * generator and emitted with {@link ProtobufGenerator#writeMessage} (minimal length, canonical). When
 * it does not fit, the sink switches to a chunking encode: nested messages use the generator's padded
 * {@link ProtobufGenerator#startMessage(int)}; at a field boundary near the limit every open level is
 * closed, the buffer is reported drainable with {@link ProtobufPipeline.Status#SUSPENDED}, and on the
 * resumed feed each level is reopened against the fresh buffer. The fits-vs-stream choice is made at the
 * root from the input length, so the chunked (non-canonical) form is only observed when forced.
 */
public final class ProtobufTypedSinkImpl implements ProtobufSink
{
    private static final int HEADROOM = 16;

    private final ProtobufSchema schema;
    private final String messageName;
    private final ProtobufGenerator generator;
    private final List<ExpandableArrayBuffer> scratch;
    private final List<ProtobufGenerator> generators;
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
        this.scratch = new ArrayList<>();
        this.generators = new ArrayList<>();
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
            if (value.gen != null && pending != null)
            {
                writeScalar(pending, source, value.gen);
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
            scope.set(schema.message(messageName), generator, null, false, null);
        }
        else
        {
            Scope parent = scope(depth - 1);
            if (parent.gen == null || pending == null || !pending.composite())
            {
                scope.set(null, null, null, false, null);
            }
            else if (chunked)
            {
                generator.startMessage(pending.number());
                scope.set(schema.resolveMessage(pending), generator, pending, false, null);
            }
            else
            {
                ExpandableArrayBuffer buffer = scratch(depth);
                ProtobufGenerator nested = acquire(depth).wrap(buffer, 0);
                scope.set(schema.resolveMessage(pending), nested, pending, false, buffer);
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
        else if (scope.gen != null && scope.field != null)
        {
            if (chunked)
            {
                generator.endMessage();
            }
            else
            {
                scope(depth - 1).gen.writeMessage(scope.field.number(), scope.buffer, 0, scope.gen.length());
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
        if (parent.gen == null || pending == null || !pending.composite())
        {
            scope.set(null, null, null, false, null);
        }
        else
        {
            parent.gen.startGroup(pending.number());
            scope.set(schema.resolveMessage(pending), parent.gen, pending, true, null);
        }
    }

    private void onEndGroup()
    {
        Scope scope = scope(depth);
        if (scope.gen != null && scope.field != null)
        {
            scope.gen.endGroup();
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
            if (scope.gen != null && scope.field != null)
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
            if (scope.gen != null && scope.field != null)
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
        return scope.gen != null && scope.message != null ? scope.message.field(field.name()) : null;
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
        ProtobufGenerator gen)
    {
        int number = field.number();
        switch (field.type())
        {
        case INT32:
            gen.writeInt32(number, (int) source.longValue());
            break;
        case INT64:
            gen.writeInt64(number, source.longValue());
            break;
        case UINT32:
            gen.writeUInt32(number, (int) source.longValue());
            break;
        case UINT64:
            gen.writeUInt64(number, source.longValue());
            break;
        case SINT32:
            gen.writeSInt32(number, (int) source.longValue());
            break;
        case SINT64:
            gen.writeSInt64(number, source.longValue());
            break;
        case FIXED32:
            gen.writeFixed32(number, (int) source.longValue());
            break;
        case FIXED64:
            gen.writeFixed64(number, source.longValue());
            break;
        case SFIXED32:
            gen.writeSFixed32(number, (int) source.longValue());
            break;
        case SFIXED64:
            gen.writeSFixed64(number, source.longValue());
            break;
        case FLOAT:
            gen.writeFloat(number, source.floatValue());
            break;
        case DOUBLE:
            gen.writeDouble(number, source.doubleValue());
            break;
        case BOOL:
            gen.writeBool(number, source.longValue() != 0L);
            break;
        case ENUM:
            gen.writeEnum(number, (int) source.longValue());
            break;
        case STRING:
        case BYTES:
            gen.writeBytes(number, source.buffer(), source.offset(), source.length());
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

    private ProtobufGenerator acquire(
        int depth)
    {
        while (scratch.size() <= depth)
        {
            scratch.add(new ExpandableArrayBuffer());
            generators.add(new ProtobufGeneratorImpl());
        }
        return generators.get(depth);
    }

    private static final class Scope
    {
        private ProtobufMessage message;
        private ProtobufGenerator gen;
        private ProtobufField field;
        private ExpandableArrayBuffer buffer;
        private boolean group;

        private void set(
            ProtobufMessage message,
            ProtobufGenerator gen,
            ProtobufField field,
            boolean group,
            ExpandableArrayBuffer buffer)
        {
            this.message = message;
            this.gen = gen;
            this.field = field;
            this.group = group;
            this.buffer = buffer;
        }
    }
}
