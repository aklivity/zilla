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
package io.aklivity.zilla.runtime.common.protobuf.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;

/**
 * A terminal {@link ProtobufSink} that renders the decoded protobuf event stream as JSON through a
 * {@link JsonGeneratorEx}, applying the proto3 JSON mapping: a message is a JSON object keyed by each field's
 * proto3 json name, a {@code repeated} field a JSON array, a {@code map} a JSON object, 64-bit integers and
 * unsigned 64-bit integers JSON strings, {@code bytes} a base64 string, an {@code enum} its value name (its
 * number when unknown), and {@code float}/{@code double} JSON numbers ({@code "NaN"}/{@code "Infinity"}/
 * {@code "-Infinity"} as strings).
 * <p>
 * Bounded-buffer contract: the JSON is produced into the wrapped generator for one fully-buffered message;
 * the generator must be sized to hold the rendered document. {@link ProtobufPipeline.Status#COMPLETED} is
 * reported when the root message closes.
 */
public final class ProtobufJsonSink implements ProtobufSink
{
    private final JsonGeneratorEx generator;
    private final List<Scope> scopes;
    private final ExpandableArrayBuffer accumulator;

    private int depth;
    private int accumulated;
    private String valueKey;

    public ProtobufJsonSink(
        JsonGeneratorEx generator)
    {
        this.generator = generator;
        this.scopes = new ArrayList<>();
        this.accumulator = new ExpandableArrayBuffer();
        this.depth = -1;
    }

