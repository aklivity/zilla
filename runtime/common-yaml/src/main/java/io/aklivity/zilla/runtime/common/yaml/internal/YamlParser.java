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
import java.util.ArrayDeque;
import java.util.Deque;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

public final class YamlParser implements JsonParser
{
    private final Deque<Frame> stack;
    private final YamlLocation end;
    private YamlEvent current;
    private YamlEvent next;

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
        this.stack = new ArrayDeque<>();
        stack.push(new Frame(node));
        this.end = new YamlLocation(1, 1, text.length());
    }

    @Override
    public boolean hasNext()
    {
        if (next == null)
        {
            next = nextEvent();
        }
        return next != null;
    }

    @Override
    public Event next()
    {
        if (!hasNext())
        {
            throw new JsonParsingException("No more events", getLocation());
        }
        current = next;
        next = null;
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

    private YamlEvent nextEvent()
    {
        while (!stack.isEmpty())
        {
            Frame frame = stack.peek();
            if (frame.node instanceof YamlObjectNode object)
            {
                if (!frame.started)
                {
                    frame.started = true;
                    return new YamlEvent(Event.START_OBJECT, null, object.line, object.column, object.offset);
                }
                if (frame.value)
                {
                    frame.value = false;
                    stack.push(new Frame(object.entries.get(frame.index++).value));
                    continue;
                }
                if (frame.index < object.entries.size())
                {
                    YamlEntry entry = object.entries.get(frame.index);
                    frame.value = true;
                    return new YamlEvent(Event.KEY_NAME, entry.name, entry.line, entry.column, entry.offset);
                }

                stack.pop();
                return new YamlEvent(Event.END_OBJECT, null, object.line, object.column, object.offset);
            }

            if (frame.node instanceof YamlArrayNode array)
            {
                if (!frame.started)
                {
                    frame.started = true;
                    return new YamlEvent(Event.START_ARRAY, null, array.line, array.column, array.offset);
                }
                if (frame.index < array.values.size())
                {
                    stack.push(new Frame(array.values.get(frame.index++)));
                    continue;
                }

                stack.pop();
                return new YamlEvent(Event.END_ARRAY, null, array.line, array.column, array.offset);
            }

            stack.pop();
            return scalarEvent((YamlScalarNode) frame.node);
        }

        return null;
    }

    private static YamlEvent scalarEvent(
        YamlScalarNode scalar)
    {
        Event event = switch (scalar.type)
        {
        case STRING -> Event.VALUE_STRING;
        case NUMBER -> Event.VALUE_NUMBER;
        case TRUE -> Event.VALUE_TRUE;
        case FALSE -> Event.VALUE_FALSE;
        case NULL -> Event.VALUE_NULL;
        };
        return new YamlEvent(event, scalar.value, scalar.line, scalar.column, scalar.offset);
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

    private static final class Frame
    {
        final YamlNode node;
        int index;
        boolean started;
        boolean value;

        private Frame(
            YamlNode node)
        {
            this.node = node;
        }
    }
}
