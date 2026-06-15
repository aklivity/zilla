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

import jakarta.json.stream.JsonParser.Event;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A {@link ProtobufParser} that reads JSON through a {@link JsonParserEx} and presents it as the descriptor-
 * bound protobuf event cursor, applying the proto3 JSON mapping in reverse — json/proto field names to field
 * numbers, 64-bit integer strings and base64 {@code bytes} strings to their wire values, enum names to
 * numbers, JSON arrays to {@code repeated} fields, and JSON objects to messages or {@code map}s. Pumped by
 * {@link io.aklivity.zilla.runtime.common.protobuf.Protobuf#stream(ProtobufParser)} into a wire
 * {@link io.aklivity.zilla.runtime.common.protobuf.ProtobufSink}, it encodes the JSON as protobuf.
 * <p>
 * Bounded-buffer contract: the JSON document is parsed whole on {@link #wrap}, expanded into the protobuf
 * event sequence bounded by the message structure, then pulled event by event; no unbounded document is
 * retained beyond the expanded message.
 */
public final class JsonProtobufParser implements ProtobufParser
{
    private final JsonParserEx parser;
    private final ProtobufSchema schema;
    private final String messageName;
    private final List<Token> tokens;
    private final UnsafeBuffer estimateView;
    private final UnsafeBuffer valueView;

    private int index;
    private int committed;
    private Token current;

    public JsonProtobufParser(
        JsonParserEx parser,
        ProtobufSchema schema,
        String messageName)
    {
        this.parser = parser;
        this.schema = schema;
        this.messageName = messageName;
        this.tokens = new ArrayList<>();
        this.estimateView = new UnsafeBuffer();
        this.valueView = new UnsafeBuffer();
    }

    @Override
    public ProtobufParser wrap(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last)
    {
        tokens.clear();
        index = 0;
        current = null;
        committed = length;
        // the segment capacity reported at a nested START_MESSAGE is the optimistic encoded-length bound the
        // wire sink reserves; the JSON byte length is a safe upper bound on any subtree's encoded size
        estimateView.wrap(buffer, offset, length);
        ProtobufMessage root = schema.message(messageName);
        if (root == null)
        {
            throw new ProtobufException("unknown message " + messageName);
        }
        try
        {
            parser.wrap(buffer, offset, length);
            Event first = parser.next();
            if (first != Event.START_OBJECT)
            {
                throw new ProtobufException("expected json object");
            }
            expandObject(root, false);
        }
        catch (ProtobufException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new ProtobufException("invalid json", ex);
        }
        return this;
    }

    @Override
    public ProtobufParser resume(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last)
    {
        return wrap(buffer, offset, length, last);
    }

    @Override
    public boolean hasNext()
    {
        return index < tokens.size();
    }

    @Override
    public long position()
    {
        return committed;
    }

    @Override
    public ProtobufEvent nextEvent(
        Mode mode)
    {
        current = tokens.get(index);
        index++;
        return current.event;
    }

    @Override
    public ProtobufField field()
    {
        return current != null ? current.field : null;
    }

    @Override
    public ProtobufMessage message()
    {
        return current != null ? current.message : null;
    }

    @Override
    public int fieldNumber()
    {
        return current != null && current.field != null ? current.field.number() : -1;
    }

    @Override
    public ProtobufWireType wireType()
    {
        return current != null && current.field != null ? current.field.type().wireType() : null;
    }

    @Override
    public long longValue()
    {
        return current.longValue;
    }

    @Override
    public double doubleValue()
    {
        return current.doubleValue;
    }

    @Override
    public float floatValue()
    {
        return current.floatValue;
    }

    @Override
    public DirectBuffer segment()
    {
        DirectBuffer segment;
        if (current.event == ProtobufEvent.START_MESSAGE || current.event == ProtobufEvent.START_GROUP)
        {
            segment = estimateView;
        }
        else
        {
            valueView.wrap(current.bytes);
            segment = valueView;
        }
        return segment;
    }

    @Override
    public int deferredBytes()
    {
        return 0;
    }

    private void expandObject(
        ProtobufMessage message,
        boolean group)
    {
        emit(group ? ProtobufEvent.START_GROUP : ProtobufEvent.START_MESSAGE).message = message;
        Event event;
        while ((event = parser.next()) == Event.KEY_NAME)
        {
            String key = parser.getString();
            ProtobufField field = message.field(key);
            Event value = parser.next();
            if (field == null)
            {
                skip(value);
            }
            else
            {
                expandField(field, value);
            }
        }
        emit(group ? ProtobufEvent.END_GROUP : ProtobufEvent.END_MESSAGE);
    }

    private void expandField(
        ProtobufField field,
        Event value)
    {
        if (map(field))
        {
            expandMap(field);
        }
        else if (field.repeated())
        {
            Event element;
            while ((element = parser.next()) != Event.END_ARRAY)
            {
                expandElement(field, element);
            }
        }
        else
        {
            expandSingle(field, value);
        }
    }

    private void expandSingle(
        ProtobufField field,
        Event value)
    {
        if (value != Event.VALUE_NULL)
        {
            if (field.composite())
            {
                emit(ProtobufEvent.FIELD).field = field;
                expandObject(field.message(), field.type() == ProtobufType.GROUP);
            }
            else
            {
                emit(ProtobufEvent.FIELD).field = field;
                emitScalar(field, value);
            }
        }
    }

    private void expandElement(
        ProtobufField field,
        Event element)
    {
        if (field.composite())
        {
            emit(ProtobufEvent.FIELD).field = field;
            expandObject(field.message(), field.type() == ProtobufType.GROUP);
        }
        else if (element != Event.VALUE_NULL)
        {
            emit(ProtobufEvent.FIELD).field = field;
            emitScalar(field, element);
        }
    }

    private void expandMap(
        ProtobufField field)
    {
        ProtobufMessage entry = field.message();
        ProtobufField keyField = entry.field(1);
        ProtobufField valueField = entry.field(2);
        Event event;
        while ((event = parser.next()) == Event.KEY_NAME)
        {
            String key = parser.getString();
            Event value = parser.next();
            emit(ProtobufEvent.FIELD).field = field;
            emit(ProtobufEvent.START_MESSAGE).message = entry;
            emit(ProtobufEvent.FIELD).field = keyField;
            emitScalarText(keyField, key);
            if (valueField.composite())
            {
                emit(ProtobufEvent.FIELD).field = valueField;
                expandObject(valueField.message(), valueField.type() == ProtobufType.GROUP);
            }
            else if (value != Event.VALUE_NULL)
            {
                emit(ProtobufEvent.FIELD).field = valueField;
                emitScalar(valueField, value);
            }
            emit(ProtobufEvent.END_MESSAGE);
        }
    }

    private void emitScalar(
        ProtobufField field,
        Event event)
    {
        Token token = emit(ProtobufEvent.VALUE);
        token.field = field;
        switch (field.type())
        {
        case INT32:
        case SINT32:
        case SFIXED32:
        case INT64:
        case SINT64:
        case SFIXED64:
            token.longValue = event == Event.VALUE_STRING ? Long.parseLong(parser.getString()) : parser.getLong();
            break;
        case UINT32:
        case FIXED32:
            token.longValue = (event == Event.VALUE_STRING ? Long.parseLong(parser.getString()) : parser.getLong())
                & 0xffffffffL;
            break;
        case UINT64:
        case FIXED64:
            token.longValue = event == Event.VALUE_STRING
                ? Long.parseUnsignedLong(parser.getString())
                : parser.getLong();
            break;
        case BOOL:
            token.longValue = event == Event.VALUE_TRUE ? 1L : 0L;
            break;
        case FLOAT:
            token.floatValue = event == Event.VALUE_STRING
                ? Float.parseFloat(parser.getString())
                : parser.getBigDecimal().floatValue();
            break;
        case DOUBLE:
            token.doubleValue = event == Event.VALUE_STRING
                ? Double.parseDouble(parser.getString())
                : parser.getBigDecimal().doubleValue();
            break;
        case ENUM:
            token.longValue = event == Event.VALUE_STRING ? enumNumber(field, parser.getString()) : parser.getInt();
            break;
        case STRING:
            token.bytes = parser.getString().getBytes(UTF_8);
            break;
        case BYTES:
            token.bytes = base64(parser.getString());
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private void emitScalarText(
        ProtobufField field,
        String text)
    {
        Token token = emit(ProtobufEvent.VALUE);
        token.field = field;
        switch (field.type())
        {
        case STRING:
            token.bytes = text.getBytes(UTF_8);
            break;
        case BOOL:
            token.longValue = "true".equals(text) ? 1L : 0L;
            break;
        case UINT32:
        case FIXED32:
            token.longValue = Long.parseLong(text) & 0xffffffffL;
            break;
        case UINT64:
        case FIXED64:
            token.longValue = Long.parseUnsignedLong(text);
            break;
        default:
            token.longValue = Long.parseLong(text);
            break;
        }
    }

    private int enumNumber(
        ProtobufField field,
        String name)
    {
        Integer number = field.enumeration() != null ? field.enumeration().number(name) : null;
        if (number == null)
        {
            throw new ProtobufException("unknown enum value " + name);
        }
        return number;
    }

    private void skip(
        Event value)
    {
        if (value == Event.START_OBJECT || value == Event.START_ARRAY)
        {
            int depth = 1;
            while (depth > 0)
            {
                Event event = parser.next();
                if (event == Event.START_OBJECT || event == Event.START_ARRAY)
                {
                    depth++;
                }
                else if (event == Event.END_OBJECT || event == Event.END_ARRAY)
                {
                    depth--;
                }
            }
        }
    }

    private Token emit(
        ProtobufEvent event)
    {
        Token token = new Token();
        token.event = event;
        tokens.add(token);
        return token;
    }

    private static boolean map(
        ProtobufField field)
    {
        ProtobufMessage message = field.message();
        return field.repeated() && message != null && message.mapEntry();
    }

    private static byte[] base64(
        String value)
    {
        byte[] bytes;
        try
        {
            bytes = Base64.getDecoder().decode(value);
        }
        catch (IllegalArgumentException ex)
        {
            bytes = Base64.getUrlDecoder().decode(value);
        }
        return bytes;
    }

    private static final class Token
    {
        private ProtobufEvent event;
        private ProtobufField field;
        private ProtobufMessage message;
        private long longValue;
        private double doubleValue;
        private float floatValue;
        private byte[] bytes;
    }
}
