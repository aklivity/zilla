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
import static jakarta.json.stream.JsonParser.Event.KEY_NAME;
import static jakarta.json.stream.JsonParser.Event.START_ARRAY;
import static jakarta.json.stream.JsonParser.Event.START_OBJECT;
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

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * An immutable, compiled JSON Schema (draft-07 subset) that validates an instance by
 * consuming a streaming {@link JsonParser} event stream without materializing a DOM.
 * <p>
 * Compile once per schema and reuse for the lifetime of the binding; {@link
 * #validate(JsonParser)} creates a per-call evaluator and may be called repeatedly on the
 * owning worker thread.
 * <p>
 * Validation is event-driven and push-based: each event is fed to every live evaluator, so
 * combinators evaluate their candidate subschemas concurrently over the single stream without
 * buffering. Supported keywords: {@code type} (including {@code integer}), {@code enum} and
 * {@code const} (scalar values), {@code minimum}/{@code maximum}/{@code exclusiveMinimum}/{@code
 * exclusiveMaximum}/{@code multipleOf}, {@code minLength}/{@code maxLength}/{@code pattern},
 * {@code items} (single schema), {@code minItems}/{@code maxItems}, {@code properties}, {@code
 * required}, {@code additionalProperties} (boolean or schema), {@code minProperties}/{@code
 * maxProperties}, and the combinators {@code allOf}/{@code anyOf}/{@code oneOf}/{@code not}/{@code
 * if}/{@code then}/{@code else}. Unsupported assertion keywords ({@code $ref}, {@code
 * patternProperties}, etc.) fail fast at compile time.
 */
public final class JsonSchema
{
    private static final JsonSchema ANY = new JsonSchema(false);
    private static final JsonSchema NONE = new JsonSchema(true);

    private static final List<String> UNSUPPORTED = List.of(
        "$ref", "dependentRequired", "dependentSchemas");

    private enum JsonType
    {
        OBJECT, ARRAY, STRING, NUMBER, INTEGER, BOOLEAN, NULL
    }

    private enum Verdict
    {
        VALID, INVALID, PENDING
    }

    private final boolean deny;
    private final Set<JsonType> types;
    private final List<JsonNode> enums;
    private final JsonNode constant;
    private final BigDecimal minimum;
    private final BigDecimal maximum;
    private final BigDecimal exclusiveMinimum;
    private final BigDecimal exclusiveMaximum;
    private final BigDecimal multipleOf;
    private final int minLength;
    private final int maxLength;
    private final Pattern pattern;
    private final JsonSchema items;
    private final List<JsonSchema> itemsTuple;
    private final JsonSchema additionalItems;
    private final JsonSchema contains;
    private final boolean uniqueItems;
    private final int minItems;
    private final int maxItems;
    private final Map<String, JsonSchema> properties;
    private final Set<String> required;
    private final boolean additionalAllowed;
    private final JsonSchema additionalSchema;
    private final int minProperties;
    private final int maxProperties;
    private final Map<Pattern, JsonSchema> patternProperties;
    private final JsonSchema propertyNames;
    private final Map<String, Set<String>> dependentRequired;
    private final Map<String, JsonSchema> dependentSchemas;
    private final List<JsonSchema> allOf;
    private final List<JsonSchema> anyOf;
    private final List<JsonSchema> oneOf;
    private final JsonSchema notSchema;
    private final JsonSchema ifSchema;
    private final JsonSchema thenSchema;
    private final JsonSchema elseSchema;

    public static JsonSchema of(
        String schema)
    {
        return new JsonSchema(JsonNode.parse(schema));
    }

    public boolean validate(
        JsonParser parser)
    {
        Eval eval = new Eval();
        Verdict verdict = Verdict.PENDING;
        while (parser.hasNext() && verdict == Verdict.PENDING)
        {
            verdict = eval.feed(parser.next(), parser);
        }
        return verdict == Verdict.VALID;
    }

    private JsonSchema(
        JsonNode schema)
    {
        for (String keyword : UNSUPPORTED)
        {
            if (schema.has(keyword))
            {
                throw new UnsupportedOperationException("JSON Schema keyword not yet supported: " + keyword);
            }
        }

        JsonNode itemsValue = schema.get("items");
        boolean tupleItems = itemsValue != null && itemsValue.isArray();

        JsonNode additional = schema.get("additionalProperties");
        boolean additionalAllowed = additional == null || !additional.isFalse();
        JsonSchema additionalSchema = additional != null && additional.isObject()
            ? new JsonSchema(additional)
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
        this.pattern = schema.has("pattern") ? Pattern.compile(schema.get("pattern").string()) : null;
        this.items = itemsValue != null && !tupleItems ? from(itemsValue) : null;
        this.itemsTuple = tupleItems ? parseSchemaArray(itemsValue) : null;
        this.additionalItems = schema.has("additionalItems") ? from(schema.get("additionalItems")) : null;
        this.contains = schema.has("contains") ? from(schema.get("contains")) : null;
        this.uniqueItems = schema.has("uniqueItems") && schema.get("uniqueItems").isTrue();
        this.minItems = integer(schema, "minItems");
        this.maxItems = integer(schema, "maxItems");
        this.properties = parseProperties(schema.get("properties"));
        this.required = parseRequired(schema.get("required"));
        this.additionalAllowed = additionalAllowed;
        this.additionalSchema = additionalSchema;
        this.minProperties = integer(schema, "minProperties");
        this.maxProperties = integer(schema, "maxProperties");
        this.patternProperties = parsePatternProperties(schema.get("patternProperties"));
        this.propertyNames = schema.has("propertyNames") ? from(schema.get("propertyNames")) : null;
        this.dependentRequired = parseDependentRequired(schema.get("dependencies"));
        this.dependentSchemas = parseDependentSchemas(schema.get("dependencies"));
        this.allOf = parseSchemaArray(schema.get("allOf"));
        this.anyOf = parseSchemaArray(schema.get("anyOf"));
        this.oneOf = parseSchemaArray(schema.get("oneOf"));
        this.notSchema = schema.has("not") ? from(schema.get("not")) : null;
        this.ifSchema = schema.has("if") ? from(schema.get("if")) : null;
        this.thenSchema = schema.has("then") ? from(schema.get("then")) : null;
        this.elseSchema = schema.has("else") ? from(schema.get("else")) : null;
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
        this.itemsTuple = null;
        this.additionalItems = null;
        this.contains = null;
        this.uniqueItems = false;
        this.minItems = -1;
        this.maxItems = -1;
        this.properties = null;
        this.required = null;
        this.additionalAllowed = true;
        this.additionalSchema = null;
        this.minProperties = -1;
        this.maxProperties = -1;
        this.patternProperties = null;
        this.propertyNames = null;
        this.dependentRequired = null;
        this.dependentSchemas = null;
        this.allOf = null;
        this.anyOf = null;
        this.oneOf = null;
        this.notSchema = null;
        this.ifSchema = null;
        this.thenSchema = null;
        this.elseSchema = null;
    }

    private Eval eval()
    {
        return new Eval();
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
            for (JsonNode candidate : enums)
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

    private static Map<Pattern, JsonSchema> parsePatternProperties(
        JsonNode value)
    {
        Map<Pattern, JsonSchema> result = null;
        if (value != null)
        {
            result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : value.members().entrySet())
            {
                result.put(Pattern.compile(entry.getKey()), from(entry.getValue()));
            }
        }
        return result;
    }

    private static Map<String, Set<String>> parseDependentRequired(
        JsonNode value)
    {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        if (value != null)
        {
            for (Map.Entry<String, JsonNode> entry : value.members().entrySet())
            {
                if (entry.getValue().isArray())
                {
                    Set<String> names = new HashSet<>();
                    for (JsonNode name : entry.getValue().elements())
                    {
                        names.add(name.string());
                    }
                    result.put(entry.getKey(), names);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static Map<String, JsonSchema> parseDependentSchemas(
        JsonNode value)
    {
        Map<String, JsonSchema> result = new LinkedHashMap<>();
        if (value != null)
        {
            for (Map.Entry<String, JsonNode> entry : value.members().entrySet())
            {
                if (!entry.getValue().isArray())
                {
                    result.put(entry.getKey(), from(entry.getValue()));
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static boolean scalarEquals(
        JsonNode expected,
        Event event,
        String text,
        BigDecimal number)
    {
        boolean result;
        switch (expected.kind())
        {
        case STRING:
            result = event == VALUE_STRING && expected.string().equals(text);
            break;
        case NUMBER:
            result = event == VALUE_NUMBER && number != null && number.compareTo(expected.number()) == 0;
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
        JsonNode value)
    {
        JsonSchema result;
        switch (value.kind())
        {
        case OBJECT:
            result = new JsonSchema(value);
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
        JsonNode value)
    {
        Set<JsonType> result;
        if (value == null)
        {
            result = null;
        }
        else if (value.isArray())
        {
            result = EnumSet.noneOf(JsonType.class);
            for (JsonNode name : value.elements())
            {
                result.add(toType(name.string()));
            }
        }
        else
        {
            result = EnumSet.of(toType(value.string()));
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

    private static List<JsonNode> parseEnum(
        JsonNode value)
    {
        List<JsonNode> result = null;
        if (value != null)
        {
            result = new ArrayList<>();
            for (JsonNode candidate : value.elements())
            {
                result.add(requireScalar(candidate));
            }
        }
        return result;
    }

    private static JsonNode parseConst(
        JsonNode value)
    {
        return value != null ? requireScalar(value) : null;
    }

    private static JsonNode requireScalar(
        JsonNode value)
    {
        if (value.isStructural())
        {
            throw new UnsupportedOperationException("JSON Schema structural enum/const not yet supported");
        }
        return value;
    }

    private static Map<String, JsonSchema> parseProperties(
        JsonNode value)
    {
        Map<String, JsonSchema> result = null;
        if (value != null)
        {
            result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : value.members().entrySet())
            {
                result.put(entry.getKey(), from(entry.getValue()));
            }
        }
        return result;
    }

    private static Set<String> parseRequired(
        JsonNode value)
    {
        Set<String> result = null;
        if (value != null)
        {
            result = new HashSet<>();
            for (JsonNode name : value.elements())
            {
                result.add(name.string());
            }
        }
        return result;
    }

    private static List<JsonSchema> parseSchemaArray(
        JsonNode value)
    {
        List<JsonSchema> result = null;
        if (value != null)
        {
            result = new ArrayList<>();
            for (JsonNode element : value.elements())
            {
                result.add(from(element));
            }
        }
        return result;
    }

    private static BigDecimal number(
        JsonNode schema,
        String key)
    {
        return schema.has(key) ? schema.get(key).number() : null;
    }

    private static int integer(
        JsonNode schema,
        String key)
    {
        return schema.has(key) ? schema.get(key).integer() : -1;
    }

    private static String tokenText(
        Event event,
        JsonParser parser)
    {
        return event == KEY_NAME || event == VALUE_STRING || event == VALUE_NUMBER ? parser.getString() : null;
    }

    private static String canonicalize(
        List<Token> tokens,
        int[] position)
    {
        Token token = tokens.get(position[0]++);
        String result;
        switch (token.event)
        {
        case START_OBJECT:
        {
            List<String> members = new ArrayList<>();
            while (tokens.get(position[0]).event != END_OBJECT)
            {
                String key = tokens.get(position[0]++).text;
                members.add(quote(key) + ":" + canonicalize(tokens, position));
            }
            position[0]++;
            members.sort(null);
            result = "{" + String.join(",", members) + "}";
            break;
        }
        case START_ARRAY:
        {
            List<String> elements = new ArrayList<>();
            while (tokens.get(position[0]).event != END_ARRAY)
            {
                elements.add(canonicalize(tokens, position));
            }
            position[0]++;
            result = "[" + String.join(",", elements) + "]";
            break;
        }
        case VALUE_STRING:
            result = quote(token.text);
            break;
        case VALUE_NUMBER:
            result = normalizeNumber(token.text);
            break;
        case VALUE_TRUE:
            result = "true";
            break;
        case VALUE_FALSE:
            result = "false";
            break;
        default:
            result = "null";
            break;
        }
        return result;
    }

    private static String quote(
        String value)
    {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String normalizeNumber(
        String text)
    {
        BigDecimal value = new BigDecimal(text);
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static final class Token
    {
        private final Event event;
        private final String text;

        private Token(
            Event event,
            String text)
        {
            this.event = event;
            this.text = text;
        }
    }

    private final class Eval
    {
        private final Eval[] allOfEvals;
        private final Eval[] anyOfEvals;
        private final Eval[] oneOfEvals;
        private final Eval notEval;
        private final Eval ifEval;
        private final Eval thenEval;
        private final Eval elseEval;
        private final Map<String, Eval> dependentSchemaEvals;

        private boolean started;
        private boolean done;
        private Verdict result;
        private int depth;
        private boolean directInvalid;
        private boolean object;
        private boolean array;
        private Set<String> seen;
        private int count;
        private Eval[] directChildren;
        private Eval containsChild;
        private int containsMatched;
        private Set<String> uniqueSeen;
        private List<Token> uniqueTokens;

        private Eval()
        {
            this.allOfEvals = evalsOf(allOf);
            this.anyOfEvals = evalsOf(anyOf);
            this.oneOfEvals = evalsOf(oneOf);
            this.notEval = notSchema != null ? notSchema.eval() : null;
            this.ifEval = ifSchema != null ? ifSchema.eval() : null;
            this.thenEval = thenSchema != null ? thenSchema.eval() : null;
            this.elseEval = elseSchema != null ? elseSchema.eval() : null;
            this.dependentSchemaEvals = evalsOfMap(dependentSchemas);
        }

        private Verdict feed(
            Event event,
            JsonParser parser)
        {
            Verdict verdict;
            if (done)
            {
                verdict = result;
            }
            else
            {
                directFeed(event, parser);
                feedCombinators(event, parser);
                if (event == START_OBJECT || event == START_ARRAY)
                {
                    depth++;
                }
                else if (event == END_OBJECT || event == END_ARRAY)
                {
                    depth--;
                }
                started = true;
                if (depth == 0)
                {
                    done = true;
                    result = combine();
                    verdict = result;
                }
                else
                {
                    verdict = Verdict.PENDING;
                }
            }
            return verdict;
        }

        private void directFeed(
            Event event,
            JsonParser parser)
        {
            if (started)
            {
                onInner(event, parser);
            }
            else
            {
                onOpen(event, parser);
            }
        }

        private void onOpen(
            Event event,
            JsonParser parser)
        {
            if (deny)
            {
                directInvalid = true;
            }
            switch (event)
            {
            case START_OBJECT:
                object = true;
                seen = new HashSet<>();
                directInvalid |= types != null && !types.contains(JsonType.OBJECT);
                break;
            case START_ARRAY:
                array = true;
                directInvalid |= types != null && !types.contains(JsonType.ARRAY);
                break;
            case VALUE_STRING:
                directInvalid |= !checkString(parser.getString());
                break;
            case VALUE_NUMBER:
                directInvalid |= !checkNumber(parser);
                break;
            case VALUE_TRUE:
            case VALUE_FALSE:
                directInvalid |= !((types == null || types.contains(JsonType.BOOLEAN)) && checkConstEnum(event, null, null));
                break;
            case VALUE_NULL:
                directInvalid |= !((types == null || types.contains(JsonType.NULL)) && checkConstEnum(VALUE_NULL, null, null));
                break;
            default:
                directInvalid = true;
                break;
            }
        }

        private void onInner(
            Event event,
            JsonParser parser)
        {
            if (directChildren != null)
            {
                routeChildren(event, parser);
            }
            else if (object)
            {
                onObjectInner(event, parser);
            }
            else if (array)
            {
                onArrayInner(event, parser);
            }
        }

        private void onObjectInner(
            Event event,
            JsonParser parser)
        {
            if (event == END_OBJECT)
            {
                directInvalid |= required != null && !seen.containsAll(required) ||
                    minProperties >= 0 && count < minProperties ||
                    maxProperties >= 0 && count > maxProperties;
            }
            else
            {
                String key = parser.getString();
                count++;
                seen.add(key);
                if (propertyNames != null)
                {
                    directInvalid |= propertyNames.eval().feed(VALUE_STRING, parser) != Verdict.VALID;
                }
                directChildren = applicableFor(key);
            }
        }

        private void onArrayInner(
            Event event,
            JsonParser parser)
        {
            if (event == END_ARRAY)
            {
                directInvalid |= minItems >= 0 && count < minItems ||
                    maxItems >= 0 && count > maxItems ||
                    contains != null && containsMatched == 0;
            }
            else
            {
                int index = count;
                count++;
                directChildren = new Eval[] {elementSchema(index).eval()};
                if (contains != null)
                {
                    containsChild = contains.eval();
                }
                if (uniqueItems)
                {
                    if (uniqueSeen == null)
                    {
                        uniqueSeen = new HashSet<>();
                    }
                    uniqueTokens = new ArrayList<>();
                }
                routeChildren(event, parser);
            }
        }

        private JsonSchema elementSchema(
            int index)
        {
            JsonSchema schema;
            if (items != null)
            {
                schema = items;
            }
            else if (itemsTuple != null)
            {
                schema = index < itemsTuple.size()
                    ? itemsTuple.get(index)
                    : additionalItems != null ? additionalItems : ANY;
            }
            else
            {
                schema = ANY;
            }
            return schema;
        }

        private void routeChildren(
            Event event,
            JsonParser parser)
        {
            if (uniqueTokens != null)
            {
                uniqueTokens.add(new Token(event, tokenText(event, parser)));
            }
            boolean complete = false;
            for (Eval child : directChildren)
            {
                Verdict verdict = child.feed(event, parser);
                if (verdict != Verdict.PENDING)
                {
                    directInvalid |= verdict == Verdict.INVALID;
                    complete = true;
                }
            }
            if (containsChild != null)
            {
                Verdict verdict = containsChild.feed(event, parser);
                if (verdict != Verdict.PENDING)
                {
                    if (verdict == Verdict.VALID)
                    {
                        containsMatched++;
                    }
                    containsChild = null;
                }
            }
            if (complete)
            {
                directChildren = null;
                if (uniqueTokens != null)
                {
                    directInvalid |= !uniqueSeen.add(canonicalize(uniqueTokens, new int[] {0}));
                    uniqueTokens = null;
                }
            }
        }

        private Eval[] applicableFor(
            String key)
        {
            List<JsonSchema> applicable = new ArrayList<>();
            boolean matched = false;
            if (properties != null && properties.containsKey(key))
            {
                applicable.add(properties.get(key));
                matched = true;
            }
            if (patternProperties != null)
            {
                for (Map.Entry<Pattern, JsonSchema> entry : patternProperties.entrySet())
                {
                    if (entry.getKey().matcher(key).find())
                    {
                        applicable.add(entry.getValue());
                        matched = true;
                    }
                }
            }
            if (!matched)
            {
                applicable.add(additionalSchema != null ? additionalSchema : additionalAllowed ? ANY : NONE);
            }
            return evalsOf(applicable);
        }

        private void feedCombinators(
            Event event,
            JsonParser parser)
        {
            feedAll(allOfEvals, event, parser);
            feedAll(anyOfEvals, event, parser);
            feedAll(oneOfEvals, event, parser);
            feedOne(notEval, event, parser);
            feedOne(ifEval, event, parser);
            feedOne(thenEval, event, parser);
            feedOne(elseEval, event, parser);
            if (dependentSchemaEvals != null)
            {
                for (Eval eval : dependentSchemaEvals.values())
                {
                    feedOne(eval, event, parser);
                }
            }
        }

        private void feedAll(
            Eval[] evals,
            Event event,
            JsonParser parser)
        {
            if (evals != null)
            {
                for (Eval eval : evals)
                {
                    feedOne(eval, event, parser);
                }
            }
        }

        private void feedOne(
            Eval eval,
            Event event,
            JsonParser parser)
        {
            if (eval != null && !eval.done)
            {
                eval.feed(event, parser);
            }
        }

        private Verdict combine()
        {
            boolean valid = !directInvalid;
            if (valid && allOfEvals != null)
            {
                for (Eval eval : allOfEvals)
                {
                    valid &= eval.result == Verdict.VALID;
                }
            }
            if (valid && anyOfEvals != null)
            {
                boolean any = false;
                for (Eval eval : anyOfEvals)
                {
                    any |= eval.result == Verdict.VALID;
                }
                valid = any;
            }
            if (valid && oneOfEvals != null)
            {
                int matched = 0;
                for (Eval eval : oneOfEvals)
                {
                    if (eval.result == Verdict.VALID)
                    {
                        matched++;
                    }
                }
                valid = matched == 1;
            }
            if (valid && notEval != null)
            {
                valid = notEval.result != Verdict.VALID;
            }
            if (valid && ifEval != null)
            {
                valid = ifEval.result == Verdict.VALID
                    ? thenEval == null || thenEval.result == Verdict.VALID
                    : elseEval == null || elseEval.result == Verdict.VALID;
            }
            if (valid && seen != null && dependentRequired != null)
            {
                for (Map.Entry<String, Set<String>> entry : dependentRequired.entrySet())
                {
                    if (seen.contains(entry.getKey()) && !seen.containsAll(entry.getValue()))
                    {
                        valid = false;
                        break;
                    }
                }
            }
            if (valid && seen != null && dependentSchemaEvals != null)
            {
                for (Map.Entry<String, Eval> entry : dependentSchemaEvals.entrySet())
                {
                    if (seen.contains(entry.getKey()) && entry.getValue().result != Verdict.VALID)
                    {
                        valid = false;
                        break;
                    }
                }
            }
            return valid ? Verdict.VALID : Verdict.INVALID;
        }

        private Eval[] evalsOf(
            List<JsonSchema> schemas)
        {
            Eval[] result = null;
            if (schemas != null)
            {
                result = new Eval[schemas.size()];
                for (int i = 0; i < schemas.size(); i++)
                {
                    result[i] = schemas.get(i).eval();
                }
            }
            return result;
        }

        private Map<String, Eval> evalsOfMap(
            Map<String, JsonSchema> schemas)
        {
            Map<String, Eval> result = null;
            if (schemas != null)
            {
                result = new LinkedHashMap<>();
                for (Map.Entry<String, JsonSchema> entry : schemas.entrySet())
                {
                    result.put(entry.getKey(), entry.getValue().eval());
                }
            }
            return result;
        }
    }
}
