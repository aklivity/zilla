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
    private final Map<String, NavigableMap<Long, int[]>> anchors;
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
        this.anchors = new HashMap<>();
        this.steps = new ArrayList<>();
        this.current = -1;
        try
        {
            project(new YamlParser(text));
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
        int[] at = new int[] {current};
        JsonValue value = value(at);
        // per the JsonParser contract, materializing a structured value advances the parser to the
        // matching END_OBJECT/END_ARRAY event; a scalar value is already positioned there, so this is a
        // no-op for scalars. Skipping this step leaves cursor/current stale, so a subsequent next() replays
        // the just-materialized value's events instead of continuing after it.
        current = at[0] - 1;
        cursor = at[0];
        return value;
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

    private void project(
        YamlParser parser)
    {
        while (parser.hasNext())
        {
            YamlEvent event = parser.next();
            if (event == YamlEvent.DOCUMENT_START)
            {
                projectNode(parser, parser.next());
            }
            else if (event == YamlEvent.DOCUMENT_END)
            {
                // the document boundary (the next document's start or the stream end) is reported on the
                // root-terminal step, the way the underlying parser frames it on DOCUMENT_END
                YamlJsonLocation boundary = location(parser.location());
                if (!steps.isEmpty())
                {
                    steps.get(steps.size() - 1).location = boundary;
                }
                end = boundary;
            }
        }
        // an empty YAML stream frames no document, so it projects no JSON value at all (mirroring YamlParser);
        // it is not turned into a null
    }

    /**
     * Projects one YAML node — whose opening {@code event} the caller has already pulled from the parser —
     * into JSON parser events, consuming the node's remaining events from the cursor. A node carrying an
     * anchor records the {@code [start, end)} range of its projected steps so a later alias can replay it;
     * the range is left open ({@code end == -1}) while the node is being projected, so a self-referential
     * alias is detected as recursive.
     */
    private void projectNode(
        YamlParser parser,
        YamlEvent event)
    {
        YamlLocation location = parser.location();
        int[] range = null;
        if (event != YamlEvent.ALIAS && parser.anchor() != null)
        {
            range = new int[] {steps.size(), -1};
            anchors.computeIfAbsent(parser.anchor(), name -> new TreeMap<>()).put(location.offset(), range);
        }
        switch (event)
        {
        case MAPPING_START ->
        {
            emit(Event.START_OBJECT, null, location);
            Set<String> keys = uniqueKeys ? new HashSet<>() : null;
            YamlEvent key = parser.next();
            while (key != YamlEvent.MAPPING_END)
            {
                projectKey(parser, key, keys);
                projectNode(parser, parser.next());
                key = parser.next();
            }
            emit(Event.END_OBJECT, null, location);
        }
        case SEQUENCE_START ->
        {
            emit(Event.START_ARRAY, null, location);
            YamlEvent item = parser.next();
            while (item != YamlEvent.SEQUENCE_END)
            {
                projectNode(parser, item);
                item = parser.next();
            }
            emit(Event.END_ARRAY, null, location);
        }
        case ALIAS -> projectAlias(parser.alias(), location);
        default -> emitScalar(parser.value(), parser.scalarType(), parser.tag(), location);
        }
        if (range != null)
        {
            range[1] = steps.size();
        }
    }

    private void projectKey(
        YamlParser parser,
        YamlEvent event,
        Set<String> keys)
    {
        YamlLocation location = parser.location();
        if (event == YamlEvent.SCALAR)
        {
            String anchor = parser.anchor();
            int at = steps.size();
            emitKey(scalarText(text(parser.value()), parser.scalarType()), location, keys);
            if (anchor != null)
            {
                // an anchored key is a single step a later alias may resolve to in value or key position
                anchors.computeIfAbsent(anchor, name -> new TreeMap<>()).put(location.offset(), new int[] {at, at + 1});
            }
        }
        else if (event == YamlEvent.ALIAS)
        {
            int[] range = resolve(parser.alias(), location);
            if (range[1] - range[0] == 1 && isScalarStep(steps.get(range[0]).event))
            {
                emitKey(stepText(steps.get(range[0])), location, keys);
            }
            else
            {
                throw error("Non-scalar YAML mapping keys are not supported", location);
            }
        }
        else
        {
            throw error("Non-scalar YAML mapping keys are not supported", location);
        }
    }

    private void projectAlias(
        String name,
        YamlLocation location)
    {
        int[] range = resolve(name, location);
        if (range[1] == -1)
        {
            throw error("Recursive YAML alias: " + name, location);
        }
        if (range[1] - range[0] == 1 && steps.get(range[0]).event == Event.KEY_NAME)
        {
            // an alias to an anchored key resolves to that key's text in value position
            Step key = steps.get(range[0]);
            steps.add(new Step(Event.VALUE_STRING, key.value, key.location));
        }
        else
        {
            // replay the anchored node's already-projected (and already alias-resolved) steps
            for (int at = range[0]; at < range[1]; at++)
            {
                Step step = steps.get(at);
                steps.add(new Step(step.event, step.value, step.location));
            }
        }
    }

    private int[] resolve(
        String name,
        YamlLocation location)
    {
        NavigableMap<Long, int[]> defined = anchors.get(name);
        Map.Entry<Long, int[]> nearest = defined != null ? defined.floorEntry(location.offset()) : null;
        if (nearest == null)
        {
            throw error("Unresolved YAML alias: " + name, location);
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
        CharSequence rawValue,
        YamlScalarType scalarType,
        String tag,
        YamlLocation location)
    {
        String value = text(rawValue);
        if (tag == null)
        {
            emit(scalarEvent(scalarType), scalarData(value, scalarType), location);
        }
        else
        {
            String text = scalarText(value, scalarType);
            switch (tag)
            {
            case STR_TAG, NON_SPECIFIC_TAG -> emit(Event.VALUE_STRING, text, location);
            case NULL_TAG -> emit(Event.VALUE_NULL, null, location);
            case BOOL_TAG -> emitBool(text, value, scalarType, location);
            case INT_TAG -> emitInteger(text, value, scalarType, location);
            case FLOAT_TAG -> emitFloat(text, value, scalarType, location);
            default -> emit(scalarEvent(scalarType), scalarData(value, scalarType), location);
            }
        }
    }

    private void emitBool(
        String text,
        String value,
        YamlScalarType scalarType,
        YamlLocation location)
    {
        if ("true".equals(text))
        {
            emit(Event.VALUE_TRUE, null, location);
        }
        else if ("false".equals(text))
        {
            emit(Event.VALUE_FALSE, null, location);
        }
        else
        {
            emit(scalarEvent(scalarType), scalarData(value, scalarType), location);
        }
    }

    private void emitInteger(
        String text,
        String value,
        YamlScalarType scalarType,
        YamlLocation location)
    {
        if (text.matches("-?0x[0-9a-fA-F]+") || text.matches("-?(?:0|[1-9][0-9]*)"))
        {
            emit(Event.VALUE_NUMBER, numberText(text), location);
        }
        else
        {
            emit(scalarEvent(scalarType), scalarData(value, scalarType), location);
        }
    }

    private void emitFloat(
        String text,
        String value,
        YamlScalarType scalarType,
        YamlLocation location)
    {
        if (text.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?"))
        {
            emit(Event.VALUE_NUMBER, text, location);
        }
        else
        {
            emit(scalarEvent(scalarType), scalarData(value, scalarType), location);
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
        String value,
        YamlScalarType scalarType)
    {
        return scalarType == YamlScalarType.STRING || scalarType == YamlScalarType.NUMBER ? value : null;
    }

    private static String scalarText(
        String value,
        YamlScalarType scalarType)
    {
        String text;
        if (value != null)
        {
            text = value;
        }
        else
        {
            text = switch (scalarType)
            {
            case TRUE -> "true";
            case FALSE -> "false";
            default -> "";
            };
        }
        return text;
    }

    private static String text(
        CharSequence value)
    {
        return value != null ? value.toString() : null;
    }

    private static boolean isScalarStep(
        Event event)
    {
        return switch (event)
        {
        case KEY_NAME, VALUE_STRING, VALUE_NUMBER, VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> true;
        default -> false;
        };
    }

    private static String stepText(
        Step step)
    {
        return switch (step.event)
        {
        case VALUE_TRUE -> "true";
        case VALUE_FALSE -> "false";
        default -> step.value != null ? step.value : "";
        };
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
