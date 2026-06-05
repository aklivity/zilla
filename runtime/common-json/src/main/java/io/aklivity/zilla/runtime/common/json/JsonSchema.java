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
package io.aklivity.zilla.runtime.common.json;

import static jakarta.json.stream.JsonParser.Event.END_ARRAY;
import static jakarta.json.stream.JsonParser.Event.END_OBJECT;
import static jakarta.json.stream.JsonParser.Event.VALUE_FALSE;
import static jakarta.json.stream.JsonParser.Event.VALUE_NULL;
import static jakarta.json.stream.JsonParser.Event.VALUE_NUMBER;
import static jakarta.json.stream.JsonParser.Event.VALUE_STRING;
import static jakarta.json.stream.JsonParser.Event.VALUE_TRUE;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * An immutable, compiled JSON Schema (draft-07 subset) that validates an instance by
 * consuming a streaming {@link JsonParser} event stream without materializing a DOM.
 * <p>
 * Compile once per schema and reuse for the lifetime of the binding; {@link
 * #validate(JsonParser)} holds no instance state and may be called repeatedly on the
 * owning worker thread.
 * <p>
 * Supported keywords: {@code type} (including {@code integer}), {@code enum} and {@code
 * const} (scalar values), {@code minimum}/{@code maximum}/{@code exclusiveMinimum}/{@code
 * exclusiveMaximum}/{@code multipleOf}, {@code minLength}/{@code maxLength}/{@code
 * pattern}, {@code items} (single schema), {@code minItems}/{@code maxItems}, {@code
 * properties}, {@code required}, {@code additionalProperties} (boolean or schema), and
 * {@code minProperties}/{@code maxProperties}. Unsupported assertion keywords (combinators,
 * {@code $ref}, {@code patternProperties}, etc.) fail fast at compile time.
 */
public final class JsonSchema
{
    private static final JsonSchema ANY = new JsonSchema(false);
    private static final JsonSchema NONE = new JsonSchema(true);

    private static final List<String> UNSUPPORTED = List.of(
        "patternProperties", "allOf", "anyOf", "oneOf", "not",
        "if", "then", "else", "$ref", "dependencies", "dependentRequired",
        "dependentSchemas", "propertyNames", "contains", "uniqueItems", "additionalItems");

    private enum JsonType
    {
        OBJECT, ARRAY, STRING, NUMBER, INTEGER, BOOLEAN, NULL
    }

    private final boolean deny;
    private final Set<JsonType> types;
    private final List<JsonValue> enums;
    private final JsonValue constant;
    private final BigDecimal minimum;
    private final BigDecimal maximum;
    private final BigDecimal exclusiveMinimum;
    private final BigDecimal exclusiveMaximum;
    private final BigDecimal multipleOf;
    private final int minLength;
    private final int maxLength;
    private final Pattern pattern;
    private final JsonSchema items;
    private final int minItems;
    private final int maxItems;
    private final Map<String, JsonSchema> properties;
    private final Set<String> required;
    private final boolean additionalAllowed;
    private final JsonSchema additionalSchema;
    private final int minProperties;
    private final int maxProperties;

    public static JsonSchema of(
        JsonObject schema)
    {
        return new JsonSchema(schema);
    }

    public boolean validate(
        JsonParser parser)
    {
        return parser.hasNext() && check(parser, parser.next());
    }

    private JsonSchema(
        JsonObject schema)
    {
        for (String keyword : UNSUPPORTED)
        {
            if (schema.containsKey(keyword))
            {
                throw new UnsupportedOperationException("JSON Schema keyword not yet supported: " + keyword);
            }
        }

        JsonValue itemsValue = schema.get("items");
        if (itemsValue != null && itemsValue.getValueType() == JsonValue.ValueType.ARRAY)
        {
            throw new UnsupportedOperationException("JSON Schema tuple items not yet supported");
        }

        JsonValue additional = schema.get("additionalProperties");
        boolean additionalAllowed = additional == null || additional.getValueType() != JsonValue.ValueType.FALSE;
        JsonSchema additionalSchema = additional != null && additional.getValueType() == JsonValue.ValueType.OBJECT
            ? new JsonSchema(additional.asJsonObject())
            : null;

        this.deny = false;
        this.types = parseTypes(schema.get("type"));
        this.enums = parseEnum(schema.get("enum"));
        this.constant = parseConst(schema.get("const"));
        this.minimum = number(schema, "minimum");
        this.maximum = number(schema, "maximum");
        this.exclusiveMinimum = number(schema, "exclusiveMinimum");
        this.exclusiveMaximum = number(schema, "exclusiveMaximum");
        this.multipleOf = number(schema, "multipleOf");
        this.minLength = integer(schema, "minLength");
        this.maxLength = integer(schema, "maxLength");
        this.pattern = schema.containsKey("pattern") ? Pattern.compile(schema.getString("pattern")) : null;
        this.items = itemsValue != null ? from(itemsValue) : null;
        this.minItems = integer(schema, "minItems");
        this.maxItems = integer(schema, "maxItems");
        this.properties = parseProperties(schema.get("properties"));
        this.required = parseRequired(schema.get("required"));
        this.additionalAllowed = additionalAllowed;
        this.additionalSchema = additionalSchema;
        this.minProperties = integer(schema, "minProperties");
        this.maxProperties = integer(schema, "maxProperties");
    }

    private JsonSchema(
        boolean deny)
    {
        this.deny = deny;
        this.types = null;
        this.enums = null;
        this.constant = null;
        this.minimum = null;
        this.maximum = null;
        this.exclusiveMinimum = null;
        this.exclusiveMaximum = null;
        this.multipleOf = null;
        this.minLength = -1;
        this.maxLength = -1;
        this.pattern = null;
        this.items = null;
        this.minItems = -1;
        this.maxItems = -1;
        this.properties = null;
        this.required = null;
        this.additionalAllowed = true;
        this.additionalSchema = null;
        this.minProperties = -1;
        this.maxProperties = -1;
    }

    private boolean check(
        JsonParser parser,
        Event event)
    {
        if (deny)
        {
            return false;
        }

        boolean valid;
        switch (event)
        {
        case START_OBJECT:
            valid = checkObject(parser);
            break;
        case START_ARRAY:
            valid = checkArray(parser);
            break;
        case VALUE_STRING:
            valid = checkString(parser.getString());
            break;
        case VALUE_NUMBER:
            valid = checkNumber(parser);
            break;
        case VALUE_TRUE:
        case VALUE_FALSE:
            valid = (types == null || types.contains(JsonType.BOOLEAN)) && checkConstEnum(event, null, null);
            break;
        case VALUE_NULL:
            valid = (types == null || types.contains(JsonType.NULL)) && checkConstEnum(VALUE_NULL, null, null);
            break;
        default:
            valid = false;
            break;
        }
        return valid;
    }

    private boolean checkObject(
        JsonParser parser)
    {
        boolean valid = types == null || types.contains(JsonType.OBJECT);
        if (valid)
        {
            Set<String> seen = new HashSet<>();
            int count = 0;
            Event event = parser.next();
            while (valid && event != END_OBJECT)
            {
                String key = parser.getString();
                count++;
                seen.add(key);
                JsonSchema child = childFor(key);
                Event value = parser.next();
                valid = child != null && child.check(parser, value);
                if (valid)
                {
                    event = parser.next();
                }
            }
            valid = valid &&
                (required == null || seen.containsAll(required)) &&
                (minProperties < 0 || count >= minProperties) &&
                (maxProperties < 0 || count <= maxProperties);
        }
        return valid;
    }

    private boolean checkArray(
        JsonParser parser)
    {
        boolean valid = types == null || types.contains(JsonType.ARRAY);
        if (valid)
        {
            JsonSchema child = items != null ? items : ANY;
            int count = 0;
            Event event = parser.next();
            while (valid && event != END_ARRAY)
            {
                count++;
                valid = child.check(parser, event);
                if (valid)
                {
                    event = parser.next();
                }
            }
            valid = valid &&
                (minItems < 0 || count >= minItems) &&
                (maxItems < 0 || count <= maxItems);
        }
        return valid;
    }

    private boolean checkString(
        String value)
    {
        int length = value.codePointCount(0, value.length());
        return (types == null || types.contains(JsonType.STRING)) &&
            checkConstEnum(VALUE_STRING, value, null) &&
            (minLength < 0 || length >= minLength) &&
            (maxLength < 0 || length <= maxLength) &&
            (pattern == null || pattern.matcher(value).find());
    }

    private boolean checkNumber(
        JsonParser parser)
    {
        boolean integral = parser.isIntegralNumber();
        BigDecimal value = parser.getBigDecimal();
        boolean typeOk = types == null ||
            types.contains(JsonType.NUMBER) ||
            integral && types.contains(JsonType.INTEGER);
        return typeOk &&
            checkConstEnum(VALUE_NUMBER, null, value) &&
            (minimum == null || value.compareTo(minimum) >= 0) &&
            (maximum == null || value.compareTo(maximum) <= 0) &&
            (exclusiveMinimum == null || value.compareTo(exclusiveMinimum) > 0) &&
            (exclusiveMaximum == null || value.compareTo(exclusiveMaximum) < 0) &&
            (multipleOf == null || multipleOf.signum() != 0 && value.remainder(multipleOf).signum() == 0);
    }

    private boolean checkConstEnum(
        Event event,
        String text,
        BigDecimal number)
    {
        boolean valid = constant == null || scalarEquals(constant, event, text, number);
        if (valid && enums != null)
        {
            valid = false;
            for (JsonValue candidate : enums)
            {
                if (scalarEquals(candidate, event, text, number))
                {
                    valid = true;
                    break;
                }
            }
        }
        return valid;
    }

    private JsonSchema childFor(
        String key)
    {
        JsonSchema child = properties != null ? properties.get(key) : null;
        if (child == null)
        {
            child = additionalSchema != null ? additionalSchema : additionalAllowed ? ANY : null;
        }
        return child;
    }

    private static boolean scalarEquals(
        JsonValue expected,
        Event event,
        String text,
        BigDecimal number)
    {
        boolean result;
        switch (expected.getValueType())
        {
        case STRING:
            result = event == VALUE_STRING && ((JsonString) expected).getString().equals(text);
            break;
        case NUMBER:
            result = event == VALUE_NUMBER && number != null && number.compareTo(((JsonNumber) expected).bigDecimalValue()) == 0;
            break;
        case TRUE:
            result = event == VALUE_TRUE;
            break;
        case FALSE:
            result = event == VALUE_FALSE;
            break;
        case NULL:
            result = event == VALUE_NULL;
            break;
        default:
            result = false;
            break;
        }
        return result;
    }

    private static JsonSchema from(
        JsonValue value)
    {
        JsonSchema result;
        switch (value.getValueType())
        {
        case OBJECT:
            result = new JsonSchema(value.asJsonObject());
            break;
        case FALSE:
            result = NONE;
            break;
        default:
            result = ANY;
            break;
        }
        return result;
    }

    private static Set<JsonType> parseTypes(
        JsonValue value)
    {
        Set<JsonType> result;
        if (value == null)
        {
            result = null;
        }
        else if (value.getValueType() == JsonValue.ValueType.ARRAY)
        {
            result = EnumSet.noneOf(JsonType.class);
            for (JsonValue name : value.asJsonArray())
            {
                result.add(toType(((JsonString) name).getString()));
            }
        }
        else
        {
            result = EnumSet.of(toType(((JsonString) value).getString()));
        }
        return result;
    }

    private static JsonType toType(
        String name)
    {
        JsonType result;
        switch (name)
        {
        case "object":
            result = JsonType.OBJECT;
            break;
        case "array":
            result = JsonType.ARRAY;
            break;
        case "string":
            result = JsonType.STRING;
            break;
        case "number":
            result = JsonType.NUMBER;
            break;
        case "integer":
            result = JsonType.INTEGER;
            break;
        case "boolean":
            result = JsonType.BOOLEAN;
            break;
        case "null":
            result = JsonType.NULL;
            break;
        default:
            throw new IllegalArgumentException("unknown JSON Schema type: " + name);
        }
        return result;
    }

    private static List<JsonValue> parseEnum(
        JsonValue value)
    {
        List<JsonValue> result = null;
        if (value != null)
        {
            result = new ArrayList<>();
            for (JsonValue candidate : value.asJsonArray())
            {
                result.add(requireScalar(candidate));
            }
        }
        return result;
    }

    private static JsonValue parseConst(
        JsonValue value)
    {
        return value != null ? requireScalar(value) : null;
    }

    private static JsonValue requireScalar(
        JsonValue value)
    {
        JsonValue.ValueType type = value.getValueType();
        if (type == JsonValue.ValueType.OBJECT || type == JsonValue.ValueType.ARRAY)
        {
            throw new UnsupportedOperationException("JSON Schema structural enum/const not yet supported");
        }
        return value;
    }

    private static Map<String, JsonSchema> parseProperties(
        JsonValue value)
    {
        Map<String, JsonSchema> result = null;
        if (value != null)
        {
            result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonValue> entry : value.asJsonObject().entrySet())
            {
                result.put(entry.getKey(), from(entry.getValue()));
            }
        }
        return result;
    }

    private static Set<String> parseRequired(
        JsonValue value)
    {
        Set<String> result = null;
        if (value != null)
        {
            result = new HashSet<>();
            for (JsonValue name : value.asJsonArray())
            {
                result.add(((JsonString) name).getString());
            }
        }
        return result;
    }

    private static BigDecimal number(
        JsonObject schema,
        String key)
    {
        return schema.containsKey(key) ? schema.getJsonNumber(key).bigDecimalValue() : null;
    }

    private static int integer(
        JsonObject schema,
        String key)
    {
        return schema.containsKey(key) ? schema.getJsonNumber(key).intValue() : -1;
    }
}
