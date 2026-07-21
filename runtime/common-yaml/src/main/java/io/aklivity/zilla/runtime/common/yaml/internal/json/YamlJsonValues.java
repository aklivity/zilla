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
package io.aklivity.zilla.runtime.common.yaml.internal.json;

import static jakarta.json.JsonValue.FALSE;
import static jakarta.json.JsonValue.NULL;
import static jakarta.json.JsonValue.TRUE;
import static jakarta.json.stream.JsonParser.Event.END_ARRAY;
import static jakarta.json.stream.JsonParser.Event.END_OBJECT;
import static jakarta.json.stream.JsonParser.Event.KEY_NAME;
import static jakarta.json.stream.JsonParser.Event.START_ARRAY;
import static jakarta.json.stream.JsonParser.Event.START_OBJECT;
import static jakarta.json.stream.JsonParser.Event.VALUE_FALSE;
import static jakarta.json.stream.JsonParser.Event.VALUE_NULL;
import static jakarta.json.stream.JsonParser.Event.VALUE_NUMBER;
import static jakarta.json.stream.JsonParser.Event.VALUE_STRING;
import static jakarta.json.stream.JsonParser.Event.VALUE_TRUE;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonException;
import jakarta.json.JsonMergePatch;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonPatch;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

final class YamlJsonValues
{
    private YamlJsonValues()
    {
    }

    static JsonObjectBuilder objectBuilder()
    {
        return new ObjectBuilder();
    }

    static JsonObjectBuilder objectBuilder(
        JsonObject object)
    {
        return new ObjectBuilder(object);
    }

    static JsonObjectBuilder objectBuilder(
        Map<String, ?> map)
    {
        return new ObjectBuilder(map);
    }

    static JsonArrayBuilder arrayBuilder()
    {
        return new ArrayBuilder();
    }

    static JsonArrayBuilder arrayBuilder(
        JsonArray array)
    {
        return new ArrayBuilder(array);
    }

    static JsonArrayBuilder arrayBuilder(
        Collection<?> collection)
    {
        return new ArrayBuilder(collection);
    }

    static JsonBuilderFactory builderFactory(
        Map<String, ?> config)
    {
        return new BuilderFactory(config);
    }

    static JsonString string(
        String value)
    {
        return new StringValue(Objects.requireNonNull(value, "value"));
    }

    static JsonNumber number(
        int value)
    {
        return new NumberValue(BigDecimal.valueOf(value));
    }

    static JsonNumber number(
        long value)
    {
        return new NumberValue(BigDecimal.valueOf(value));
    }

    static JsonNumber number(
        double value)
    {
        if (!Double.isFinite(value))
        {
            throw new NumberFormatException("Non-finite double values are not valid JSON values");
        }
        return new NumberValue(BigDecimal.valueOf(value));
    }

    static JsonNumber number(
        BigDecimal value)
    {
        return new NumberValue(Objects.requireNonNull(value, "value"));
    }

    static JsonNumber number(
        BigInteger value)
    {
        return new NumberValue(new BigDecimal(Objects.requireNonNull(value, "value")));
    }

    static JsonNumber number(
        Number value)
    {
        Objects.requireNonNull(value, "value");
        if (value instanceof BigDecimal decimal)
        {
            return number(decimal);
        }
        if (value instanceof BigInteger integer)
        {
            return number(integer);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
        {
            return new NumberValue(BigDecimal.valueOf(value.longValue()));
        }
        return number(value.doubleValue());
    }

    static JsonValue value(
        Object value)
    {
        if (value == null)
        {
            return NULL;
        }
        if (value instanceof JsonValue json)
        {
            return json;
        }
        if (value instanceof String string)
        {
            return string(string);
        }
        if (value instanceof BigDecimal decimal)
        {
            return number(decimal);
        }
        if (value instanceof BigInteger integer)
        {
            return number(integer);
        }
        if (value instanceof Number number)
        {
            return number(number);
        }
        if (value instanceof Boolean bool)
        {
            return bool ? TRUE : FALSE;
        }
        if (value instanceof Map<?, ?> map)
        {
            ObjectBuilder builder = new ObjectBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                builder.add(String.valueOf(entry.getKey()), value(entry.getValue()));
            }
            return builder.build();
        }
        if (value instanceof Collection<?> collection)
        {
            ArrayBuilder builder = new ArrayBuilder();
            collection.forEach(v -> builder.add(value(v)));
            return builder.build();
        }
        throw new JsonException("Unsupported JSON value type: " + value.getClass());
    }

