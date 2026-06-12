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
 * with their subtrees). It drives the public {@link ProtobufGenerator} API: a nested message opens with
 * {@code startMessage(field, source.length())} — the input length as the optimistic slot estimate — so
 * the body streams straight to the output and {@link ProtobufGenerator#endMessage()} fills the length
 * (canonical when the width matches, padded when smaller). When {@link ProtobufGenerator#remaining()}
 * runs low at a field boundary it calls {@link ProtobufGenerator#flush()} and returns
 * {@link ProtobufPipeline.Status#SUSPENDED}; the caller drains, re-wraps the generator (which reopens the
 * open levels), and resumes. So a message that fits encodes as one canonical record and one too large to
 * buffer streams as merge-able records — with no scratch buffers and no mode switch.
 */
public final class ProtobufTypedSinkImpl implements ProtobufSink
{
    private static final int HEADROOM = 16;

    private final ProtobufSchema schema;
    private final String messageName;
    private final ProtobufGenerator generator;
    private final List<Scope> scopes;

    private int depth;
    private ProtobufField pending;

    public ProtobufTypedSinkImpl(
        ProtobufGenerator generator,
        ProtobufSchema schema,
        String messageName)
    {
        this.schema = schema;
        this.messageName = messageName;
        this.generator = generator;
        this.scopes = new ArrayList<>();
        this.depth = -1;
    }

    @Override
    public ProtobufPipeline.Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
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
            if (scope(depth).active && pending != null)
            {
                writeScalar(pending, source);
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

        if (status == ProtobufPipeline.Status.RESUMABLE && depth >= 0 &&
            generator.length() > 0 && generator.remaining() < HEADROOM)
        {
            generator.flush();
            status = ProtobufPipeline.Status.SUSPENDED;
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = -1;
        pending = null;
    }

    private void onStartMessage(
        ProtobufSource source)
    {
        depth++;
        Scope scope = scope(depth);
        if (depth == 0)
        {
            scope.set(schema.message(messageName), true);
        }
        else if (!scope(depth - 1).active || pending == null || !pending.composite())
        {
            scope.set(null, false);
        }
        else
        {
            generator.startMessage(pending.number(), source.length());
            scope.set(schema.resolveMessage(pending), true);
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
        else if (scope.active)
        {
            generator.endMessage();
        }
        depth--;
        return status;
    }

    private void onStartGroup()
    {
        depth++;
        Scope scope = scope(depth);
        if (!scope(depth - 1).active || pending == null || !pending.composite())
        {
            scope.set(null, false);
        }
        else
        {
            generator.startGroup(pending.number());
            scope.set(schema.resolveMessage(pending), true);
        }
    }

    private void onEndGroup()
    {
        Scope scope = scope(depth);
        if (scope.active)
        {
            generator.endGroup();
        }
        depth--;
    }

    private ProtobufField mapField(
        ProtobufField field)
    {
        Scope scope = scope(depth);
        return scope.active && scope.message != null ? scope.message.field(field.name()) : null;
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
        ProtobufSource source)
    {
        int number = field.number();
        switch (field.type())
        {
        case INT32:
            generator.writeInt32(number, (int) source.longValue());
            break;
        case INT64:
            generator.writeInt64(number, source.longValue());
            break;
        case UINT32:
            generator.writeUInt32(number, (int) source.longValue());
            break;
        case UINT64:
            generator.writeUInt64(number, source.longValue());
            break;
        case SINT32:
            generator.writeSInt32(number, (int) source.longValue());
            break;
        case SINT64:
            generator.writeSInt64(number, source.longValue());
            break;
        case FIXED32:
            generator.writeFixed32(number, (int) source.longValue());
            break;
        case FIXED64:
            generator.writeFixed64(number, source.longValue());
            break;
        case SFIXED32:
            generator.writeSFixed32(number, (int) source.longValue());
            break;
        case SFIXED64:
            generator.writeSFixed64(number, source.longValue());
            break;
        case FLOAT:
            generator.writeFloat(number, source.floatValue());
            break;
        case DOUBLE:
            generator.writeDouble(number, source.doubleValue());
            break;
        case BOOL:
            generator.writeBool(number, source.longValue() != 0L);
            break;
        case ENUM:
            generator.writeEnum(number, (int) source.longValue());
            break;
        case STRING:
        case BYTES:
            generator.writeBytes(number, source.buffer(), source.offset(), source.length());
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private static final class Scope
    {
        private ProtobufMessage message;
        private boolean active;

        private void set(
            ProtobufMessage message,
            boolean active)
        {
            this.message = message;
            this.active = active;
        }
    }
}
