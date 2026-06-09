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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import io.aklivity.zilla.runtime.common.json.JsonRefResolver;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchema.Draft;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;

/**
 * An immutable, compiled JSON Schema that validates an instance by consuming a streaming
 * {@link JsonParser} event stream without materializing a DOM. Compile once per schema and
 * reuse for the lifetime of the binding; {@link #validate(JsonParser)} and
 * {@link #validate(JsonParser, Consumer)} each create a per-call evaluator and may be called
 * repeatedly on the owning worker thread.
 * <p>
 * Validation is event-driven and push-based: each event is fed to every live evaluator, so
 * combinators evaluate their candidate subschemas concurrently over the single stream without
 * buffering. Supports drafts 04, 06, and 07; {@link Draft} selects the dialect and is otherwise
 * auto-detected from a top-level {@code $schema} URI (defaulting to draft-07).
 * <p>
 * Diagnostics, when requested, are produced at parity with leadpony justify's {@code Problem}
 * output: {@code "[line,col][pointer] message"}, with the failing keyword carried alongside the
 * instance JSON-Pointer.
 */
public final class JsonSchemaImpl implements JsonSchema
{
    private static final JsonSchemaImpl ANY = new JsonSchemaImpl(false);
    private static final JsonSchemaImpl NONE = new JsonSchemaImpl(true);

    private static final JsonRefResolver LOCAL_ONLY = ref ->
    {
        throw new UnsupportedOperationException("non-local $ref not resolvable: " + ref);
    };

    private static final List<String> UNSUPPORTED = List.of(
        "dependentRequired", "dependentSchemas");

    private enum JsonType
    {
        OBJECT, ARRAY, STRING, NUMBER, INTEGER, BOOLEAN, NULL
    }

    private enum Verdict
    {
        VALID, INVALID, PENDING
    }

    private final boolean deny;
    private final String ref;
    private final Context context;
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
    private final JsonSchemaImpl items;
    private final List<JsonSchemaImpl> itemsTuple;
    private final JsonSchemaImpl additionalItems;
    private final JsonSchemaImpl contains;
    private final boolean uniqueItems;
    private final int minItems;
    private final int maxItems;
    private final Map<String, JsonSchemaImpl> properties;
    private final Set<String> required;
    private final boolean additionalAllowed;
    private final JsonSchemaImpl additionalSchema;
    private final int minProperties;
    private final int maxProperties;
    private final Map<Pattern, JsonSchemaImpl> patternProperties;
    private final JsonSchemaImpl propertyNames;
    private final Map<String, Set<String>> dependentRequired;
    private final Map<String, JsonSchemaImpl> dependentSchemas;
    private final List<JsonSchemaImpl> allOf;
    private final List<JsonSchemaImpl> anyOf;
    private final List<JsonSchemaImpl> oneOf;
    private final JsonSchemaImpl notSchema;
    private final JsonSchemaImpl ifSchema;
    private final JsonSchemaImpl thenSchema;
    private final JsonSchemaImpl elseSchema;

    public static JsonSchema of(
        String schema)
    {
        return of(schema, LOCAL_ONLY, null);
    }

    public static JsonSchema of(
        String schema,
        JsonRefResolver resolver)
    {
        return of(schema, resolver, null);
    }

    public static JsonSchema of(
        String schema,
        Draft draft)
    {
        return of(schema, LOCAL_ONLY, draft);
    }

    public static JsonSchema of(
        String schema,
        JsonRefResolver resolver,
        Draft draft)
    {
        JsonNode root = JsonNode.parse(schema);
        Draft resolved = draft != null ? draft : detectDraft(root);
        return from(root, new Context(root, resolver, resolved));
    }

    public static Set<String> collectRefs(
        String schema)
    {
        Set<String> refs = new LinkedHashSet<>();
        collectRefs(JsonNode.parse(schema), refs);
        return refs;
    }

    @Override
    public boolean validate(
        JsonParser parser)
    {
        Eval eval = eval();
        Verdict verdict = Verdict.PENDING;
        while (parser.hasNext() && verdict == Verdict.PENDING)
        {
            verdict = eval.feed(parser.next(), parser);
        }
        return verdict == Verdict.VALID;
    }

