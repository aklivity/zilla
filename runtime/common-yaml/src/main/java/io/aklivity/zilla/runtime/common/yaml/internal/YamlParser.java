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
package io.aklivity.zilla.runtime.common.yaml.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

public final class YamlParser implements JsonParser
{
    private final List<YamlEvent> events;
    private final YamlLocation end;
    private int cursor;
    private YamlEvent current;

    public YamlParser(
        Reader reader)
    {
        this(readAll(reader));
    }

    public YamlParser(
        InputStream in)
    {
        this(in, UTF_8);
    }

    public YamlParser(
        InputStream in,
        Charset charset)
    {
        this(readAll(in, charset));
    }

    private YamlParser(
        String text)
    {
        YamlNode node = YamlDocumentParser.parse(text);
        this.events = new ArrayList<>();
        flatten(node, events);
        this.end = new YamlLocation(1, 1, text.length());
        this.cursor = -1;
    }

    @Override
    public boolean hasNext()
    {
        return cursor + 1 < events.size();
    }

    @Override
    public Event next()
    {
        if (!hasNext())
        {
            throw new JsonParsingException("No more events", getLocation());
        }
        current = events.get(++cursor);
        return current.event;
    }

    @Override
    public String getString()
    {
        if (current == null || current.value == null)
        {
            throw new IllegalStateException("No string value is available for current event");
        }
        return current.value;
    }

    @Override
    public boolean isIntegralNumber()
    {
        String value = numberValue();
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if (c == '.' || c == 'e' || c == 'E')
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getInt()
    {
        return Integer.parseInt(numberValue());
    }

    @Override
    public long getLong()
    {
        return Long.parseLong(numberValue());
    }

    @Override
    public BigDecimal getBigDecimal()
    {
        return new BigDecimal(numberValue());
    }

    @Override
    public JsonLocation getLocation()
    {
        return current != null ? current.location : end;
    }

    @Override
    public void close()
    {
    }

    @Override
    public JsonObject getObject()
    {
        throw new UnsupportedOperationException("getObject not yet supported");
    }

    @Override
    public JsonValue getValue()
    {
        throw new UnsupportedOperationException("getValue not yet supported");
    }

    @Override
    public JsonArray getArray()
    {
        throw new UnsupportedOperationException("getArray not yet supported");
    }

    @Override
    public java.util.stream.Stream<JsonValue> getArrayStream()
    {
        throw new UnsupportedOperationException("getArrayStream not yet supported");
    }

    @Override
    public java.util.stream.Stream<java.util.Map.Entry<String, JsonValue>> getObjectStream()
    {
        throw new UnsupportedOperationException("getObjectStream not yet supported");
    }

    @Override
    public java.util.stream.Stream<JsonValue> getValueStream()
    {
        throw new UnsupportedOperationException("getValueStream not yet supported");
    }

    @Override
    public void skipObject()
    {
        throw new UnsupportedOperationException("skipObject not yet supported; use event-by-event consumption instead");
    }

    @Override
    public void skipArray()
    {
        throw new UnsupportedOperationException("skipArray not yet supported; use event-by-event consumption instead");
    }

    private String numberValue()
    {
        if (current == null || current.event != Event.VALUE_NUMBER)
        {
            throw new IllegalStateException("Not a number");
        }
        return current.value;
    }

    private static void flatten(
        YamlNode node,
        List<YamlEvent> events)
    {
        if (node instanceof YamlObjectNode object)
        {
            events.add(new YamlEvent(Event.START_OBJECT, null, object.line, object.column, object.offset));
            for (YamlEntry entry : object.entries)
            {
                events.add(new YamlEvent(Event.KEY_NAME, entry.name, entry.line, entry.column, entry.offset));
                flatten(entry.value, events);
            }
            events.add(new YamlEvent(Event.END_OBJECT, null, object.line, object.column, object.offset));
        }
        else if (node instanceof YamlArrayNode array)
        {
            events.add(new YamlEvent(Event.START_ARRAY, null, array.line, array.column, array.offset));
            for (YamlNode value : array.values)
            {
                flatten(value, events);
            }
            events.add(new YamlEvent(Event.END_ARRAY, null, array.line, array.column, array.offset));
        }
        else
        {
            flattenScalar((YamlScalarNode) node, events);
        }
    }

    private static void flattenScalar(
        YamlScalarNode scalar,
        List<YamlEvent> events)
    {
        Event event = switch (scalar.type)
        {
        case STRING -> Event.VALUE_STRING;
        case NUMBER -> Event.VALUE_NUMBER;
        case TRUE -> Event.VALUE_TRUE;
        case FALSE -> Event.VALUE_FALSE;
        case NULL -> Event.VALUE_NULL;
        };
        events.add(new YamlEvent(event, scalar.value, scalar.line, scalar.column, scalar.offset));
    }

    private static String readAll(
        Reader reader)
    {
        try
        {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1)
            {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
        catch (IOException ex)
        {
            throw new JsonParsingException(ex.getMessage(), ex,
                new YamlLocation(1, 1, 0));
        }
    }

    private static String readAll(
        InputStream in,
        Charset charset)
    {
        try
        {
            return new String(in.readAllBytes(), charset);
        }
        catch (IOException ex)
        {
            throw new JsonParsingException(ex.getMessage(), ex,
                new YamlLocation(1, 1, 0));
        }
    }
}
