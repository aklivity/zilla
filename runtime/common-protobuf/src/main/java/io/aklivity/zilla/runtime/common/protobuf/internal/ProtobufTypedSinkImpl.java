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

import org.agrona.DirectBuffer;

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
 * with their subtrees). Output is one logical byte stream split into flow-control chunks the consumer
 * concatenates before parsing, so the per-record fragments of a too-large message merge on decode.
 * <p>
 * Before each write the sink checks the field fits the hard limit. A field that does not fit yet drains:
 * the sink calls {@link ProtobufGenerator#flush()} (closing the open message records with their lengths)
 * and returns {@link ProtobufPipeline.Status#SUSPENDED}, leaving the field unwritten; the caller drains
 * and re-wraps, and the pump replays the event against the fresh buffer, where the generator reopens the
 * records. A length-delimited value larger than a whole chunk cannot be deferred to a fresh buffer, so it
 * is fragmented mid-byte via {@link ProtobufGenerator#writeSegment} — its length prefix written once and
 * its body streamed across chunks, the enclosing records carrying the deferred remainder until it is
 * fully written. {@code valueWritten} tracks how much of the in-flight value has been emitted so a
 * replayed {@code VALUE} event resumes where it left off.
 */
public final class ProtobufTypedSinkImpl implements ProtobufSink
{
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
        return dispatch(control, source, event);
    }

    @Override
    public ProtobufPipeline.Status resume(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        // re-run the event that did not fit; pending holds the field and the source re-exposes the value's
        // unconsumed remainder, so this continues a fragmented value or retries a whole field on a fresh buffer
        return dispatch(control, source, event);
    }

    @Override
    public void reset()
    {
        depth = -1;
        pending = null;
    }

    private ProtobufPipeline.Status dispatch(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
        switch (event)
        {
        case START_MESSAGE:
            status = onStartMessage(source);
            break;
        case START_GROUP:
            status = onStartGroup();
            break;
        case FIELD:
            pending = mapField(source.field());
            break;
        case VALUE:
            status = onValue(control, source);
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
        return status;
    }

    private ProtobufPipeline.Status onStartMessage(
        ProtobufSource source)
    {
        boolean root = depth < 0;
        boolean nested = !root && scope(depth).active && pending != null && pending.composite();
        ProtobufPipeline.Status status = nested
            ? reserve(tagSize(pending.number()) + varintSize(source.segment().capacity()))
            : ProtobufPipeline.Status.ADVANCED;
        if (status == ProtobufPipeline.Status.ADVANCED)
        {
            depth++;
            Scope scope = scope(depth);
            if (root)
            {
                scope.set(schema.message(messageName), true);
            }
            else if (!nested)
            {
                scope.set(null, false);
            }
            else
            {
                generator.startMessage(pending.number(), source.segment().capacity());
                scope.set(schema.resolveMessage(pending), true);
            }
        }
        return status;
    }

    private ProtobufPipeline.Status onStartGroup()
    {
        boolean nested = scope(depth).active && pending != null && pending.composite();
        ProtobufPipeline.Status status = nested
            ? reserve(tagSize(pending.number()))
            : ProtobufPipeline.Status.ADVANCED;
        if (status == ProtobufPipeline.Status.ADVANCED)
        {
            depth++;
            Scope scope = scope(depth);
            if (!nested)
            {
                scope.set(null, false);
            }
            else
            {
                generator.startGroup(pending.number());
                scope.set(schema.resolveMessage(pending), true);
            }
        }
        return status;
    }

    private ProtobufPipeline.Status onValue(
        ProtobufController control,
        ProtobufSource source)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
        if (scope(depth).active && pending != null)
        {
            status = pending.type().wireType() == ProtobufWireType.LEN
                ? onValueLength(control, pending, source)
                : onValueScalar(pending, source);
        }
        return status;
    }

    // A length-delimited value streams through the consumption-driven generator: it takes only what fits, the
    // sink pushes the unconsumed remainder back to the source via control.consumed(), and on resume the source
    // re-exposes that remainder — so the sink tracks no write cursor of its own. A whole value that fits is one
    // writeSegment call producing a single canonical record (its prefix is the minimal length varint).
    private ProtobufPipeline.Status onValueLength(
        ProtobufController control,
        ProtobufField field,
        ProtobufSource source)
    {
        DirectBuffer segment = source.segment();
        int available = segment.capacity();
        int deferred = source.deferredBytes();
        int before = generator.consumed();
        generator.writeSegment(field.number(), segment, 0, available, deferred);
        int written = generator.consumed() - before;
        control.consumed(written);
        ProtobufPipeline.Status status;
        if (written < available)
        {
            if (generator.deferring())
            {
                // input-bound: the generator wrote every whole unit it could and holds a sub-unit tail that
                // only the next input window can complete; STARVE so the driver feeds the next window with the
                // unconsumed tail re-presented to combine with it. No flush — the output is not drained here;
                // the in-flight value (and its open string) keeps streaming into the same output window
                status = ProtobufPipeline.Status.STARVED;
            }
            else if (generator.length() > 0)
            {
                // output filled before this chunk drained; drain and replay against a fresh buffer
                generator.flush();
                status = ProtobufPipeline.Status.SUSPENDED;
            }
            else
            {
                throw new ProtobufException("value header exceeds output limit");
            }
        }
        else
        {
            // this chunk fully written: ADVANCED whether the value completed (deferred == 0) or more input follows
            status = ProtobufPipeline.Status.ADVANCED;
        }
        return status;
    }

    private ProtobufPipeline.Status onValueScalar(
        ProtobufField field,
        ProtobufSource source)
    {
        ProtobufPipeline.Status status = reserve(tagSize(field.number()) + scalarSize(field));
        if (status == ProtobufPipeline.Status.ADVANCED)
        {
            writeScalar(field, source);
        }
        return status;
    }

    private ProtobufPipeline.Status onEndMessage()
    {
        Scope scope = scope(depth);
        ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
        if (depth == 0)
        {
            status = ProtobufPipeline.Status.COMPLETED;
        }
        else if (scope.active)
        {
            generator.endMessage();
        }
        depth--;
        return status;
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

    // flush-and-suspend when the next field will not fit; reject when even a freshly drained buffer cannot
    // hold it (nothing written yet, so the flush would free nothing)
    private ProtobufPipeline.Status reserve(
        int need)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
        if (need > generator.remaining())
        {
            if (generator.length() > 0)
            {
                generator.flush();
                status = ProtobufPipeline.Status.SUSPENDED;
            }
            else
            {
                throw new ProtobufException("value of " + need + " bytes exceeds output limit");
            }
        }
        return status;
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
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private static int scalarSize(
        ProtobufField field)
    {
        int size;
        switch (field.type())
        {
        case FIXED32:
        case SFIXED32:
        case FLOAT:
            size = 4;
            break;
        case FIXED64:
        case SFIXED64:
        case DOUBLE:
            size = 8;
            break;
        default:
            size = 10;
            break;
        }
        return size;
    }

    private static int tagSize(
        int number)
    {
        return varintSize((long) number << 3);
    }

    private static int varintSize(
        long value)
    {
        long remaining = value & 0xffffffffL;
        int size = 1;
        while (remaining >= 0x80L)
        {
            remaining >>>= 7;
            size++;
        }
        return size;
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