    static JsonParser parser(
        JsonValue value)
    {
        return new ValueParser(value);
    }

    static JsonPointer pointer(
        String pointer)
    {
        return new Pointer(pointer);
    }

    static JsonPatch patch(
        JsonArray operations)
    {
        List<PatchOperation> patch = new ArrayList<>();
        for (JsonValue value : operations)
        {
            JsonObject operation = value.asJsonObject();
            String op = operation.getString("op");
            String path = operation.getString("path");
            String from = operation.containsKey("from") ? operation.getString("from") : null;
            JsonValue opValue = operation.containsKey("value") ? operation.get("value") : null;
            patch.add(new PatchOperation(op, path, from, opValue));
        }
        return new Patch(patch);
    }

    static JsonPatchBuilder patchBuilder(
        JsonArray operations)
    {
        PatchBuilder builder = new PatchBuilder();
        for (JsonValue value : operations)
        {
            JsonObject operation = value.asJsonObject();
            String op = operation.getString("op");
            String path = operation.getString("path");
            switch (op)
            {
            case "add" -> builder.add(path, operation.get("value"));
            case "remove" -> builder.remove(path);
            case "replace" -> builder.replace(path, operation.get("value"));
            case "move" -> builder.move(path, operation.getString("from"));
            case "copy" -> builder.copy(path, operation.getString("from"));
            case "test" -> builder.test(path, operation.get("value"));
            default -> throw new JsonException("Unsupported JSON patch operation: " + op);
            }
        }
        return builder;
    }

    static JsonPatch diff(
        JsonStructure source,
        JsonStructure target)
    {
        PatchBuilder builder = new PatchBuilder();
        diff("", source, target, builder);
        return builder.build();
    }

    static JsonMergePatch mergePatch(
        JsonValue patch)
    {
        return new MergePatch(patch);
    }

    static JsonMergePatch mergeDiff(
        JsonValue source,
        JsonValue target)
    {
        return new MergePatch(mergeDiffValue(source, target));
    }

    private static void diff(
        String path,
        JsonValue source,
        JsonValue target,
        PatchBuilder builder)
    {
        if (Objects.equals(source, target))
        {
            return;
        }
        if (source instanceof JsonObject sourceObject && target instanceof JsonObject targetObject)
        {
            for (String name : sourceObject.keySet())
            {
                if (!targetObject.containsKey(name))
                {
                    builder.remove(path + "/" + escapePointer(name));
                }
            }
            for (Map.Entry<String, JsonValue> entry : targetObject.entrySet())
            {
                String child = path + "/" + escapePointer(entry.getKey());
                if (sourceObject.containsKey(entry.getKey()))
                {
                    diff(child, sourceObject.get(entry.getKey()), entry.getValue(), builder);
                }
                else
                {
                    builder.add(child, entry.getValue());
                }
            }
        }
        else
        {
            builder.replace(path, target);
        }
    }

    private static JsonValue mergeDiffValue(
        JsonValue source,
        JsonValue target)
    {
        if (Objects.equals(source, target))
        {
            return objectBuilder().build();
        }
        if (source instanceof JsonObject sourceObject && target instanceof JsonObject targetObject)
        {
            ObjectBuilder diff = new ObjectBuilder();
            for (String name : sourceObject.keySet())
            {
                if (!targetObject.containsKey(name))
                {
                    diff.addNull(name);
                }
            }
            for (Map.Entry<String, JsonValue> entry : targetObject.entrySet())
            {
                JsonValue value = sourceObject.containsKey(entry.getKey())
                    ? mergeDiffValue(sourceObject.get(entry.getKey()), entry.getValue())
                    : entry.getValue();
                if (!(value instanceof JsonObject object && object.isEmpty()))
                {
                    diff.add(entry.getKey(), value);
                }
            }
            return diff.build();
        }
        return target;
    }

    private static String toJson(
        JsonValue value)
    {
        return switch (value.getValueType())
        {
        case OBJECT -> objectToJson(value.asJsonObject());
        case ARRAY -> arrayToJson(value.asJsonArray());
        case STRING -> quote(((JsonString) value).getString());
        case NUMBER -> value.toString();
        case TRUE -> "true";
        case FALSE -> "false";
        case NULL -> "null";
        };
    }

    private static String objectToJson(
        JsonObject object)
    {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        boolean comma = false;
        for (Map.Entry<String, JsonValue> entry : object.entrySet())
        {
            if (comma)
            {
                builder.append(',');
            }
            builder.append(quote(entry.getKey())).append(':').append(toJson(entry.getValue()));
            comma = true;
        }
        return builder.append('}').toString();
    }

