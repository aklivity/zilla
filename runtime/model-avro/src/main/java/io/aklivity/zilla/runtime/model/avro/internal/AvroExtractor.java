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
package io.aklivity.zilla.runtime.model.avro.internal;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;

// Transparent pipeline stage that forwards every event unchanged while capturing the value of each
// registered top-level field as a side-effect, making it available to the read pipeline once the value
// completes. A length-delimited value (string/bytes/fixed) split across input windows arrives as a leading
// event with deferred bytes followed by SEGMENT continuations; those are coalesced into the field buffer
// until no bytes remain deferred. Numeric and boolean values render to their ASCII text, matching the
// extraction surfaced by the legacy converter.
final class AvroExtractor implements AvroTransform
{
    private final List<Field> fields;

    private int depth;
    private Field current;

    AvroExtractor()
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
    public Status transform(
        AvroController control,
        AvroSource source,
        AvroEvent event,
        AvroSink sink)
    {
        if (!fields.isEmpty())
        {
            observe(source, event);
        }
        return sink.transform(control, source, event);
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
        AvroSource source,
        AvroEvent event)
    {
        switch (event)
        {
        case START_RECORD:
        case START_ARRAY:
        case START_MAP:
            depth++;
            current = null;
            break;
        case END_RECORD:
        case END_ARRAY:
        case END_MAP:
            depth--;
            current = null;
            break;
        case FIELD_NAME:
            current = depth == 1 ? find(source.getField()) : null;
            if (current != null)
            {
                current.length = 0;
            }
            break;
        case STRING:
        case BYTES:
        case FIXED:
        case SEGMENT:
            if (current != null)
            {
                appendSegment(source);
            }
            break;
        case INT:
        case ENUM:
            if (current != null)
            {
                current.length = current.value.putIntAscii(0, source.getInt());
                current = null;
            }
            break;
        case LONG:
            if (current != null)
            {
                current.length = current.value.putLongAscii(0, source.getLong());
                current = null;
            }
            break;
        case FLOAT:
            if (current != null)
            {
                current.length = current.value.putStringWithoutLengthAscii(0, String.valueOf(source.getFloat()));
                current = null;
            }
            break;
        case DOUBLE:
            if (current != null)
            {
                current.length = current.value.putStringWithoutLengthAscii(0, String.valueOf(source.getDouble()));
                current = null;
            }
            break;
        case BOOLEAN:
            if (current != null)
            {
                current.length = current.value.putStringWithoutLengthAscii(0, String.valueOf(source.getBoolean()));
                current = null;
            }
            break;
        case UNION_BRANCH:
        case MAP_KEY:
        case START_MESSAGE:
        case END_MESSAGE:
            break;
        default:
            current = null;
            break;
        }
    }

    private void appendSegment(
        AvroSource source)
    {
        DirectBuffer segment = source.getSegment();
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
