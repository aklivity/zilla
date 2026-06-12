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
    private int valueWritten;
    private int valueTotal;
    private ProtobufEvent suspended;

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
        ProtobufPipeline.Status status = dispatch(source, event);
        if (status == ProtobufPipeline.Status.SUSPENDED)
        {
            suspended = event;
        }
        return status;
    }

    @Override
    public ProtobufPipeline.Status resume(
        ProtobufController control,
        ProtobufSource source)
    {
        // re-run the event that did not fit; the field/value position is held in pending and valueWritten,
        // so this continues a fragmented value or retries a whole field against the freshly drained buffer
        return dispatch(source, suspended);
    }

    @Override
    public void reset()
    {
        depth = -1;
        pending = null;
        valueWritten = 0;
        valueTotal = 0;
        suspended = null;
    }

    private ProtobufPipeline.Status dispatch(
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
            status = onValue(source);
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
            ? reserve(tagSize(pending.number()) + varintSize(source.length()))
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
                generator.startMessage(pending.number(), source.length());
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
        ProtobufSource source)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
        if (scope(depth).active && pending != null)
        {
            status = pending.type().wireType() == ProtobufWireType.LEN
                ? onValueLength(pending, source)
                : onValueScalar(pending, source);
        }
        return status;
    }

    private ProtobufPipeline.Status onValueLength(
        ProtobufField field,
        ProtobufSource source)
    {
        int number = field.number();
        int length = source.length();
        int deferred = source.bytesDeferred();
        int remaining = generator.remaining();
        ProtobufPipeline.Status status;
        if (valueWritten == 0 && deferred == 0 && tagSize(number) + varintSize(length) + length <= remaining)
        {
            // whole value present and it fits — write it in one piece
            generator.writeBytes(number, source.buffer(), source.offset(), length);
            status = ProtobufPipeline.Status.ADVANCED;
        }
        else if (valueWritten == 0 && deferred == 0 && generator.length() > 0)
        {
            // break at this field boundary; on a fresh buffer the whole value may fit
            generator.flush();
            status = ProtobufPipeline.Status.SUSPENDED;
        }
        else
        {
            // a value chunked on input (deferred > 0) or too large for one buffer streams via writeSegment
            status = writeChunk(number, length, deferred, source, remaining);
        }
        return status;
    }

    private ProtobufPipeline.Status writeChunk(
        int number,
        int length,
        int deferred,
        ProtobufSource source,
        int remaining)
    {
        if (valueWritten == 0)
        {
            // the first chunk carries the whole value's length: length now plus all that is still deferred
            valueTotal = length + deferred;
        }
        int chunkRemaining = valueTotal - deferred - valueWritten;
        int sourceOffset = source.offset() + length - chunkRemaining;
        int header = valueWritten == 0 ? tagSize(number) + varintSize(valueTotal) : 0;
        ProtobufPipeline.Status status;
        if (valueWritten == 0 && header + 1 > remaining && generator.length() > 0)
        {
            // can't even start the value here; a fresh buffer may hold the header
            generator.flush();
            status = ProtobufPipeline.Status.SUSPENDED;
        }
        else if (valueWritten == 0 && header + 1 > remaining)
        {
            throw new ProtobufException("value header exceeds output limit");
        }
        else
        {
            int now = Math.min(remaining - header, chunkRemaining);
            generator.writeSegment(number, source.buffer(), sourceOffset, now, valueTotal - valueWritten - now);
            valueWritten += now;
            if (now < chunkRemaining)
            {
                // output filled mid-chunk; suspend and replay this same value event
                generator.flush();
                status = ProtobufPipeline.Status.SUSPENDED;
            }
            else if (deferred > 0)
            {
                // chunk fully written, more input chunks of this value still to come
                status = ProtobufPipeline.Status.ADVANCED;
            }
            else
            {
                valueWritten = 0;
                valueTotal = 0;
                status = ProtobufPipeline.Status.ADVANCED;
            }
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