    private static String arrayToJson(
        JsonArray array)
    {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        boolean comma = false;
        for (JsonValue value : array)
        {
            if (comma)
            {
                builder.append(',');
            }
            builder.append(toJson(value));
            comma = true;
        }
        return builder.append(']').toString();
    }

    private static String quote(
        String value)
    {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            switch (c)
            {
            case '"' -> builder.append("\\\"");
            case '\\' -> builder.append("\\\\");
            case '\b' -> builder.append("\\b");
            case '\f' -> builder.append("\\f");
            case '\n' -> builder.append("\\n");
            case '\r' -> builder.append("\\r");
            case '\t' -> builder.append("\\t");
            default ->
            {
                if (c < 0x20)
                {
                    builder.append(String.format("\\u%04x", (int) c));
                }
                else
                {
                    builder.append(c);
                }
            }
            }
        }
        return builder.append('"').toString();
    }

    private static String escapePointer(
        String token)
    {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private static String unescapePointer(
        String token)
    {
        return token.replace("~1", "/").replace("~0", "~");
    }

    private static List<String> tokens(
        String pointer)
    {
        if (pointer.isEmpty())
        {
            return List.of();
        }
        if (!pointer.startsWith("/"))
        {
            throw new JsonException("Invalid JSON pointer: " + pointer);
        }
        String[] parts = pointer.substring(1).split("/", -1);
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts)
        {
            tokens.add(unescapePointer(part));
        }
        return tokens;
    }

    private static final class ObjectValue extends AbstractMap<String, JsonValue> implements JsonObject
    {
        private final Map<String, JsonValue> values;
        private final Set<Entry<String, JsonValue>> entries;

        private ObjectValue(
            Map<String, JsonValue> values)
        {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.entries = this.values.entrySet();
        }

        @Override
        public Set<Entry<String, JsonValue>> entrySet()
        {
            return entries;
        }

        @Override
        public JsonArray getJsonArray(
            String name)
        {
            return (JsonArray) get(name);
        }

        @Override
        public JsonObject getJsonObject(
            String name)
        {
            return (JsonObject) get(name);
        }

        @Override
        public JsonNumber getJsonNumber(
            String name)
        {
            return (JsonNumber) get(name);
        }

        @Override
        public JsonString getJsonString(
            String name)
        {
            return (JsonString) get(name);
        }

        @Override
        public String getString(
            String name)
        {
            return getJsonString(name).getString();
        }

        @Override
        public String getString(
            String name,
            String defaultValue)
        {
            JsonString value = getJsonString(name);
            return value != null ? value.getString() : defaultValue;
        }

        @Override
        public int getInt(
            String name)
        {
            return getJsonNumber(name).intValue();
        }

        @Override
        public int getInt(
            String name,
            int defaultValue)
        {
            JsonNumber value = getJsonNumber(name);
            return value != null ? value.intValue() : defaultValue;
        }

        @Override
        public boolean getBoolean(
            String name)
        {
            return switch (get(name).getValueType())
            {
            case TRUE -> true;
            case FALSE -> false;
            default -> throw new ClassCastException("JSON value is not a boolean");
            };
        }

        @Override
        public boolean getBoolean(
            String name,
            boolean defaultValue)
        {
            JsonValue value = get(name);
            return value != null ? value == TRUE : defaultValue;
        }

        @Override
        public boolean isNull(
            String name)
        {
            return get(name) == NULL;
        }

        @Override
        public ValueType getValueType()
        {
            return ValueType.OBJECT;
        }

        @Override
        public JsonValue getValue(
            String jsonPointer)
        {
            return pointer(jsonPointer).getValue(this);
        }

        @Override
        public String toString()
        {
            return objectToJson(this);
        }
    }

    private static final class ArrayValue extends AbstractList<JsonValue> implements JsonArray
    {
        private final List<JsonValue> values;

        private ArrayValue(
            List<JsonValue> values)
        {
            this.values = List.copyOf(values);
        }

        @Override
        public JsonValue get(
            int index)
        {
            return values.get(index);
        }

        @Override
        public int size()
        {
            return values.size();
        }

        @Override
        public JsonObject getJsonObject(
            int index)
        {
            return (JsonObject) get(index);
        }

        @Override
        public JsonArray getJsonArray(
            int index)
        {
            return (JsonArray) get(index);
        }

        @Override
        public JsonNumber getJsonNumber(
            int index)
        {
            return (JsonNumber) get(index);
        }

        @Override
        public JsonString getJsonString(
            int index)
        {
            return (JsonString) get(index);
        }

        @Override
        public <T extends JsonValue> List<T> getValuesAs(
            Class<T> clazz)
        {
            List<T> result = new ArrayList<>(values.size());
            for (JsonValue value : values)
            {
                result.add(clazz.cast(value));
            }
            return List.copyOf(result);
        }

        @Override
        public String getString(
            int index)
        {
            return getJsonString(index).getString();
        }

        @Override
        public String getString(
            int index,
            String defaultValue)
        {
            JsonValue value = get(index);
            return value instanceof JsonString string ? string.getString() : defaultValue;
        }

        @Override
        public int getInt(
            int index)
        {
            return getJsonNumber(index).intValue();
        }

        @Override
        public int getInt(
            int index,
            int defaultValue)
        {
            JsonValue value = get(index);
            return value instanceof JsonNumber number ? number.intValue() : defaultValue;
        }

        @Override
        public boolean getBoolean(
            int index)
        {
            return switch (get(index).getValueType())
            {
            case TRUE -> true;
            case FALSE -> false;
            default -> throw new ClassCastException("JSON value is not a boolean");
            };
        }

        @Override
        public boolean getBoolean(
            int index,
            boolean defaultValue)
        {
            JsonValue value = get(index);
            return value == TRUE ? true : value == FALSE ? false : defaultValue;
        }

        @Override
        public boolean isNull(
            int index)
        {
            return get(index) == NULL;
        }

        @Override
        public ValueType getValueType()
        {
            return ValueType.ARRAY;
        }

        @Override
        public String toString()
        {
            return arrayToJson(this);
        }
    }

    private static final class StringValue implements JsonString
    {
        private final String value;

        private StringValue(
            String value)
        {
            this.value = value;
        }

        @Override
        public String getString()
        {
            return value;
        }

        @Override
        public CharSequence getChars()
        {
            return value;
        }

        @Override
        public ValueType getValueType()
        {
            return ValueType.STRING;
        }

        @Override
        public String toString()
        {
            return quote(value);
        }

        @Override
        public int hashCode()
        {
            return value.hashCode();
        }

        @Override
        public boolean equals(
            Object obj)
        {
            return obj instanceof JsonString that && value.equals(that.getString());
        }
    }

    private static final class NumberValue implements JsonNumber
    {
        private final BigDecimal value;

        private NumberValue(
            BigDecimal value)
        {
            this.value = value.stripTrailingZeros();
        }

        @Override
        public boolean isIntegral()
        {
            return value.scale() <= 0;
        }

        @Override
        public int intValue()
        {
            return value.intValue();
        }

        @Override
        public int intValueExact()
        {
            return value.intValueExact();
        }

        @Override
        public long longValue()
        {
            return value.longValue();
        }

        @Override
        public long longValueExact()
        {
            return value.longValueExact();
        }

        @Override
        public BigInteger bigIntegerValue()
        {
            return value.toBigInteger();
        }

        @Override
        public BigInteger bigIntegerValueExact()
        {
            return value.toBigIntegerExact();
        }

        @Override
        public double doubleValue()
        {
            return value.doubleValue();
        }

        @Override
        public BigDecimal bigDecimalValue()
        {
            return value;
        }

        @Override
        public ValueType getValueType()
        {
            return ValueType.NUMBER;
        }

        @Override
        public String toString()
        {
            return value.toPlainString();
        }

        @Override
        public int hashCode()
        {
            return value.hashCode();
        }

        @Override
        public boolean equals(
            Object obj)
        {
            return obj instanceof JsonNumber that && value.compareTo(that.bigDecimalValue()) == 0;
        }
    }

    private static final class ObjectBuilder implements JsonObjectBuilder
    {
        private final Map<String, JsonValue> values;

        private ObjectBuilder()
        {
            this.values = new LinkedHashMap<>();
        }

        private ObjectBuilder(
            JsonObject object)
        {
            this();
            values.putAll(object);
        }

        private ObjectBuilder(
            Map<String, ?> map)
        {
            this();
            map.forEach((name, item) -> add(name, YamlJsonValues.value(item)));
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            JsonValue value)
        {
            values.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            String value)
        {
            return add(name, string(value));
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            BigInteger value)
        {
            return add(name, number(value));
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            BigDecimal value)
        {
            return add(name, number(value));
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            int value)
        {
            return add(name, number(value));
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            long value)
        {
            return add(name, number(value));
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            double value)
        {
            return add(name, number(value));
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            boolean value)
        {
            return add(name, value ? TRUE : FALSE);
        }

        @Override
        public JsonObjectBuilder addNull(
            String name)
        {
            return add(name, NULL);
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            JsonObjectBuilder builder)
        {
            return add(name, builder.build());
        }

        @Override
        public JsonObjectBuilder add(
            String name,
            JsonArrayBuilder builder)
        {
            return add(name, builder.build());
        }

        @Override
        public JsonObjectBuilder remove(
            String name)
        {
            values.remove(name);
            return this;
        }

        @Override
        public JsonObject build()
        {
            return new ObjectValue(values);
        }
    }

    private static final class ArrayBuilder implements JsonArrayBuilder
    {
        private final List<JsonValue> values;

        private ArrayBuilder()
        {
            this.values = new ArrayList<>();
        }

        private ArrayBuilder(
            JsonArray array)
        {
            this();
            values.addAll(array);
        }

        private ArrayBuilder(
            Collection<?> collection)
        {
            this();
            collection.forEach(item -> add(YamlJsonValues.value(item)));
        }

        @Override
        public JsonArrayBuilder add(
            JsonValue value)
        {
            values.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        @Override
        public JsonArrayBuilder add(
            String value)
        {
            return add(string(value));
        }

        @Override
        public JsonArrayBuilder add(
            BigDecimal value)
        {
            return add(number(value));
        }

        @Override
        public JsonArrayBuilder add(
            BigInteger value)
        {
            return add(number(value));
        }

        @Override
        public JsonArrayBuilder add(
            int value)
        {
            return add(number(value));
        }

        @Override
        public JsonArrayBuilder add(
            long value)
        {
            return add(number(value));
        }

        @Override
        public JsonArrayBuilder add(
            double value)
        {
            return add(number(value));
        }

        @Override
        public JsonArrayBuilder add(
            boolean value)
        {
            return add(value ? TRUE : FALSE);
        }

        @Override
        public JsonArrayBuilder addNull()
        {
            return add(NULL);
        }

        @Override
        public JsonArrayBuilder add(
            JsonObjectBuilder builder)
        {
            return add(builder.build());
        }

        @Override
        public JsonArrayBuilder add(
            JsonArrayBuilder builder)
        {
            return add(builder.build());
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            JsonValue value)
        {
            values.add(index, Objects.requireNonNull(value, "value"));
            return this;
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            String value)
        {
            return add(index, string(value));
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            BigDecimal value)
        {
            return add(index, number(value));
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            BigInteger value)
        {
            return add(index, number(value));
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            int value)
        {
            return add(index, number(value));
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            long value)
        {
            return add(index, number(value));
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            double value)
        {
            return add(index, number(value));
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            boolean value)
        {
            return add(index, value ? TRUE : FALSE);
        }

        @Override
        public JsonArrayBuilder addNull(
            int index)
        {
            return add(index, NULL);
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            JsonObjectBuilder builder)
        {
            return add(index, builder.build());
        }

        @Override
        public JsonArrayBuilder add(
            int index,
            JsonArrayBuilder builder)
        {
            return add(index, builder.build());
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            JsonValue value)
        {
            values.set(index, Objects.requireNonNull(value, "value"));
            return this;
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            String value)
        {
            return set(index, string(value));
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            BigDecimal value)
        {
            return set(index, number(value));
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            BigInteger value)
        {
            return set(index, number(value));
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            int value)
        {
            return set(index, number(value));
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            long value)
        {
            return set(index, number(value));
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            double value)
        {
            return set(index, number(value));
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            boolean value)
        {
            return set(index, value ? TRUE : FALSE);
        }

        @Override
        public JsonArrayBuilder setNull(
            int index)
        {
            return set(index, NULL);
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            JsonObjectBuilder builder)
        {
            return set(index, builder.build());
        }

        @Override
        public JsonArrayBuilder set(
            int index,
            JsonArrayBuilder builder)
        {
            return set(index, builder.build());
        }

        @Override
        public JsonArrayBuilder remove(
            int index)
        {
            values.remove(index);
            return this;
        }

        @Override
        public JsonArray build()
        {
            return new ArrayValue(values);
        }
    }

    private static final class BuilderFactory implements JsonBuilderFactory
    {
        private final Map<String, ?> config;

        private BuilderFactory(
            Map<String, ?> config)
        {
            this.config = config == null ? Map.of() : Map.copyOf(config);
        }

        @Override
        public JsonObjectBuilder createObjectBuilder()
        {
            return objectBuilder();
        }

        @Override
        public JsonObjectBuilder createObjectBuilder(
            JsonObject object)
        {
            return objectBuilder(object);
        }

        @Override
        public JsonObjectBuilder createObjectBuilder(
            Map<String, Object> map)
        {
            return objectBuilder(map);
        }

        @Override
        public JsonArrayBuilder createArrayBuilder()
        {
            return arrayBuilder();
        }

        @Override
        public JsonArrayBuilder createArrayBuilder(
            JsonArray array)
        {
            return arrayBuilder(array);
        }

        @Override
        public JsonArrayBuilder createArrayBuilder(
            Collection<?> collection)
        {
            return arrayBuilder(collection);
        }

        @Override
        public Map<String, ?> getConfigInUse()
        {
            return config;
        }
    }

    private static final class Pointer implements JsonPointer
    {
        private final String pointer;
        private final List<String> tokens;

        private Pointer(
            String pointer)
        {
            this.pointer = Objects.requireNonNull(pointer, "pointer");
            this.tokens = tokens(pointer);
        }

        @Override
        public <T extends JsonStructure> T add(
            T target,
            JsonValue value)
        {
            return apply(target, value, "add");
        }

        @Override
        public <T extends JsonStructure> T remove(
            T target)
        {
            return apply(target, null, "remove");
        }

        @Override
        public <T extends JsonStructure> T replace(
            T target,
            JsonValue value)
        {
            return apply(target, value, "replace");
        }

        @Override
        public boolean containsValue(
            JsonStructure target)
        {
            try
            {
                getValue(target);
                return true;
            }
            catch (JsonException | IndexOutOfBoundsException | ClassCastException ex)
            {
                return false;
            }
        }

        @Override
        public JsonValue getValue(
            JsonStructure target)
        {
            JsonValue current = target;
            for (String token : tokens)
            {
                current = current instanceof JsonObject object ? object.get(token) :
                    current instanceof JsonArray array ? array.get(Integer.parseInt(token)) : null;
                if (current == null)
                {
                    throw new JsonException("JSON pointer not found: " + pointer);
                }
            }
            return current;
        }

        @Override
        public String toString()
        {
            return pointer;
        }

        @SuppressWarnings("unchecked")
        private <T extends JsonStructure> T apply(
            T target,
            JsonValue value,
            String op)
        {
            JsonValue result = applyAt(target, 0, value, op);
            return (T) result;
        }

        private JsonValue applyAt(
            JsonValue current,
            int index,
            JsonValue value,
            String op)
        {
            if (index == tokens.size())
            {
                return switch (op)
                {
                case "remove" -> throw new JsonException("Cannot remove root JSON value");
                case "add", "replace" -> value;
                default -> throw new JsonException("Unsupported pointer operation: " + op);
                };
            }

            String token = tokens.get(index);
            boolean leaf = index == tokens.size() - 1;
            if (current instanceof JsonObject object)
            {
                ObjectBuilder builder = new ObjectBuilder(object);
                if (leaf)
                {
                    switch (op)
                    {
                    case "add", "replace" -> builder.add(token, value);
                    case "remove" -> builder.remove(token);
                    default -> throw new JsonException("Unsupported pointer operation: " + op);
                    }
                }
                else
                {
                    builder.add(token, applyAt(object.get(token), index + 1, value, op));
                }
                return builder.build();
            }
            if (current instanceof JsonArray array)
            {
                ArrayBuilder builder = new ArrayBuilder(array);
                int at = "-".equals(token) ? array.size() : Integer.parseInt(token);
                if (leaf)
                {
                    switch (op)
                    {
                    case "add" -> builder.add(at, value);
                    case "replace" -> builder.set(at, value);
                    case "remove" -> builder.remove(at);
                    default -> throw new JsonException("Unsupported pointer operation: " + op);
                    }
                }
                else
                {
                    builder.set(at, applyAt(array.get(at), index + 1, value, op));
                }
                return builder.build();
            }
            throw new JsonException("Cannot apply JSON pointer to scalar value: " + pointer);
        }
    }

    private record PatchOperation(String op, String path, String from, JsonValue value)
    {
    }

    private static final class Patch implements JsonPatch
    {
        private final List<PatchOperation> operations;

        private Patch(
            List<PatchOperation> operations)
        {
            this.operations = List.copyOf(operations);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends JsonStructure> T apply(
            T target)
        {
            JsonStructure result = target;
            for (PatchOperation operation : operations)
            {
                Pointer pointer = new Pointer(operation.path);
                switch (operation.op)
                {
                case "add" -> result = pointer.add(result, operation.value);
                case "remove" -> result = pointer.remove(result);
                case "replace" -> result = pointer.replace(result, operation.value);
                case "move" ->
                {
                    JsonValue moved = new Pointer(operation.from).getValue(result);
                    result = new Pointer(operation.from).remove(result);
                    result = pointer.add(result, moved);
                }
                case "copy" -> result = pointer.add(result, new Pointer(operation.from).getValue(result));
                case "test" ->
                {
                    if (!Objects.equals(pointer.getValue(result), operation.value))
                    {
                        throw new JsonException("JSON patch test failed at " + operation.path);
                    }
                }
                default -> throw new JsonException("Unsupported JSON patch operation: " + operation.op);
                }
            }
            return (T) result;
        }

        @Override
        public JsonArray toJsonArray()
        {
            ArrayBuilder builder = new ArrayBuilder();
            for (PatchOperation operation : operations)
            {
                ObjectBuilder object = new ObjectBuilder();
                object.add("op", operation.op);
                object.add("path", operation.path);
                if (operation.from != null)
                {
                    object.add("from", operation.from);
                }
                if (operation.value != null)
                {
                    object.add("value", operation.value);
                }
                builder.add(object);
            }
            return builder.build();
        }
    }

    static final class PatchBuilder implements JsonPatchBuilder
    {
        private final List<PatchOperation> operations;

        PatchBuilder()
        {
            this.operations = new ArrayList<>();
        }

        @Override
        public JsonPatchBuilder add(
            String path,
            JsonValue value)
        {
            operations.add(new PatchOperation("add", path, null, value));
            return this;
        }

        @Override
        public JsonPatchBuilder add(
            String path,
            String value)
        {
            return add(path, string(value));
        }

        @Override
        public JsonPatchBuilder add(
            String path,
            int value)
        {
            return add(path, number(value));
        }

        @Override
        public JsonPatchBuilder add(
            String path,
            boolean value)
        {
            return add(path, value ? TRUE : FALSE);
        }

        @Override
        public JsonPatchBuilder remove(
            String path)
        {
            operations.add(new PatchOperation("remove", path, null, null));
            return this;
        }

        @Override
        public JsonPatchBuilder replace(
            String path,
            JsonValue value)
        {
            operations.add(new PatchOperation("replace", path, null, value));
            return this;
        }

        @Override
        public JsonPatchBuilder replace(
            String path,
            String value)
        {
            return replace(path, string(value));
        }

        @Override
        public JsonPatchBuilder replace(
            String path,
            int value)
        {
            return replace(path, number(value));
        }

        @Override
        public JsonPatchBuilder replace(
            String path,
            boolean value)
        {
            return replace(path, value ? TRUE : FALSE);
        }

        @Override
        public JsonPatchBuilder move(
            String path,
            String from)
        {
            operations.add(new PatchOperation("move", path, from, null));
            return this;
        }

        @Override
        public JsonPatchBuilder copy(
            String path,
            String from)
        {
            operations.add(new PatchOperation("copy", path, from, null));
            return this;
        }

        @Override
        public JsonPatchBuilder test(
            String path,
            JsonValue value)
        {
            operations.add(new PatchOperation("test", path, null, value));
            return this;
        }

        @Override
        public JsonPatchBuilder test(
            String path,
            String value)
        {
            return test(path, string(value));
        }

        @Override
        public JsonPatchBuilder test(
            String path,
            int value)
        {
            return test(path, number(value));
        }

        @Override
        public JsonPatchBuilder test(
            String path,
            boolean value)
        {
            return test(path, value ? TRUE : FALSE);
        }

        @Override
        public JsonPatch build()
        {
            return new Patch(operations);
        }
    }

    private static final class MergePatch implements JsonMergePatch
    {
        private final JsonValue patch;

        private MergePatch(
            JsonValue patch)
        {
            this.patch = patch;
        }

        @Override
        public JsonValue apply(
            JsonValue target)
        {
            return merge(patch, target);
        }

        @Override
        public JsonValue toJsonValue()
        {
            return patch;
        }

        private static JsonValue merge(
            JsonValue patch,
            JsonValue target)
        {
            if (!(patch instanceof JsonObject patchObject))
            {
                return patch;
            }

            ObjectBuilder result = target instanceof JsonObject targetObject
                ? new ObjectBuilder(targetObject)
                : new ObjectBuilder();
            for (Map.Entry<String, JsonValue> entry : patchObject.entrySet())
            {
                if (entry.getValue() == NULL)
                {
                    result.remove(entry.getKey());
                }
                else
                {
                    JsonValue targetValue = target instanceof JsonObject targetObject ? targetObject.get(entry.getKey()) : null;
                    result.add(entry.getKey(), merge(entry.getValue(), targetValue));
                }
            }
            return result.build();
        }
    }

    private static final class ValueParser implements JsonParser
    {
        private final Deque<Frame> stack;
        private Event current;
        private JsonValue value;
        private String string;

        private ValueParser(
            JsonValue value)
        {
            this.stack = new ArrayDeque<>();
            this.stack.push(new Frame(value));
        }

        @Override
        public boolean hasNext()
        {
            return !stack.isEmpty();
        }

        @Override
        public Event next()
        {
            if (!hasNext())
            {
                throw new JsonParsingException("No more events", getLocation());
            }

            Frame frame = stack.peek();
            if (frame.value instanceof JsonObject object)
            {
                if (!frame.started)
                {
                    frame.started = true;
                    return event(START_OBJECT, object, null);
                }
                if (frame.pendingValue != null)
                {
                    JsonValue pending = frame.pendingValue;
                    frame.pendingValue = null;
                    stack.push(new Frame(pending));
                    return next();
                }
                if (frame.index < frame.entries.size())
                {
                    Map.Entry<String, JsonValue> entry = frame.entries.get(frame.index++);
                    frame.pendingValue = entry.getValue();
                    return event(KEY_NAME, string(entry.getKey()), entry.getKey());
                }
                stack.pop();
                return event(END_OBJECT, object, null);
            }
            if (frame.value instanceof JsonArray array)
            {
                if (!frame.started)
                {
                    frame.started = true;
                    return event(START_ARRAY, array, null);
                }
                if (frame.index < array.size())
                {
                    stack.push(new Frame(array.get(frame.index++)));
                    return next();
                }
                stack.pop();
                return event(END_ARRAY, array, null);
            }

            stack.pop();
            return switch (frame.value.getValueType())
            {
            case STRING -> event(VALUE_STRING, frame.value, ((JsonString) frame.value).getString());
            case NUMBER -> event(VALUE_NUMBER, frame.value, frame.value.toString());
            case TRUE -> event(VALUE_TRUE, frame.value, null);
            case FALSE -> event(VALUE_FALSE, frame.value, null);
            case NULL -> event(VALUE_NULL, frame.value, null);
            default -> throw new JsonParsingException("Unexpected JSON value", getLocation());
            };
        }

        @Override
        public Event currentEvent()
        {
            return current;
        }

        @Override
        public String getString()
        {
            if (string == null)
            {
                throw new IllegalStateException("No string value is available for current event");
            }
            return string;
        }

        @Override
        public boolean isIntegralNumber()
        {
            return ((JsonNumber) value).isIntegral();
        }

        @Override
        public int getInt()
        {
            return ((JsonNumber) value).intValue();
        }

        @Override
        public long getLong()
        {
            return ((JsonNumber) value).longValue();
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            return ((JsonNumber) value).bigDecimalValue();
        }

        @Override
        public JsonLocation getLocation()
        {
            return new YamlJsonLocation(new io.aklivity.zilla.runtime.common.yaml.internal.YamlLocation(1, 1, 0));
        }

        @Override
        public JsonObject getObject()
        {
            return value.asJsonObject();
        }

        @Override
        public JsonValue getValue()
        {
            return value;
        }

        @Override
        public JsonArray getArray()
        {
            return value.asJsonArray();
        }

        @Override
        public Stream<JsonValue> getArrayStream()
        {
            return getArray().stream();
        }

        @Override
        public Stream<Map.Entry<String, JsonValue>> getObjectStream()
        {
            return getObject().entrySet().stream();
        }

        @Override
        public Stream<JsonValue> getValueStream()
        {
            return Stream.of(value);
        }

        @Override
        public void skipArray()
        {
            skip(START_ARRAY);
        }

        @Override
        public void skipObject()
        {
            skip(START_OBJECT);
        }

        @Override
        public void close()
        {
        }

        private void skip(
            Event expected)
        {
            if (current != expected)
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

        private Event event(
            Event event,
            JsonValue value,
            String string)
        {
            this.current = event;
            this.value = value;
            this.string = string;
            return event;
        }

        private static final class Frame
        {
            final JsonValue value;
            final List<Map.Entry<String, JsonValue>> entries;
            int index;
            boolean started;
            JsonValue pendingValue;

            private Frame(
                JsonValue value)
            {
                this.value = value;
                this.entries = value instanceof JsonObject object ? List.copyOf(object.entrySet()) : List.of();
            }
        }
    }
}
