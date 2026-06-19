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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

// Transparent pipeline stage that forwards every event unchanged while capturing the value of each
// registered top-level field as a side-effect, making it available to the read pipeline once the value
// completes. A length-delimited scalar (string/bytes) split across input windows arrives as repeated VALUE
// pieces with a decreasing deferred count; those are coalesced into the field buffer until none remain
// deferred. Numeric, boolean and enum values render to their ASCII text, matching the extraction surfaced
// by the legacy converter.
final class ProtobufExtractor implements ProtobufTransform
{
    private final List<Field> fields;

    private int depth;
    private Field current;

    ProtobufExtractor()
    {
        this.fields = new ArrayList<>();
    }

    void register(
        String name)
    {
        if (find(name) == null)
        {
            fields.add(new Field(name));
        }
    }

    int length(
        String name)
    {
        Field field = find(name);
        return field != null ? field.length : 0;
    }

    DirectBuffer value(
        String name)
    {
        Field field = find(name);
        return field != null ? field.value : null;
    }

    @Override
    public Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event,
        ProtobufSink sink)
    {
        if (!fields.isEmpty())
        {
            observe(source, event);
        }
        return sink.feed(control, source, event);
    }

    @Override
    public void reset()
    {
        depth = 0;
        current = null;
        for (int i = 0; i < fields.size(); i++)
        {
            fields.get(i).length = 0;
        }
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
            current = depth == 1 ? find(source.field().name()) : null;
            if (current != null)
            {
                current.length = 0;
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
        DirectBuffer segment = source.segment();
        int length = segment.capacity();
        current.value.putBytes(current.length, segment, 0, length);
        current.length += length;
        if (source.deferredBytes() == 0)
        {
            current = null;
        }
    }

    private Field find(
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
        return result;
    }

    private static final class Field
    {
        private final String name;
        private final MutableDirectBuffer value;

        private int length;

        private Field(
            String name)
        {
            this.name = name;
            this.value = new ExpandableDirectByteBuffer();
        }
    }
}
