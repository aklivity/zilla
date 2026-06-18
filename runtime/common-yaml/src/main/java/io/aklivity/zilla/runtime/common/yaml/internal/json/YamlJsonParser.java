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
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.yaml.YamlConfig;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlEvent;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlLocation;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlParseException;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlScalarType;

/**
 * A {@link JsonParser} over a YAML 1.2 stream restricted to the YAML JSON Schema. It is layered on the
 * {@link YamlParser} event stream: the YAML events are projected to JSON parser events, aliases are resolved
 * against their anchored values, JSON Schema tags ({@code !!str}, {@code !!int}, {@code !!float},
 * {@code !!bool}, {@code !!null}) are coerced, and constructs JSON cannot represent (non-scalar or duplicate
 * mapping keys) are rejected. The underlying parser remains full YAML 1.2; the JSON restriction lives here.
 */
public final class YamlJsonParser implements JsonParser
{
    private static final String STR_TAG = "tag:yaml.org,2002:str";
    private static final String INT_TAG = "tag:yaml.org,2002:int";
    private static final String FLOAT_TAG = "tag:yaml.org,2002:float";
    private static final String BOOL_TAG = "tag:yaml.org,2002:bool";
    private static final String NULL_TAG = "tag:yaml.org,2002:null";
    private static final String NON_SPECIFIC_TAG = "!";

