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
package io.aklivity.zilla.runtime.common.yaml.internal.json;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.yaml.YamlConfig;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlArrayNode;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlDocumentParser;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlEntry;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlLocation;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlNode;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlObjectNode;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlParseException;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlScalarNode;

public final class YamlJsonParser implements JsonParser
{
    private final Deque<Frame> stack;
    private final String text;
    private final Map<String, ?> config;
    private final boolean uniqueKeys;
    private YamlJsonLocation end;
    private long documentOffset;
    private YamlJsonEvent current;
    private YamlJsonEvent next;
    private boolean exhausted;

    public YamlJsonParser(
        Reader reader)
    {
        this(readAll(reader), Map.of());
    }

    YamlJsonParser(
        Reader reader,
        Map<String, ?> config)
    {
        this(readAll(reader), config);
    }

    public YamlJsonParser(
        InputStream in)
    {
        this(in, UTF_8);
    }

    public YamlJsonParser(
        InputStream in,
        Charset charset)
    {
        this(readAll(in, charset), Map.of());
    }

    YamlJsonParser(
        InputStream in,
        Charset charset,
        Map<String, ?> config)
    {
        this(readAll(in, charset), config);
    }

    private YamlJsonParser(
        String text,
        Map<String, ?> config)
    {
        this.stack = new ArrayDeque<>();
        this.text = text;
        this.config = jsonAsYamlConfig(config);
        this.uniqueKeys = Boolean.TRUE.equals(this.config.get(YamlConfig.FEATURE_UNIQUE_KEYS));
        parseDocument(0);
    }

    private void parseDocument(
        long offset)
    {
        try
        {
            YamlDocumentParser.Result result = YamlDocumentParser.parse(text.substring((int) offset), config);
            rejectJsonUnsupported(result.node, offset);
            if (uniqueKeys)
            {
                rejectDuplicateKeys(result.node, offset);
            }
            this.documentOffset = offset;
            this.end = location(result.end, offset);
            stack.push(new Frame(result.node));
        }
        catch (YamlParseException ex)
        {
            throw new JsonParsingException(ex.getMessage(), location(ex.location(), offset));
        }
    }

    private static Map<String, ?> jsonAsYamlConfig(
        Map<String, ?> config)
    {
        Map<String, Object> effective = new HashMap<>();
        if (config != null)
        {
            effective.putAll(config);
        }
        effective.put(YamlConfig.FEATURE_NON_SCALAR_KEYS, false);
        return Map.copyOf(effective);
    }