    /**
     * Validates and reports each failing keyword + instance JSON-Pointer + message via
     * {@code reporter}. Returns {@code true} iff every diagnostic-bearing failure cleared.
     */
    @Override
    public boolean validate(
        JsonParser parser,
        Consumer<JsonSchemaDiagnostic> reporter)
    {
        Trace trace = new Trace(reporter);
        Eval eval = eval(trace);
        Verdict verdict = Verdict.PENDING;
        while (parser.hasNext() && verdict == Verdict.PENDING)
        {
            verdict = eval.feed(parser.next(), parser);
        }
        return verdict == Verdict.VALID;
    }

    private JsonSchemaImpl(
        JsonNode schema,
        Context context)
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
        JsonSchemaImpl additionalSchema = additional != null && additional.isObject()
            ? from(additional, context)
            : null;

        this.deny = false;
        this.ref = null;
        this.context = context;
        this.types = parseTypes(schema.get("type"));
        this.enums = parseEnum(schema.get("enum"));
        this.constant = parseConst(schema.get("const"));
        BigDecimal minimumValue = number(schema, "minimum");
        BigDecimal maximumValue = number(schema, "maximum");
        BigDecimal exclusiveMin = exclusiveBound(schema, "exclusiveMinimum", minimumValue, context.draft);
        BigDecimal exclusiveMax = exclusiveBound(schema, "exclusiveMaximum", maximumValue, context.draft);
        boolean minSuppressed = exclusiveMin != null && context.draft == Draft.DRAFT_04 &&
            schema.has("exclusiveMinimum") && schema.get("exclusiveMinimum").isTrue();
        boolean maxSuppressed = exclusiveMax != null && context.draft == Draft.DRAFT_04 &&
            schema.has("exclusiveMaximum") && schema.get("exclusiveMaximum").isTrue();
        this.minimum = minSuppressed ? null : minimumValue;
        this.maximum = maxSuppressed ? null : maximumValue;
        this.exclusiveMinimum = exclusiveMin;
        this.exclusiveMaximum = exclusiveMax;
        this.multipleOf = number(schema, "multipleOf");
        this.minLength = integer(schema, "minLength");
        this.maxLength = integer(schema, "maxLength");
        this.pattern = schema.has("pattern") ? Pattern.compile(schema.get("pattern").string()) : null;
        this.items = itemsValue != null && !tupleItems ? from(itemsValue, context) : null;
        this.itemsTuple = tupleItems ? parseSchemaArray(itemsValue, context) : null;
        this.additionalItems = schema.has("additionalItems") ? from(schema.get("additionalItems"), context) : null;
        this.contains = schema.has("contains") ? from(schema.get("contains"), context) : null;
        this.uniqueItems = schema.has("uniqueItems") && schema.get("uniqueItems").isTrue();
        this.minItems = integer(schema, "minItems");
        this.maxItems = integer(schema, "maxItems");
        this.properties = parseProperties(schema.get("properties"), context);
        this.required = parseRequired(schema.get("required"));
        this.additionalAllowed = additionalAllowed;
        this.additionalSchema = additionalSchema;
        this.minProperties = integer(schema, "minProperties");
        this.maxProperties = integer(schema, "maxProperties");
        this.patternProperties = parsePatternProperties(schema.get("patternProperties"), context);
        this.propertyNames = schema.has("propertyNames") ? from(schema.get("propertyNames"), context) : null;
        this.dependentRequired = parseDependentRequired(schema.get("dependencies"));
        this.dependentSchemas = parseDependentSchemas(schema.get("dependencies"), context);
        this.allOf = parseSchemaArray(schema.get("allOf"), context);
        this.anyOf = parseSchemaArray(schema.get("anyOf"), context);
        this.oneOf = parseSchemaArray(schema.get("oneOf"), context);
        this.notSchema = schema.has("not") ? from(schema.get("not"), context) : null;
        this.ifSchema = schema.has("if") ? from(schema.get("if"), context) : null;
        this.thenSchema = schema.has("then") ? from(schema.get("then"), context) : null;
        this.elseSchema = schema.has("else") ? from(schema.get("else"), context) : null;
    }

    private JsonSchemaImpl(
        String ref,
        Context context)
    {
        this.deny = false;
        this.ref = ref;
        this.context = context;
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

    private JsonSchemaImpl(
        boolean deny)
    {
        this.deny = deny;
        this.ref = null;
        this.context = null;
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
        return eval(Trace.NONE);
    }

    private Eval eval(
        Trace trace)
    {
        JsonSchemaImpl schema = this;
        Set<String> visited = null;
        while (schema.ref != null)
        {
            if (visited == null)
            {
                visited = new HashSet<>();
            }
            if (!visited.add(schema.ref))
            {
                throw new IllegalStateException("cyclic $ref: " + schema.ref);
            }
            schema = schema.context.resolve(schema.ref);
        }
        return schema.evalDirect(trace);
    }

    private Eval evalDirect(
        Trace trace)
    {
        return new Eval(trace);
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

    private static Map<Pattern, JsonSchemaImpl> parsePatternProperties(
        JsonNode value,
        Context context)
    {
        Map<Pattern, JsonSchemaImpl> result = null;
        if (value != null)
        {
            result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : value.members().entrySet())
            {
                result.put(Pattern.compile(entry.getKey()), from(entry.getValue(), context));
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

    private static Map<String, JsonSchemaImpl> parseDependentSchemas(
        JsonNode value,
        Context context)
    {
        Map<String, JsonSchemaImpl> result = new LinkedHashMap<>();
        if (value != null)
        {
            for (Map.Entry<String, JsonNode> entry : value.members().entrySet())
            {
                if (!entry.getValue().isArray())
                {
                    result.put(entry.getKey(), from(entry.getValue(), context));
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

    private static JsonSchemaImpl from(
        JsonNode value,
        Context context)
    {
        JsonSchemaImpl result;
        if (value.isObject() && value.has("$ref"))
        {
            result = new JsonSchemaImpl(value.get("$ref").string(), context);
        }
        else
        {
            switch (value.kind())
            {
            case OBJECT:
                result = new JsonSchemaImpl(value, context);
                break;
            case FALSE:
                result = NONE;
                break;
            default:
                result = ANY;
                break;
            }
        }
        return result;
    }

    private static void collectRefs(
        JsonNode node,
        Set<String> refs)
    {
        if (node.isObject())
        {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isString())
            {
                refs.add(ref.string());
            }
            for (JsonNode member : node.members().values())
            {
                collectRefs(member, refs);
            }
        }
        else if (node.isArray())
        {
            for (JsonNode element : node.elements())
            {
                collectRefs(element, refs);
            }
        }
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

    private static Map<String, JsonSchemaImpl> parseProperties(
        JsonNode value,
        Context context)
    {
        Map<String, JsonSchemaImpl> result = null;
        if (value != null)
        {
            result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : value.members().entrySet())
            {
                result.put(entry.getKey(), from(entry.getValue(), context));
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

    private static List<JsonSchemaImpl> parseSchemaArray(
        JsonNode value,
        Context context)
    {
        List<JsonSchemaImpl> result = null;
        if (value != null)
        {
            result = new ArrayList<>();
            for (JsonNode element : value.elements())
            {
                result.add(from(element, context));
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

    private static BigDecimal exclusiveBound(
        JsonNode schema,
        String key,
        BigDecimal sibling,
        Draft draft)
    {
        BigDecimal result;
        if (!schema.has(key))
        {
            result = null;
        }
        else
        {
            JsonNode value = schema.get(key);
            if (draft == Draft.DRAFT_04)
            {
                if (value.isTrue())
                {
                    result = sibling;
                }
                else if (value.isFalse())
                {
                    result = null;
                }
                else
                {
                    throw new UnsupportedOperationException(
                        "draft-04 " + key + " must be boolean (paired with sibling minimum/maximum)");
                }
            }
            else if (value.kind() == JsonNode.Kind.NUMBER)
            {
                result = value.number();
            }
            else
            {
                throw new UnsupportedOperationException(
                    "draft-06+ " + key + " must be numeric");
            }
        }
        return result;
    }

    private static Draft detectDraft(
        JsonNode root)
    {
        Draft draft = Draft.DRAFT_07;
        if (root.isObject() && root.has("$schema"))
        {
            JsonNode value = root.get("$schema");
            if (value.isString())
            {
                String text = value.string();
                if (text.contains("draft-04"))
                {
                    draft = Draft.DRAFT_04;
                }
                else if (text.contains("draft-06"))
                {
                    draft = Draft.DRAFT_06;
                }
            }
        }
        return draft;
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

    private static final class Context
    {
        private final JsonNode root;
        private final JsonRefResolver resolver;
        private final Map<String, JsonSchemaImpl> cache;
        private final Draft draft;

        private Context(
            JsonNode root,
            JsonRefResolver resolver,
            Draft draft)
        {
            this.root = root;
            this.resolver = resolver;
            this.cache = new HashMap<>();
            this.draft = draft;
        }

        private JsonSchemaImpl resolve(
            String ref)
        {
            JsonSchemaImpl schema = cache.get(ref);
            if (schema == null)
            {
                schema = from(target(ref), this);
                cache.put(ref, schema);
            }
            return schema;
        }

        private JsonNode target(
            String ref)
        {
            JsonNode node;
            if (ref.startsWith("#"))
            {
                node = root;
                String pointer = ref.substring(1);
                if (!pointer.isEmpty())
                {
                    for (String segment : pointer.substring(1).split("/", -1))
                    {
                        String key = segment.replace("~1", "/").replace("~0", "~");
                        node = node.isArray() ? node.elements().get(Integer.parseInt(key)) : node.get(key);
                        if (node == null)
                        {
                            throw new IllegalArgumentException("unresolved $ref: " + ref);
                        }
                    }
                }
            }
            else
            {
                String text = resolver.resolve(ref);
                if (text == null)
                {
                    throw new IllegalArgumentException("unresolved $ref: " + ref);
                }
                node = JsonNode.parse(text);
            }
            return node;
        }
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

    private static final class Trace
    {
        private static final Trace NONE = new Trace(null);

        private final Consumer<JsonSchemaDiagnostic> reporter;
        private final StringBuilder pointer;

        private Trace(
            Consumer<JsonSchemaDiagnostic> reporter)
        {
            this.reporter = reporter;
            this.pointer = reporter != null ? new StringBuilder() : null;
        }

        private boolean active()
        {
            return reporter != null;
        }

        private int push(
            String segment)
        {
            int mark = 0;
            if (active())
            {
                mark = pointer.length();
                pointer.append('/').append(escape(segment));
            }
            return mark;
        }

        private int push(
            int index)
        {
            int mark = 0;
            if (active())
            {
                mark = pointer.length();
                pointer.append('/').append(index);
            }
            return mark;
        }

        private void pop(
            int mark)
        {
            if (active())
            {
                pointer.setLength(mark);
            }
        }

        private void report(
            String keyword,
            String message,
            JsonParser parser)
        {
            if (active())
            {
                JsonLocation location = parser.getLocation();
                reporter.accept(new JsonSchemaDiagnostic(
                    location.getLineNumber(),
                    location.getColumnNumber(),
                    pointer.toString(),
                    keyword,
                    message));
            }
        }

        private static String escape(
            String segment)
        {
            return segment.replace("~", "~0").replace("/", "~1");
        }
    }

    private final class Eval
    {
        private final Trace trace;
        private final Eval[] allOfEvals;
        private final Eval[] anyOfEvals;
        private final Eval[] oneOfEvals;
        private final Eval notEval;
        private final Eval ifEval;
        private final Eval thenEval;
        private final Eval elseEval;
        private final Map<String, Eval> dependentSchemaEvals;
        private final boolean trackKeys;

        private boolean started;
        private boolean done;
        private Verdict result;
        private int depth;
        private boolean directInvalid;
        private boolean object;
        private boolean array;
        private Set<String> seen;
        private int count;
        private Eval directChild;
        private int directChildMark;
        private Eval[] directChildren;
        private Eval containsChild;
        private int containsMatched;
        private Set<String> uniqueSeen;
        private List<Token> uniqueTokens;

        private Eval(
            Trace trace)
        {
            this.trace = trace;
            this.allOfEvals = evalsOf(allOf, trace);
            this.anyOfEvals = evalsOf(anyOf, trace);
            this.oneOfEvals = evalsOf(oneOf, trace);
            this.notEval = notSchema != null ? notSchema.eval(trace) : null;
            this.ifEval = ifSchema != null ? ifSchema.eval(trace) : null;
            this.thenEval = thenSchema != null ? thenSchema.eval(trace) : null;
            this.elseEval = elseSchema != null ? elseSchema.eval(trace) : null;
            this.dependentSchemaEvals = evalsOfMap(dependentSchemas, trace);
            this.trackKeys = required != null || dependentRequired != null || dependentSchemaEvals != null;
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
                    result = combine(parser);
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
            if (JsonSchemaImpl.this != ANY)
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
        }

        private void onOpen(
            Event event,
            JsonParser parser)
        {
            if (deny)
            {
                directInvalid = true;
                trace.report("false", "subschema disallows all instances", parser);
            }
            switch (event)
            {
            case START_OBJECT:
                object = true;
                seen = trackKeys ? new HashSet<>() : null;
                if (types != null && !types.contains(JsonType.OBJECT))
                {
                    directInvalid = true;
                    trace.report("type", "expected " + typesText() + " but was object", parser);
                }
                else if (constant != null || enums != null)
                {
                    directInvalid = true;
                    trace.report(constant != null ? "const" : "enum", "object not in allowed set", parser);
                }
                break;
            case START_ARRAY:
                array = true;
                if (types != null && !types.contains(JsonType.ARRAY))
                {
                    directInvalid = true;
                    trace.report("type", "expected " + typesText() + " but was array", parser);
                }
                else if (constant != null || enums != null)
                {
                    directInvalid = true;
                    trace.report(constant != null ? "const" : "enum", "array not in allowed set", parser);
                }
                break;
            case VALUE_STRING:
                checkStringReport(parser);
                break;
            case VALUE_NUMBER:
                checkNumberReport(parser);
                break;
            case VALUE_TRUE:
            case VALUE_FALSE:
                if (types != null && !types.contains(JsonType.BOOLEAN))
                {
                    directInvalid = true;
                    trace.report("type", "expected " + typesText() + " but was boolean", parser);
                }
                else if (!checkConstEnum(event, null, null))
                {
                    directInvalid = true;
                    trace.report(constant != null ? "const" : "enum", "boolean not in allowed set", parser);
                }
                break;
            case VALUE_NULL:
                if (types != null && !types.contains(JsonType.NULL))
                {
                    directInvalid = true;
                    trace.report("type", "expected " + typesText() + " but was null", parser);
                }
                else if (!checkConstEnum(VALUE_NULL, null, null))
                {
                    directInvalid = true;
                    trace.report(constant != null ? "const" : "enum", "null not in allowed set", parser);
                }
                break;
            default:
                directInvalid = true;
                trace.report("type", "unexpected event " + event, parser);
                break;
            }
        }

        private String typesText()
        {
            String result;
            if (types == null)
            {
                result = "any";
            }
            else if (types.size() == 1)
            {
                result = types.iterator().next().name().toLowerCase();
            }
            else
            {
                StringBuilder sb = new StringBuilder("one of [");
                boolean first = true;
                for (JsonType t : types)
                {
                    if (!first)
                    {
                        sb.append(',');
                    }
                    sb.append(t.name().toLowerCase());
                    first = false;
                }
                sb.append(']');
                result = sb.toString();
            }
            return result;
        }

        private void checkStringReport(
            JsonParser parser)
        {
            String value = parser.getString();
            int length = value.codePointCount(0, value.length());
            if (types != null && !types.contains(JsonType.STRING))
            {
                directInvalid = true;
                trace.report("type", "expected " + typesText() + " but was string", parser);
            }
            else if (!checkConstEnum(VALUE_STRING, value, null))
            {
                directInvalid = true;
                trace.report(constant != null ? "const" : "enum", "string not in allowed set", parser);
            }
            else if (minLength >= 0 && length < minLength)
            {
                directInvalid = true;
                trace.report("minLength", "length " + length + " < minLength " + minLength, parser);
            }
            else if (maxLength >= 0 && length > maxLength)
            {
                directInvalid = true;
                trace.report("maxLength", "length " + length + " > maxLength " + maxLength, parser);
            }
            else if (pattern != null && !pattern.matcher(value).find())
            {
                directInvalid = true;
                trace.report("pattern", "does not match pattern " + pattern.pattern(), parser);
            }
        }

        private void checkNumberReport(
            JsonParser parser)
        {
            BigDecimal value = parser.getBigDecimal();
            boolean integral = context != null && context.draft != Draft.DRAFT_04
                ? value.signum() == 0 || value.stripTrailingZeros().scale() <= 0
                : parser.isIntegralNumber();
            boolean typeOk = types == null ||
                types.contains(JsonType.NUMBER) ||
                integral && types.contains(JsonType.INTEGER);
            if (!typeOk)
            {
                directInvalid = true;
                trace.report("type", "expected " + typesText() + " but was number", parser);
            }
            else if (!checkConstEnum(VALUE_NUMBER, null, value))
            {
                directInvalid = true;
                trace.report(constant != null ? "const" : "enum", "number not in allowed set", parser);
            }
            else if (minimum != null && value.compareTo(minimum) < 0)
            {
                directInvalid = true;
                trace.report("minimum", value + " < minimum " + minimum, parser);
            }
            else if (maximum != null && value.compareTo(maximum) > 0)
            {
                directInvalid = true;
                trace.report("maximum", value + " > maximum " + maximum, parser);
            }
            else if (exclusiveMinimum != null && value.compareTo(exclusiveMinimum) <= 0)
            {
                directInvalid = true;
                trace.report("exclusiveMinimum", value + " <= exclusiveMinimum " + exclusiveMinimum, parser);
            }
            else if (exclusiveMaximum != null && value.compareTo(exclusiveMaximum) >= 0)
            {
                directInvalid = true;
                trace.report("exclusiveMaximum", value + " >= exclusiveMaximum " + exclusiveMaximum, parser);
            }
            else if (multipleOf != null && (multipleOf.signum() == 0 || value.remainder(multipleOf).signum() != 0))
            {
                directInvalid = true;
                trace.report("multipleOf", value + " is not a multiple of " + multipleOf, parser);
            }
        }

        private void onInner(
            Event event,
            JsonParser parser)
        {
            if (directChild != null || directChildren != null)
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
                if (required != null && !seen.containsAll(required))
                {
                    directInvalid = true;
                    Set<String> missing = new LinkedHashSet<>(required);
                    missing.removeAll(seen);
                    trace.report("required", "missing required " + missing, parser);
                }
                if (minProperties >= 0 && count < minProperties)
                {
                    directInvalid = true;
                    trace.report("minProperties", count + " < minProperties " + minProperties, parser);
                }
                if (maxProperties >= 0 && count > maxProperties)
                {
                    directInvalid = true;
                    trace.report("maxProperties", count + " > maxProperties " + maxProperties, parser);
                }
            }
            else
            {
                String key = parser.getString();
                count++;
                if (seen != null)
                {
                    seen.add(key);
                }
                if (propertyNames != null && propertyNames.eval(trace).feed(VALUE_STRING, parser) != Verdict.VALID)
                {
                    directInvalid = true;
                    trace.report("propertyNames", "property name '" + key + "' violates propertyNames", parser);
                }
                setApplicableFor(key);
            }
        }

        private void onArrayInner(
            Event event,
            JsonParser parser)
        {
            if (event == END_ARRAY)
            {
                if (minItems >= 0 && count < minItems)
                {
                    directInvalid = true;
                    trace.report("minItems", count + " < minItems " + minItems, parser);
                }
                if (maxItems >= 0 && count > maxItems)
                {
                    directInvalid = true;
                    trace.report("maxItems", count + " > maxItems " + maxItems, parser);
                }
                if (contains != null && containsMatched == 0)
                {
                    directInvalid = true;
                    trace.report("contains", "no array element matched contains subschema", parser);
                }
            }
            else
            {
                int index = count;
                count++;
                directChildMark = trace.push(index);
                directChild = elementSchema(index).eval(trace);
                if (contains != null && containsMatched == 0)
                {
                    containsChild = contains.eval(Trace.NONE);
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

        private JsonSchemaImpl elementSchema(
            int index)
        {
            JsonSchemaImpl schema;
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
            if (directChild != null)
            {
                Verdict verdict = directChild.feed(event, parser);
                if (verdict != Verdict.PENDING)
                {
                    directInvalid |= verdict == Verdict.INVALID;
                    directChild = null;
                    trace.pop(directChildMark);
                    complete = true;
                }
            }
            if (directChildren != null)
            {
                for (Eval child : directChildren)
                {
                    Verdict verdict = child.feed(event, parser);
                    if (verdict != Verdict.PENDING)
                    {
                        directInvalid |= verdict == Verdict.INVALID;
                        complete = true;
                    }
                }
                if (complete)
                {
                    trace.pop(directChildMark);
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
                directChild = null;
                directChildren = null;
                if (uniqueTokens != null)
                {
                    if (!uniqueSeen.add(canonicalize(uniqueTokens, new int[] {0})))
                    {
                        directInvalid = true;
                        trace.report("uniqueItems", "duplicate array element", parser);
                    }
                    uniqueTokens = null;
                }
            }
        }

        private void setApplicableFor(
            String key)
        {
            directChildMark = trace.push(key);
            if (patternProperties == null)
            {
                JsonSchemaImpl schema = properties != null ? properties.get(key) : null;
                if (schema == null)
                {
                    schema = additionalSchema != null ? additionalSchema : additionalAllowed ? ANY : NONE;
                }
                directChild = schema.eval(trace);
                directChildren = null;
            }
            else
            {
                List<JsonSchemaImpl> applicable = new ArrayList<>();
                boolean matched = false;
                if (properties != null && properties.containsKey(key))
                {
                    applicable.add(properties.get(key));
                    matched = true;
                }
                for (Map.Entry<Pattern, JsonSchemaImpl> entry : patternProperties.entrySet())
                {
                    if (entry.getKey().matcher(key).find())
                    {
                        applicable.add(entry.getValue());
                        matched = true;
                    }
                }
                if (!matched)
                {
                    applicable.add(additionalSchema != null ? additionalSchema : additionalAllowed ? ANY : NONE);
                }
                setApplicable(applicable);
            }
        }

        private void setApplicable(
            List<JsonSchemaImpl> applicable)
        {
            if (applicable.size() == 1)
            {
                directChild = applicable.get(0).eval(trace);
                directChildren = null;
            }
            else
            {
                directChild = null;
                directChildren = evalsOf(applicable, trace);
            }
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

        private Verdict combine(
            JsonParser parser)
        {
            boolean valid = !directInvalid;
            if (valid && allOfEvals != null)
            {
                for (Eval eval : allOfEvals)
                {
                    if (eval.result != Verdict.VALID)
                    {
                        valid = false;
                    }
                }
                if (!valid)
                {
                    trace.report("allOf", "instance failed at least one allOf subschema", parser);
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
                if (!valid)
                {
                    trace.report("anyOf", "instance failed every anyOf subschema", parser);
                }
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
                if (!valid)
                {
                    trace.report("oneOf", matched == 0
                        ? "instance failed every oneOf subschema"
                        : "instance matched " + matched + " oneOf subschemas (must be exactly 1)", parser);
                }
            }
            if (valid && notEval != null)
            {
                valid = notEval.result != Verdict.VALID;
                if (!valid)
                {
                    trace.report("not", "instance must not match not-subschema", parser);
                }
            }
            if (valid && ifEval != null)
            {
                valid = ifEval.result == Verdict.VALID
                    ? thenEval == null || thenEval.result == Verdict.VALID
                    : elseEval == null || elseEval.result == Verdict.VALID;
                if (!valid)
                {
                    trace.report(ifEval.result == Verdict.VALID ? "then" : "else",
                        "if/then/else branch failed", parser);
                }
            }
            if (valid && seen != null && dependentRequired != null)
            {
                for (Map.Entry<String, Set<String>> entry : dependentRequired.entrySet())
                {
                    if (seen.contains(entry.getKey()) && !seen.containsAll(entry.getValue()))
                    {
                        valid = false;
                        trace.report("dependencies",
                            "presence of '" + entry.getKey() + "' requires " + entry.getValue(), parser);
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
                        trace.report("dependencies",
                            "presence of '" + entry.getKey() + "' requires dependent schema to match", parser);
                        break;
                    }
                }
            }
            return valid ? Verdict.VALID : Verdict.INVALID;
        }

        private Eval[] evalsOf(
            List<JsonSchemaImpl> schemas,
            Trace childTrace)
        {
            Eval[] result = null;
            if (schemas != null)
            {
                result = new Eval[schemas.size()];
                for (int i = 0; i < schemas.size(); i++)
                {
                    result[i] = schemas.get(i).eval(childTrace);
                }
            }
            return result;
        }

        private Map<String, Eval> evalsOfMap(
            Map<String, JsonSchemaImpl> schemas,
            Trace childTrace)
        {
            Map<String, Eval> result = null;
            if (schemas != null)
            {
                result = new LinkedHashMap<>();
                for (Map.Entry<String, JsonSchemaImpl> entry : schemas.entrySet())
                {
                    result.put(entry.getKey(), entry.getValue().eval(childTrace));
                }
            }
            return result;
        }
    }
}