    private final boolean uniqueKeys;
    private final List<Yev> events;
    private final Map<String, NavigableMap<Long, YamlAlias>> anchors;
    private final List<Step> steps;
    private YamlJsonLocation end;
    private int cursor;
    private int current;

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
        this.uniqueKeys = config != null && Boolean.TRUE.equals(config.get(YamlConfig.FEATURE_UNIQUE_KEYS));
        this.events = new ArrayList<>();
        this.anchors = new HashMap<>();
        this.steps = new ArrayList<>();
        this.current = -1;
        try
        {
            capture(new YamlParser(text));
            indexAnchors();
            project();
        }
        catch (YamlParseException ex)
        {
            throw new JsonParsingException(ex.getMessage(),
                new YamlJsonLocation(ex.location() != null ? ex.location() : new YamlLocation(1, 1, 0)));
        }
        if (end == null)
        {
            end = new YamlJsonLocation(new YamlLocation(1, 1, 0));
        }
    }

    @Override
    public boolean hasNext()
    {
        return cursor < steps.size();
    }

    @Override
    public Event next()
    {
        if (!hasNext())
        {
            throw new JsonParsingException("No more events", getLocation());
        }
        current = cursor++;
        return steps.get(current).event;
    }

    @Override
    public Event currentEvent()
    {
        if (current < 0)
        {
            throw new IllegalStateException("No current event");
        }
        return steps.get(current).event;
    }

    @Override
    public String getString()
    {
        if (current < 0 || steps.get(current).value == null)
        {
            throw new IllegalStateException("No string value is available for current event");
        }
        return steps.get(current).value;
    }

    @Override
    public boolean isIntegralNumber()
    {
        String value = numberValue();
        boolean integral = true;
        for (int i = 0; i < value.length() && integral; i++)
        {
            char c = value.charAt(i);
            integral = c != '.' && c != 'e' && c != 'E';
        }
        return integral;
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
        return current < 0 ? new YamlJsonLocation(new YamlLocation(1, 1, 0)) : steps.get(current).location;
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
        if (current < 0)
        {
            throw new IllegalStateException("No value is available for current event");
        }
        return value(new int[] {current});
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
    public java.util.stream.Stream<Map.Entry<String, JsonValue>> getObjectStream()
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
        if (current < 0 || steps.get(current).event != expected)
        {
            throw new IllegalStateException("Parser is not positioned on " + expected);
        }
        int depth = 1;
        while (depth != 0)
        {
            switch (next())
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
        if (current < 0 || steps.get(current).event != Event.VALUE_NUMBER)
        {
            throw new IllegalStateException("Not a number");
        }
        return steps.get(current).value;
    }

    private JsonValue value(
        int[] at)
    {
        Step step = steps.get(at[0]);
        JsonValue value;
        switch (step.event)
        {
        case START_OBJECT ->
        {
            at[0]++;
            JsonObjectBuilder builder = YamlJsonValues.objectBuilder();
            while (steps.get(at[0]).event != Event.END_OBJECT)
            {
                String name = steps.get(at[0]).value;
                at[0]++;
                builder.add(name, value(at));
            }
            at[0]++;
            value = builder.build();
        }
        case START_ARRAY ->
        {
            at[0]++;
            JsonArrayBuilder builder = YamlJsonValues.arrayBuilder();
            while (steps.get(at[0]).event != Event.END_ARRAY)
            {
                builder.add(value(at));
            }
            at[0]++;
            value = builder.build();
        }
        case VALUE_STRING, KEY_NAME ->
        {
            value = YamlJsonValues.string(step.value);
            at[0]++;
        }
        case VALUE_NUMBER ->
        {
            value = YamlJsonValues.number(new BigDecimal(step.value));
            at[0]++;
        }
        case VALUE_TRUE ->
        {
            value = JsonValue.TRUE;
            at[0]++;
        }
        case VALUE_FALSE ->
        {
            value = JsonValue.FALSE;
            at[0]++;
        }
        default ->
        {
            value = JsonValue.NULL;
            at[0]++;
        }
        }
        return value;
    }

    private void capture(
        YamlParser parser)
    {
        while (parser.hasNext())
        {
            YamlEvent event = parser.next();
            CharSequence value = parser.value();
            events.add(new Yev(event, value != null ? value.toString() : null, parser.scalarType(),
                parser.anchor(), parser.tag(), parser.alias(), parser.location()));
        }
    }

    private void indexAnchors()
    {
        for (int index = 0; index < events.size(); index++)
        {
            Yev event = events.get(index);
            if (event.anchor != null)
            {
                // an anchor name may be redefined; key each definition by its source offset so an alias can
                // resolve to the nearest preceding definition (YAML 1.2 anchor scoping)
                anchors.computeIfAbsent(event.anchor, name -> new TreeMap<>())
                    .put(event.location.offset(), new YamlAlias(events, index, nodeEnd(index)));
            }
        }
    }

    private int nodeEnd(
        int index)
    {
        YamlEvent kind = events.get(index).event;
        int end;
        if (kind == YamlEvent.MAPPING_START || kind == YamlEvent.SEQUENCE_START)
        {
            int depth = 0;
            int at = index;
            do
            {
                YamlEvent event = events.get(at).event;
                if (event == YamlEvent.MAPPING_START || event == YamlEvent.SEQUENCE_START)
                {
                    depth++;
                }
                else if (event == YamlEvent.MAPPING_END || event == YamlEvent.SEQUENCE_END)
                {
                    depth--;
                }
                at++;
            }
            while (depth != 0);
            end = at;
        }
        else
        {
            end = index + 1;
        }
        return end;
    }

    private void project()
    {
        int index = 0;
        while (index < events.size())
        {
            if (events.get(index).event == YamlEvent.DOCUMENT_START)
            {
                int rootStart = index + 1;
                int rootEnd = nodeEnd(rootStart);
                projectNode(new YamlAlias(events, rootStart, rootEnd), new HashSet<>());
                // the root-terminal event reports the document boundary (the next document's start or the
                // stream end), which YamlParser frames on DOCUMENT_END
                YamlJsonLocation boundary = location(events.get(rootEnd).location);
                if (!steps.isEmpty())
                {
                    steps.get(steps.size() - 1).location = boundary;
                }
                end = boundary;
                index = rootEnd + 1;
            }
            else
            {
                index++;
            }
        }
    }

    private void projectNode(
        YamlAlias cursor,
        Set<String> active)
    {
        Yev node = cursor.next();
        switch (node.event)
        {
        case MAPPING_START ->
        {
            emit(Event.START_OBJECT, null, node.location);
            Set<String> keys = uniqueKeys ? new HashSet<>() : null;
            while (cursor.peek() != YamlEvent.MAPPING_END)
            {
                projectKey(cursor, keys);
                projectNode(cursor, active);
            }
            cursor.next();
            emit(Event.END_OBJECT, null, node.location);
        }
        case SEQUENCE_START ->
        {
            emit(Event.START_ARRAY, null, node.location);
            while (cursor.peek() != YamlEvent.SEQUENCE_END)
            {
                projectNode(cursor, active);
            }
            cursor.next();
            emit(Event.END_ARRAY, null, node.location);
        }
        case ALIAS -> projectAlias(node, active);
        default -> emitScalar(node);
        }
    }

    private void projectKey(
        YamlAlias cursor,
        Set<String> keys)
    {
        Yev key = cursor.next();
        if (key.event == YamlEvent.SCALAR)
        {
            emitKey(scalarText(key), key.location, keys);
        }
        else if (key.event == YamlEvent.ALIAS && resolve(key).scalar())
        {
            emitKey(scalarText(resolve(key).first()), key.location, keys);
        }
        else
        {
            throw error("Non-scalar YAML mapping keys are not supported", key.location);
        }
    }

    private void projectAlias(
        Yev reference,
        Set<String> active)
    {
        YamlAlias alias = resolve(reference);
        if (alias.scalar() && alias.first().scalarType == null)
        {
            // an alias to an anchored key resolves to that key's text in value position
            emit(Event.VALUE_STRING, scalarText(alias.first()), alias.first().location);
        }
        else if (active.add(reference.alias))
        {
            projectNode(alias.open(), active);
            active.remove(reference.alias);
        }
        else
        {
            throw error("Recursive YAML alias: " + reference.alias, reference.location);
        }
    }

    private YamlAlias resolve(
        Yev reference)
    {
        NavigableMap<Long, YamlAlias> defined = anchors.get(reference.alias);
        Map.Entry<Long, YamlAlias> nearest = defined != null ? defined.floorEntry(reference.location.offset()) : null;
        if (nearest == null)
        {
            throw error("Unresolved YAML alias: " + reference.alias, reference.location);
        }
        return nearest.getValue();
    }

    private void emitKey(
        String name,
        YamlLocation location,
        Set<String> keys)
    {
        if (keys != null && !keys.add(name))
        {
            throw error("Duplicate YAML mapping key: " + name, location);
        }
        emit(Event.KEY_NAME, name, location);
    }

    private void emitScalar(
        Yev scalar)
    {
        String tag = scalar.tag;
        if (tag == null)
        {
            emit(scalarEvent(scalar.scalarType), scalarData(scalar), scalar.location);
        }
        else
        {
            String text = scalarText(scalar);
            switch (tag)
            {
            case STR_TAG, NON_SPECIFIC_TAG -> emit(Event.VALUE_STRING, text, scalar.location);
            case NULL_TAG -> emit(Event.VALUE_NULL, null, scalar.location);
            case BOOL_TAG -> emitBool(text, scalar);
            case INT_TAG -> emitInteger(text, scalar);
            case FLOAT_TAG -> emitFloat(text, scalar);
            default -> emit(scalarEvent(scalar.scalarType), scalarData(scalar), scalar.location);
            }
        }
    }

    private void emitBool(
        String text,
        Yev scalar)
    {
        if ("true".equals(text))
        {
            emit(Event.VALUE_TRUE, null, scalar.location);
        }
        else if ("false".equals(text))
        {
            emit(Event.VALUE_FALSE, null, scalar.location);
        }
        else
        {
            emit(scalarEvent(scalar.scalarType), scalarData(scalar), scalar.location);
        }
    }

    private void emitInteger(
        String text,
        Yev scalar)
    {
        if (text.matches("-?0x[0-9a-fA-F]+") || text.matches("-?(?:0|[1-9][0-9]*)"))
        {
            emit(Event.VALUE_NUMBER, numberText(text), scalar.location);
        }
        else
        {
            emit(scalarEvent(scalar.scalarType), scalarData(scalar), scalar.location);
        }
    }

    private void emitFloat(
        String text,
        Yev scalar)
    {
        if (text.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?"))
        {
            emit(Event.VALUE_NUMBER, text, scalar.location);
        }
        else
        {
            emit(scalarEvent(scalar.scalarType), scalarData(scalar), scalar.location);
        }
    }

    private void emit(
        Event event,
        String value,
        YamlLocation location)
    {
        steps.add(new Step(event, value, location(location)));
    }

    private static Event scalarEvent(
        YamlScalarType type)
    {
        return switch (type)
        {
        case STRING -> Event.VALUE_STRING;
        case NUMBER -> Event.VALUE_NUMBER;
        case TRUE -> Event.VALUE_TRUE;
        case FALSE -> Event.VALUE_FALSE;
        case NULL -> Event.VALUE_NULL;
        };
    }

    private static String scalarData(
        Yev scalar)
    {
        return scalar.scalarType == YamlScalarType.STRING || scalar.scalarType == YamlScalarType.NUMBER ?
            scalar.value : null;
    }

    private static String scalarText(
        Yev scalar)
    {
        String text;
        if (scalar.value != null)
        {
            text = scalar.value;
        }
        else
        {
            text = switch (scalar.scalarType)
            {
            case TRUE -> "true";
            case FALSE -> "false";
            default -> "";
            };
        }
        return text;
    }

    private static String numberText(
        String text)
    {
        boolean negative = text.startsWith("-");
        String scalar = negative ? text.substring(1) : text;
        String result;
        if (scalar.startsWith("0x"))
        {
            String value = new BigInteger(scalar.substring(2), 16).toString();
            result = negative ? "-" + value : value;
        }
        else
        {
            result = text;
        }
        return result;
    }

    private static YamlJsonLocation location(
        YamlLocation location)
    {
        return new YamlJsonLocation(location);
    }

    private static JsonParsingException error(
        String message,
        YamlLocation location)
    {
        return new JsonParsingException(message, new YamlJsonLocation(location));
    }

    private static String readAll(
        Reader reader)
    {
        try
        {
            char[] buffer = new char[1024];
            int length = 0;
            int read;
            while ((read = reader.read(buffer, length, buffer.length - length)) != -1)
            {
                length += read;
                if (length == buffer.length)
                {
                    buffer = Arrays.copyOf(buffer, buffer.length << 1);
                }
            }
            return new String(buffer, 0, length);
        }
        catch (IOException ex)
        {
            throw new JsonParsingException(ex.getMessage(), ex, new YamlJsonLocation(new YamlLocation(1, 1, 0)));
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
            throw new JsonParsingException(ex.getMessage(), ex, new YamlJsonLocation(new YamlLocation(1, 1, 0)));
        }
    }

    /**
     * A replayable cursor over the captured events of an anchored node (its {@code &name} definition spanning
     * {@code [from, to)}). An alias to the anchor resolves to a fresh {@link #open()} cursor that the projector
     * pulls the anchored value from, the way it pulls from the underlying parser.
     */
    private static final class YamlAlias
    {
        private final List<Yev> events;
        private final int from;
        private final int to;
        private int at;

        private YamlAlias(
            List<Yev> events,
            int from,
            int to)
        {
            this.events = events;
            this.from = from;
            this.to = to;
            this.at = from;
        }

        private YamlAlias open()
        {
            return new YamlAlias(events, from, to);
        }

        private boolean scalar()
        {
            return to - from == 1 && events.get(from).event == YamlEvent.SCALAR;
        }

        private Yev first()
        {
            return events.get(from);
        }

        private YamlEvent peek()
        {
            return events.get(at).event;
        }

        private Yev next()
        {
            return events.get(at++);
        }
    }

    private static final class Yev
    {
        private final YamlEvent event;
        private final String value;
        private final YamlScalarType scalarType;
        private final String anchor;
        private final String tag;
        private final String alias;
        private final YamlLocation location;

        private Yev(
            YamlEvent event,
            String value,
            YamlScalarType scalarType,
            String anchor,
            String tag,
            String alias,
            YamlLocation location)
        {
            this.event = event;
            this.value = value;
            this.scalarType = scalarType;
            this.anchor = anchor;
            this.tag = tag;
            this.alias = alias;
            this.location = location;
        }
    }

    private static final class Step
    {
        private final Event event;
        private final String value;
        private YamlJsonLocation location;

        private Step(
            Event event,
            String value,
            YamlJsonLocation location)
        {
            this.event = event;
            this.value = value;
            this.location = location;
        }
    }
}
