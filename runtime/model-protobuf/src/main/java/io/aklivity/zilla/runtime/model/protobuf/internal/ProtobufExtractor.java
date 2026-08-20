/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

// Transparent pipeline stage that forwards every event unchanged while capturing the value of every
// top-level field as a side-effect, making it available to the read pipeline once the value completes.
// A length-delimited scalar (string/bytes) split across input windows arrives as repeated VALUE pieces with
// a decreasing deferred count; those are coalesced into the field buffer until none remain deferred.
// Numeric, boolean and enum values render to their ASCII text, matching the extraction surfaced by the
// legacy converter.
final class ProtobufExtractor implements ProtobufTransform
{
    // every distinct field name ever seen on this extractor, keyed by name and never shrunk: a Field's
    // name/path/value buffer are all allocated exactly once (see supplyField) and reused across every
    // later message that has a field of that name, however many messages that extractor lives across
    private final List<Field> fields;
    // fields captured for the in-flight message, in encounter order; cleared (not reallocated) on reset()
    private final List<Field> captured;

    private int depth;
    private Field current;

    ProtobufExtractor()
    {
        this.fields = new ArrayList<>();
        this.captured = new ArrayList<>();
    }

    int captured()
    {
        return captured.size();
    }

    // pre-joined "$." + name, computed once per distinct field name (see supplyField) rather than on
    // every capture, so a caller building a JSON pointer path never pays a per-call concatenation
    String path(
        int index)
    {
        return captured.get(index).path;
    }

    int length(
        int index)
    {
        return captured.get(index).length;
    }

    DirectBufferEx value(
        int index)
    {
        return captured.get(index).value;
    }

    @Override
    public Status transform(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event,
        ProtobufSink sink)
    {
        observe(source, event);
        return sink.transform(control, source, event);
    }

    @Override
    public void reset()
    {
        depth = 0;
        captured.clear();
        current = null;
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    private void observe(
        ProtobufSource source,
        ProtobufEvent event)
    {
        switch (event)
        {
        case START_MESSAGE:
        case START_GROUP:
            depth++;
            current = null;
            break;
        case END_MESSAGE:
        case END_GROUP:
            depth--;
            current = null;
            break;
        case FIELD:
            current = depth == 1 ? supplyField(source.field().name()) : null;
            if (current != null)
            {
                current.length = 0;
                // a repeated top-level field re-observed within the same message overwrites its value in
                // place rather than appearing twice in capture order (matches supplyField's Field reuse)
                if (!captured.contains(current))
                {
                    captured.add(current);
                }
            }
            break;
        case VALUE:
            if (current != null)
            {
                observeValue(source);
            }
            break;
        default:
            current = null;
            break;
        }
    }

    private void observeValue(
        ProtobufSource source)
    {
        ProtobufField field = source.field();
        if (field != null && field.type().wireType() == ProtobufWireType.LEN)
        {
            appendSegment(source);
        }
        else if (field != null)
        {
            renderScalar(source, field);
            current = null;
        }
        else
        {
            current = null;
        }
    }

    private void renderScalar(
        ProtobufSource source,
        ProtobufField field)
    {
        switch (field.type())
        {
        case DOUBLE:
            current.length = current.value.putStringWithoutLengthAscii(0, String.valueOf(source.doubleValue()));
            break;
        case FLOAT:
            current.length = current.value.putStringWithoutLengthAscii(0, String.valueOf(source.floatValue()));
            break;
        case BOOL:
            current.length = current.value.putStringWithoutLengthAscii(0, String.valueOf(source.longValue() != 0L));
            break;
        case INT64:
        case UINT64:
        case SINT64:
        case FIXED64:
        case SFIXED64:
            current.length = current.value.putLongAscii(0, source.longValue());
            break;
        default:
            current.length = current.value.putIntAscii(0, (int) source.longValue());
            break;
        }
    }

    private void appendSegment(
        ProtobufSource source)
    {
        DirectBufferEx segment = source.segment();
        int length = segment.capacity();
        current.value.putBytes(current.length, segment, 0, length);
        current.length += length;
        if (source.deferredBytes() == 0)
        {
            current = null;
        }
    }

    // searches every field name ever seen on this extractor (not just this message's), so a name that
    // recurs across messages — the common case, since the same schema shapes every message on a stream —
    // reuses its Field (and thus its name/path Strings) instead of reallocating them every call
    private Field supplyField(
        String name)
    {
        Field result = null;
        for (int i = 0; result == null && i < fields.size(); i++)
        {
            if (fields.get(i).name.equals(name))
            {
                result = fields.get(i);
            }
        }
        if (result == null)
        {
            result = new Field();
            result.name = name;
            result.path = "$." + name;
            fields.add(result);
        }
        return result;
    }

    private static final class Field
    {
        private final MutableDirectBufferEx value;

        private String name;
        private String path;
        private int length;

        private Field()
        {
            this.value = new ExpandableDirectByteBufferEx();
        }
    }
}