    @Override
    public boolean hasNext()
    {
        if (next == null && !exhausted)
        {
            next = nextEvent();
            exhausted = next == null;
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
    public Event currentEvent()
    {
        if (current == null)
        {
            throw new IllegalStateException("No current event");
        }
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
        return exhausted ? end : current != null ? current.location : end;
    }

    @Override
    public void close()
    {
    }

    @Override
    public JsonObject getObject()
    {
        JsonValue value = getValue();
        if (value instanceof JsonObject object)
        {
            return object;
        }
        throw new IllegalStateException("Current YAML value is not an object");
    }

    @Override
    public JsonValue getValue()
    {
        if (current == null || current.node == null)
        {
            throw new IllegalStateException("No value is available for current event");
        }
        return toJsonValue(current.node);
    }

    @Override
    public JsonArray getArray()
    {
        JsonValue value = getValue();
        if (value instanceof JsonArray array)
        {
            return array;
        }
        throw new IllegalStateException("Current YAML value is not an array");
    }

    @Override
    public java.util.stream.Stream<JsonValue> getArrayStream()
    {
        return getArray().stream();
    }

    @Override
    public java.util.stream.Stream<java.util.Map.Entry<String, JsonValue>> getObjectStream()
    {
        return getObject().entrySet().stream();
    }

    @Override
    public java.util.stream.Stream<JsonValue> getValueStream()
    {
        return java.util.stream.Stream.of(getValue());
    }

    @Override
    public void skipObject()
    {
        skip(Event.START_OBJECT);
    }

    @Override
    public void skipArray()
    {
        skip(Event.START_ARRAY);
    }

    private void skip(
        Event expected)
    {
        if (current == null || current.event != expected)
        {
            throw new IllegalStateException("Parser is not positioned on " + expected);
        }

        int depth = 1;
        while (depth != 0)
        {
            Event event = next();
            switch (event)
            {
            case START_OBJECT, START_ARRAY -> depth++;
            case END_OBJECT, END_ARRAY -> depth--;
            default ->
            {
            }
            }
        }
    }

    private String numberValue()
    {
        if (current == null || current.event != Event.VALUE_NUMBER)
        {
            throw new IllegalStateException("Not a number");
        }
        return current.value;
    }

    private YamlJsonEvent nextEvent()
    {
        while (!stack.isEmpty())
        {
            Frame frame = stack.peek();
            if (frame.node instanceof YamlObjectNode object)
            {
                if (!frame.started)
                {
                    frame.started = true;
                    return event(Event.START_OBJECT, null, object, object.line, object.column, object.offset);
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
                    String name = jsonKeyName(entry);
                    frame.value = true;
                    return event(Event.KEY_NAME, name,
                        YamlScalarNode.string(name, entry.line, entry.column, entry.offset),
                        entry.line, entry.column, entry.offset);
                }

                boolean root = stack.size() == 1;
                stack.pop();
                return root ? eventAtEnd(Event.END_OBJECT, null, object) :
                    event(Event.END_OBJECT, null, object, object.line, object.column, object.offset);
            }

            if (frame.node instanceof YamlArrayNode array)
            {
                if (!frame.started)
                {
                    frame.started = true;
                    return event(Event.START_ARRAY, null, array, array.line, array.column, array.offset);
                }
                if (frame.index < array.values.size())
                {
                    stack.push(new Frame(array.values.get(frame.index++)));
                    continue;
                }

                boolean root = stack.size() == 1;
                stack.pop();
                return root ? eventAtEnd(Event.END_ARRAY, null, array) :
                    event(Event.END_ARRAY, null, array, array.line, array.column, array.offset);
            }

            boolean root = stack.size() == 1;
            stack.pop();
            return scalarEvent((YamlScalarNode) frame.node, root);
        }

        long offset = end.getStreamOffset();
        if (offset < text.length() && hasDocumentContent(text, (int) offset))
        {
            parseDocument(offset);
            return nextEvent();
        }

        return null;
    }

    private YamlJsonEvent scalarEvent(
        YamlScalarNode scalar,
        boolean root)
    {
        Event event = switch (scalar.type)
        {
        case STRING -> Event.VALUE_STRING;
        case NUMBER -> Event.VALUE_NUMBER;
        case TRUE -> Event.VALUE_TRUE;
        case FALSE -> Event.VALUE_FALSE;
        case NULL -> Event.VALUE_NULL;
        };
        return root ? eventAtEnd(event, scalar.value, scalar) :
            event(event, scalar.value, scalar, scalar.line, scalar.column, scalar.offset);
    }

    private JsonValue toJsonValue(
        YamlNode node)
    {
        if (node instanceof YamlObjectNode object)
        {
            JsonObjectBuilder builder = YamlJsonValues.objectBuilder();
            for (YamlEntry entry : object.entries)
            {
                builder.add(jsonKeyName(entry), toJsonValue(entry.value));
            }
            return builder.build();
        }
        if (node instanceof YamlArrayNode array)
        {
            JsonArrayBuilder builder = YamlJsonValues.arrayBuilder();
            for (YamlNode value : array.values)
            {
                builder.add(toJsonValue(value));
            }
            return builder.build();
        }

        YamlScalarNode scalar = (YamlScalarNode) node;
        return switch (scalar.type)
        {
        case STRING -> YamlJsonValues.string(scalar.value);
        case NUMBER -> YamlJsonValues.number(new BigDecimal(scalar.value));
        case TRUE -> JsonValue.TRUE;
        case FALSE -> JsonValue.FALSE;
        case NULL -> JsonValue.NULL;
        };
    }

    private static void rejectJsonUnsupported(
        YamlNode node,
        long offset)
    {
        if (node instanceof YamlObjectNode object)
        {
            for (YamlEntry entry : object.entries)
            {
                if (entry.key != null)
                {
                    if (!(entry.key instanceof YamlScalarNode))
                    {
                        throw new JsonParsingException("Non-scalar YAML mapping keys are not supported",
                            new YamlJsonLocation(new YamlLocation(entry.line, entry.column, offset + entry.offset)));
                    }
                }
                rejectJsonUnsupported(entry.value, offset);
            }
        }
        else if (node instanceof YamlArrayNode array)
        {
            for (YamlNode value : array.values)
            {
                rejectJsonUnsupported(value, offset);
            }
        }
    }

    private static void rejectDuplicateKeys(
        YamlNode node,
        long offset)
    {
        if (node instanceof YamlObjectNode object)
        {
            Set<String> names = new HashSet<>();
            for (YamlEntry entry : object.entries)
            {
                String name = jsonKeyName(entry);
                if (!names.add(name))
                {
                    throw new JsonParsingException("Duplicate YAML mapping key: " + name,
                        new YamlJsonLocation(new YamlLocation(entry.line, entry.column, offset + entry.offset)));
                }
                rejectDuplicateKeys(entry.value, offset);
            }
        }
        else if (node instanceof YamlArrayNode array)
        {
            for (YamlNode value : array.values)
            {
                rejectDuplicateKeys(value, offset);
            }
        }
    }

    private static String jsonKeyName(
        YamlEntry entry)
    {
        if (entry.name != null)
        {
            return entry.name;
        }
        if (entry.key instanceof YamlScalarNode scalar)
        {
            return scalarText(scalar);
        }
        throw new JsonParsingException("Non-scalar YAML mapping keys are not supported",
            new YamlJsonLocation(new YamlLocation(entry.line, entry.column, entry.offset)));
    }

    private YamlJsonEvent event(
        Event event,
        String value,
        YamlNode node,
        int line,
        int column,
        long offset)
    {
        return new YamlJsonEvent(event, value, node, line, column, documentOffset + offset);
    }

    private YamlJsonEvent eventAtEnd(
        Event event,
        String value,
        YamlNode node)
    {
        return new YamlJsonEvent(event, value, node, end);
    }

    private static YamlJsonLocation location(
        YamlLocation location,
        long offset)
    {
        return new YamlJsonLocation(new YamlLocation((int) location.line(), (int) location.column(),
            offset + location.offset()));
    }

    private static boolean hasDocumentContent(
        String text,
        int offset)
    {
        while (offset < text.length())
        {
            int lineEnd = text.indexOf('\n', offset);
            if (lineEnd == -1)
            {
                lineEnd = text.length();
            }
            String raw = text.substring(offset, lineEnd);
            if (raw.endsWith("\r"))
            {
                raw = raw.substring(0, raw.length() - 1);
            }
            String content = raw.stripLeading();
            int commentAt = content.indexOf('#');
            if (commentAt == 0)
            {
                content = "";
            }
            content = content.strip();
            if (!content.isEmpty() && !"...".equals(content))
            {
                return true;
            }
            offset = lineEnd == text.length() ? lineEnd : lineEnd + 1;
        }
        return false;
    }

    private static String scalarText(
        YamlScalarNode scalar)
    {
        return scalar.value != null ? scalar.value : switch (scalar.type)
        {
        case TRUE -> "true";
        case FALSE -> "false";
        case NULL -> "null";
        default -> "";
        };
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
                new YamlJsonLocation(new YamlLocation(1, 1, 0)));
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
                new YamlJsonLocation(new YamlLocation(1, 1, 0)));
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
