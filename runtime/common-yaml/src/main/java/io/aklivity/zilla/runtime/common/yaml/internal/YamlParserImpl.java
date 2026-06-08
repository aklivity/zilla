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
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;

import io.aklivity.zilla.runtime.common.yaml.YamlArray;
import io.aklivity.zilla.runtime.common.yaml.YamlEvent;
import io.aklivity.zilla.runtime.common.yaml.YamlEvent.EventType;
import io.aklivity.zilla.runtime.common.yaml.YamlObject;
import io.aklivity.zilla.runtime.common.yaml.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.YamlScalar;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

public final class YamlParserImpl implements YamlParser
{
    private final String text;
    private Deque<Frame> stack;
    private YamlNode root;
    private YamlEvent current;
    private YamlEvent next;
    private boolean parsed;
    private boolean streaming;
    private boolean exhausted;

    public YamlParserImpl(
        Reader reader)
    {
        this.text = readAll(reader);
    }

    public YamlParserImpl(
        InputStream in)
    {
        this(in, UTF_8);
    }

    public YamlParserImpl(
        InputStream in,
        Charset charset)
    {
        this.text = readAll(in, charset);
    }

    @Override
    public boolean hasNext()
    {
        if (parsed)
        {
            return false;
        }
        if (next == null && !exhausted)
        {
            streaming = true;
            ensureStack();
            next = nextEvent();
            exhausted = next == null;
        }
        return next != null;
    }

    @Override
    public YamlEvent next()
    {
        if (!hasNext())
        {
            throw new IllegalStateException("No more YAML events");
        }
        YamlEvent event = next;
        next = null;
        current = event;
        return event;
    }

    @Override
    public YamlValue parse()
    {
        if (parsed || streaming)
        {
            throw new IllegalStateException("YAML document has already been parsed");
        }
        parsed = true;
        return YamlValues.wrap(root());
    }

    @Override
    public YamlValue getValue()
    {
        return current != null ? current.getValue() : null;
    }

    @Override
    public YamlObject getObject()
    {
        return getValue().asYamlObject();
    }

    @Override
    public YamlArray getArray()
    {
        return getValue().asYamlArray();
    }

    @Override
    public YamlScalar getScalar()
    {
        return getValue().asYamlScalar();
    }

    @Override
    public String getString()
    {
        return current != null ? current.getString() : null;
    }

    @Override
    public void close()
    {
    }

    private YamlNode root()
    {
        if (root == null)
        {
            root = YamlDocumentParser.parse(text).node;
        }
        return root;
    }

    private void ensureStack()
    {
        if (stack == null)
        {
            stack = new ArrayDeque<>();
            stack.push(new Frame(root()));
        }
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
                    return new YamlEvent(EventType.START_OBJECT, null, YamlValues.wrap(object));
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
                    YamlValue key = entry.key != null ? YamlValues.wrap(entry.key) :
                        YamlValues.wrap(YamlScalarNode.string(entry.name, entry.line, entry.column, entry.offset));
                    return new YamlEvent(EventType.KEY_NAME, entry.name,
                        key);
                }

                stack.pop();
                return new YamlEvent(EventType.END_OBJECT, null, null);
            }

            if (frame.node instanceof YamlArrayNode array)
            {
                if (!frame.started)
                {
                    frame.started = true;
                    return new YamlEvent(EventType.START_ARRAY, null, YamlValues.wrap(array));
                }
                if (frame.index < array.values.size())
                {
                    stack.push(new Frame(array.values.get(frame.index++)));
                    continue;
                }

                stack.pop();
                return new YamlEvent(EventType.END_ARRAY, null, null);
            }

            stack.pop();
            return scalarEvent((YamlScalarNode) frame.node);
        }

        return null;
    }

    private YamlEvent scalarEvent(
        YamlScalarNode scalar)
    {
        EventType eventType = switch (scalar.type)
        {
        case STRING -> EventType.VALUE_STRING;
        case NUMBER -> EventType.VALUE_NUMBER;
        case TRUE -> EventType.VALUE_TRUE;
        case FALSE -> EventType.VALUE_FALSE;
        case NULL -> EventType.VALUE_NULL;
        };
        return new YamlEvent(eventType, scalar.value, YamlValues.wrap(scalar));
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
            throw new YamlParseException(ex.getMessage(), new YamlLocation(1, 1, 0));
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
            throw new YamlParseException(ex.getMessage(), new YamlLocation(1, 1, 0));
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
