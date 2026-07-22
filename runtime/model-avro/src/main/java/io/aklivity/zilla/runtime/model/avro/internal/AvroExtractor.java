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
package io.aklivity.zilla.runtime.model.avro.internal;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;

// Transparent pipeline stage that forwards every event unchanged while capturing the value of every
// top-level field as a side-effect, making it available to the read pipeline once the value completes.
// A length-delimited value (string/bytes/fixed) split across input windows arrives as a leading event with
// deferred bytes followed by SEGMENT continuations; those are coalesced into the field buffer until no bytes
// remain deferred. Numeric and boolean values render to their ASCII text, matching the extraction surfaced
// by the legacy converter.
final class AvroExtractor implements AvroTransform
{
    private final List<Field> fields;

    private int captured;
    private int depth;
    private Field current;

    AvroExtractor()
    {
        this.fields = new ArrayList<>();
    }

    int captured()
    {
        return captured;
    }

    String name(
        int index)
    {
        return fields.get(index).name;
    }

    int length(
        int index)
    {
        return fields.get(index).length;
    }

    DirectBufferEx value(
        int index)
    {
        return fields.get(index).value;
    }

    @Override
    public Status transform(
        AvroController control,
        AvroSource source,
        AvroEvent event,
        AvroSink sink)
    {
        observe(source, event);
        return sink.transform(control, source, event);
    }

    @Override
    public void reset()
    {
        depth = 0;
        captured = 0;
        current = null;
    }

    @Override
    public boolean identity()
    {
        return true;
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
            current = depth == 1 ? supplyField(source.getField()) : null;
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
        DirectBufferEx segment = source.getSegment();
        int length = segment.capacity();
        current.value.putBytes(current.length, segment, 0, length);
        current.length += length;
        if (source.deferredBytes() == 0)
        {
            current = null;
        }
    }

    private Field supplyField(
        String name)
    {
        Field result = null;
        for (int i = 0; result == null && i < captured; i++)
        {
            if (fields.get(i).name.equals(name))
            {
                result = fields.get(i);
            }
        }
        if (result == null)
        {
            if (captured == fields.size())
            {
                fields.add(new Field());
            }
            result = fields.get(captured);
            result.name = name;
            captured++;
        }
        return result;
    }

    private static final class Field
    {
        private final MutableDirectBufferEx value;

        private String name;
        private int length;

        private Field()
        {
            this.value = new ExpandableDirectByteBufferEx();
        }
    }
}
