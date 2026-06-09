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
package io.aklivity.zilla.runtime.common.json.internal;

import static jakarta.json.stream.JsonParser.Event.END_ARRAY;
import static jakarta.json.stream.JsonParser.Event.END_OBJECT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.StreamingJson;

/**
 * A minimal, provider-free JSON tree parsed with {@link StreamingJson} so that {@code
 * common-json} can read a schema document using its own parser, without depending on a
 * {@code jakarta.json} provider. Used at schema-compile time only.
 */
final class JsonNode
{
    enum Kind
    {
        OBJECT, ARRAY, STRING, NUMBER, TRUE, FALSE, NULL
    }

    private final Kind kind;
    private final Map<String, JsonNode> members;
    private final List<JsonNode> elements;
    private final String text;

    static JsonNode parse(
        String json)
    {
        byte[] bytes = (json + " ").getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        JsonParser parser = StreamingJson.createParser(in);
        return read(parser, parser.next());
    }

    Kind kind()
    {
        return kind;
    }

    boolean isObject()
    {
        return kind == Kind.OBJECT;
    }

    boolean isArray()
    {
        return kind == Kind.ARRAY;
    }

    boolean isString()
    {
        return kind == Kind.STRING;
    }

    boolean isTrue()
    {
        return kind == Kind.TRUE;
    }

    boolean isFalse()
    {
        return kind == Kind.FALSE;
    }

    boolean isStructural()
    {
        return kind == Kind.OBJECT || kind == Kind.ARRAY;
    }

    boolean has(
        String key)
    {
        return members != null && members.containsKey(key);
    }

    JsonNode get(
        String key)
    {
        return members != null ? members.get(key) : null;
    }

    Map<String, JsonNode> members()
    {
        return members;
    }

    List<JsonNode> elements()
    {
        return elements;
    }

    String string()
    {
        return text;
    }

    BigDecimal number()
    {
        return new BigDecimal(text);
    }

    int integer()
    {
        return new BigDecimal(text).intValueExact();
    }

    private JsonNode(
        Kind kind,
        Map<String, JsonNode> members,
        List<JsonNode> elements,
        String text)
    {
        this.kind = kind;
        this.members = members;
        this.elements = elements;
        this.text = text;
    }

    private static JsonNode read(
        JsonParser parser,
        Event event)
    {
        JsonNode node;
        switch (event)
        {
        case START_OBJECT:
        {
            Map<String, JsonNode> members = new LinkedHashMap<>();
            Event next = parser.next();
            while (next != END_OBJECT)
            {
                String key = parser.getString();
                members.put(key, read(parser, parser.next()));
                next = parser.next();
            }
            node = new JsonNode(Kind.OBJECT, members, null, null);
            break;
        }
        case START_ARRAY:
        {
            List<JsonNode> elements = new ArrayList<>();
            Event next = parser.next();
            while (next != END_ARRAY)
            {
                elements.add(read(parser, next));
                next = parser.next();
            }
            node = new JsonNode(Kind.ARRAY, null, elements, null);
            break;
        }
        case VALUE_STRING:
            node = new JsonNode(Kind.STRING, null, null, parser.getString());
            break;
        case VALUE_NUMBER:
            node = new JsonNode(Kind.NUMBER, null, null, parser.getString());
            break;
        case VALUE_TRUE:
            node = new JsonNode(Kind.TRUE, null, null, null);
            break;
        case VALUE_FALSE:
            node = new JsonNode(Kind.FALSE, null, null, null);
            break;
        default:
            node = new JsonNode(Kind.NULL, null, null, null);
            break;
        }
        return node;
    }
}