    @Override
    public ProtobufPipeline.Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
        switch (event)
        {
        case START_MESSAGE:
            onStartMessage(source);
            break;
        case START_GROUP:
            onStartGroup(source);
            break;
        case FIELD:
            onField(source);
            break;
        case VALUE:
            onValue(source);
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

    @Override
    public void reset()
    {
        depth = -1;
        accumulated = 0;
        valueKey = null;
        generator.reset();
    }

    private void onStartMessage(
        ProtobufSource source)
    {
        boolean root = depth < 0;
        Scope parent = root ? null : scope(depth);
        ProtobufMessage message = source.message();
        boolean mapEntry = !root && message != null && message.mapEntry();
        if (root)
        {
            generator.writeStartObject();
        }
        else if (parent.mapEntry)
        {
            // the value of a map entry whose value type is a message — emit its key, then its object
            generator.writeKey(parent.pendingKey);
            generator.writeStartObject();
        }
        else if (!mapEntry)
        {
            generator.writeStartObject();
        }
        depth++;
        scope(depth).reset(mapEntry);
    }

    private void onStartGroup(
        ProtobufSource source)
    {
        generator.writeStartObject();
        depth++;
        scope(depth).reset(false);
    }

    private void onField(
        ProtobufSource source)
    {
        Scope scope = scope(depth);
        ProtobufField field = source.field();
        if (scope.mapEntry)
        {
            scope.pendingNumber = field.number();
        }
        else
        {
            if (scope.openField != -1 && scope.openField != field.number())
            {
                closeContainer(scope);
            }
            if (field.repeated())
            {
                if (scope.openField != field.number())
                {
                    generator.writeKey(field.jsonName());
                    if (map(field))
                    {
                        generator.writeStartObject();
                    }
                    else
                    {
                        generator.writeStartArray();
                    }
                    scope.openField = field.number();
                }
            }
            else
            {
                generator.writeKey(field.jsonName());
            }
        }
    }

    private void onValue(
        ProtobufSource source)
    {
        Scope scope = scope(depth);
        ProtobufField field = source.field();
        if (scope.mapEntry && scope.pendingNumber == 1)
        {
            scope.pendingKey = scalarString(field, source);
        }
        else
        {
            String key = scope.mapEntry ? scope.pendingKey : null;
            writeValue(field, source, key);
        }
    }

    private ProtobufPipeline.Status onEndMessage()
    {
        Scope scope = scope(depth);
        if (scope.openField != -1)
        {
            closeContainer(scope);
        }
        ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
        if (depth == 0)
        {
            generator.writeEnd();
            status = ProtobufPipeline.Status.COMPLETED;
        }
        else if (!scope.mapEntry)
        {
            generator.writeEnd();
        }
        depth--;
        return status;
    }

    private void onEndGroup()
    {
        Scope scope = scope(depth);
        if (scope.openField != -1)
        {
            closeContainer(scope);
        }
        generator.writeEnd();
        depth--;
    }

    private void closeContainer(
        Scope scope)
    {
        generator.writeEnd();
        scope.openField = -1;
    }

    private void writeValue(
        ProtobufField field,
        ProtobufSource source,
        String key)
    {
        ProtobufType type = field.type();
        if (type == ProtobufType.STRING || type == ProtobufType.BYTES)
        {
            if (key != null)
            {
                valueKey = key;
            }
            DirectBuffer segment = source.segment();
            int length = segment.capacity();
            segment.getBytes(0, accumulator, accumulated, length);
            accumulated += length;
            if (source.deferredBytes() == 0)
            {
                byte[] bytes = new byte[accumulated];
                accumulator.getBytes(0, bytes);
                accumulated = 0;
                if (valueKey != null)
                {
                    generator.writeKey(valueKey);
                    valueKey = null;
                }
                if (type == ProtobufType.STRING)
                {
                    generator.write(new String(bytes, UTF_8));
                }
                else
                {
                    generator.write(Base64.getEncoder().encodeToString(bytes));
                }
            }
        }
        else
        {
            if (key != null)
            {
                generator.writeKey(key);
            }
            writeScalar(field, source);
        }
    }

    private void writeScalar(
        ProtobufField field,
        ProtobufSource source)
    {
        switch (field.type())
        {
        case INT32:
        case SINT32:
        case SFIXED32:
            generator.write((int) source.longValue());
            break;
        case UINT32:
        case FIXED32:
            generator.write(source.longValue() & 0xffffffffL);
            break;
        case INT64:
        case SINT64:
        case SFIXED64:
            generator.write(Long.toString(source.longValue()));
            break;
        case UINT64:
        case FIXED64:
            generator.write(Long.toUnsignedString(source.longValue()));
            break;
        case BOOL:
            generator.write(source.longValue() != 0L);
            break;
        case FLOAT:
            float f = source.floatValue();
            if (Float.isFinite(f))
            {
                generator.writeNumber(Float.toString(f));
            }
            else
            {
                generator.write(nonFinite(f));
            }
            break;
        case DOUBLE:
            double d = source.doubleValue();
            if (Double.isFinite(d))
            {
                generator.writeNumber(Double.toString(d));
            }
            else
            {
                generator.write(nonFinite(d));
            }
            break;
        case ENUM:
            int number = (int) source.longValue();
            ProtobufEnum enumeration = field.enumeration();
            String name = enumeration != null ? enumeration.name(number) : null;
            if (name != null)
            {
                generator.write(name);
            }
            else
            {
                generator.write(number);
            }
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private String scalarString(
        ProtobufField field,
        ProtobufSource source)
    {
        String value;
        switch (field.type())
        {
        case STRING:
            DirectBuffer segment = source.segment();
            byte[] bytes = new byte[segment.capacity()];
            segment.getBytes(0, bytes);
            value = new String(bytes, UTF_8);
            break;
        case BOOL:
            value = source.longValue() != 0L ? "true" : "false";
            break;
        case UINT64:
        case FIXED64:
            value = Long.toUnsignedString(source.longValue());
            break;
        case UINT32:
        case FIXED32:
            value = Long.toString(source.longValue() & 0xffffffffL);
            break;
        default:
            value = Long.toString(source.longValue());
            break;
        }
        return value;
    }

    private static boolean map(
        ProtobufField field)
    {
        ProtobufMessage message = field.message();
        return message != null && message.mapEntry();
    }

    private static String nonFinite(
        double value)
    {
        String text;
        if (Double.isNaN(value))
        {
            text = "NaN";
        }
        else
        {
            text = value > 0 ? "Infinity" : "-Infinity";
        }
        return text;
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

    private static final class Scope
    {
        private boolean mapEntry;
        private int openField;
        private int pendingNumber;
        private String pendingKey;

        private void reset(
            boolean mapEntry)
        {
            this.mapEntry = mapEntry;
            this.openField = -1;
            this.pendingNumber = 0;
            this.pendingKey = null;
        }
    }
}
