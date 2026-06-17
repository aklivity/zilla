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
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonRefResolver;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchema.Draft;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonValidationException;

/**
 * An immutable, compiled JSON Schema that validates an instance by consuming a streaming
 * {@link JsonParser} event stream without materializing a DOM. Compile once and reuse;
 * {@link #validate(JsonParser)} and {@link #validate(JsonParser, Consumer)} hold no per-call
 * state on the schema, so a compiled schema may be shared and validated concurrently.
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

    private static final String[] NO_KEYS = new String[0];
    private static final JsonSchemaImpl[] NO_SCHEMAS = new JsonSchemaImpl[0];

    // a lexeme of at most this many characters is always within long range, so getLong() is safe
    private static final int LONG_DIGITS = 18;
    private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    private static final JsonRefResolver LOCAL_ONLY = ref -> null;

    private static final URI EMPTY_URI = URI.create("");

    private static final List<String> SINGLE_SUBSCHEMAS = List.of(
        "additionalProperties", "additionalItems", "unevaluatedProperties", "unevaluatedItems",
        "contains", "propertyNames", "not", "if", "then", "else", "contentSchema");

    private static final List<String> ARRAY_SUBSCHEMAS = List.of(
        "prefixItems", "allOf", "anyOf", "oneOf");

    private static final List<String> MAP_SUBSCHEMAS = List.of(
        "properties", "patternProperties", "$defs", "definitions", "dependentSchemas");

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
    private final String refApplicator;
    private final String dynamicRef;
    private final boolean recursiveRef;
    private final Context context;
    private final Set<JsonType> types;
    private final Set<String> enumCanons;
    private final String constantCanon;
    // scalar-string const/enum: the decoded candidate(s), so a string instance is compared as a
    // CharSequence without materializing its canonical (set only when no canonical fallback is needed)
    private final String constString;
    private final String[] enumStrings;
    private final BigDecimal minimum;
    private final BigDecimal maximum;
    private final BigDecimal exclusiveMinimum;
    private final BigDecimal exclusiveMaximum;
    private final BigDecimal multipleOf;
    // every present numeric bound is an integer within long range (and multipleOf is non-zero): an
    // integer instance can then be checked with long arithmetic instead of a BigDecimal per value
    private final boolean integerBounds;
    private final int minLength;
    private final int maxLength;
    private final Pattern pattern;
    private final JsonSchemaImpl items;
    private final List<JsonSchemaImpl> itemsTuple;
    private final JsonSchemaImpl additionalItems;
    private final JsonSchemaImpl contains;
    private final int minContains;
    private final int maxContains;
    private final boolean uniqueItems;
    private final int minItems;
    private final int maxItems;
    private final Map<String, JsonSchemaImpl> properties;
    private final Set<String> required;
    private final boolean additionalAllowed;
    private final boolean hasAdditional;
    private final JsonSchemaImpl additionalSchema;
    private final JsonSchemaImpl unevaluatedProperties;
    private final JsonSchemaImpl unevaluatedItems;
    private final int minProperties;
    private final int maxProperties;
    private final Map<Pattern, JsonSchemaImpl> patternProperties;
    private final JsonSchemaImpl propertyNames;
    private final Map<String, Set<String>> dependentRequired;
    private final Map<String, JsonSchemaImpl> dependentSchemas;
    // an object using only properties/required/additionalProperties: its keys can be matched as a
    // CharSequence against these arrays, so a key is not materialized into a String to look up or track
    private final boolean simpleObject;
    private final String[] propertyKeys;
    private final JsonSchemaImpl[] propertySchemas;
    private final String[] requiredKeys;
    private final List<JsonSchemaImpl> allOf;
    private final List<JsonSchemaImpl> anyOf;
    private final List<JsonSchemaImpl> oneOf;
    private final JsonSchemaImpl notSchema;
    private final JsonSchemaImpl ifSchema;
    private final JsonSchemaImpl thenSchema;
    private final JsonSchemaImpl elseSchema;

    private List<String> retainedPaths;

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
        Registry registry = new Registry(resolver, resolved);
        URI rootBase = baseOf(root, EMPTY_URI, resolved);
        registry.ids.put(rootBase.toString(), root);
        index(root, EMPTY_URI, registry);
        JsonSchemaImpl result = from(root, new Context(registry, rootBase));
        if (result != ANY && result != NONE)
        {
            result.retainedPaths = JsonSchemaPaths.retained(schema);
        }
        return result;
    }

    public static Set<String> collectRefs(
        String schema)
    {
        Set<String> refs = new LinkedHashSet<>();
        collectRefs(JsonNode.parse(schema), refs);
        return refs;
    }

    // stateless so the schema stays immutable and shareable; a caller validating repeatedly reuses the
    // validating parser from newParser(), which carries the mutable per-validation state
    @Override
    public boolean validate(
        JsonParser parser)
    {
        return drive(eval(), new ParserSource().wrap(parser), parser);
    }

    @Override
    public boolean validate(
        JsonParser parser,
        Consumer<JsonSchemaDiagnostic> reporter)
    {
        return drive(eval(new Trace(reporter)), new ParserSource().wrap(parser), parser);
    }

    private static boolean drive(
        Eval eval,
        JsonSource source,
        JsonParser parser)
    {
        Verdict verdict = Verdict.PENDING;
        while (parser.hasNext() && verdict == Verdict.PENDING)
        {
            verdict = eval.feed(parser.next(), source);
        }
        return verdict == Verdict.VALID;
    }

    @Override
    public JsonParser newParser(
        boolean throwing,
        JsonParser parser)
    {
        return newParser(throwing, parser, null);
    }

    @Override
    public JsonParser newParser(
        boolean throwing,
        JsonParser parser,
        Consumer<JsonSchemaDiagnostic> reporter)
    {
        return new ValidatingParser(throwing, parser, reporter);
    }

    @Override
    public JsonTransform validator()
    {
        return new Validator();
    }

    @Override
    public List<String> retainedPaths()
    {
        return retainedPaths != null ? retainedPaths : List.of();
    }

    private JsonSchemaImpl(
        JsonNode schema,
        Context context)
    {
        JsonNode itemsValue = schema.get("items");
        JsonNode prefixItemsValue = schema.get("prefixItems");
        List<JsonSchemaImpl> tuple;
        JsonSchemaImpl allItems;
        JsonSchemaImpl restItems;
        if (prefixItemsValue != null)
        {
            tuple = parseSchemaArray(prefixItemsValue, context);
            allItems = null;
            restItems = itemsValue != null ? from(itemsValue, context) : null;
        }
        else if (itemsValue != null && itemsValue.isArray())
        {
            tuple = parseSchemaArray(itemsValue, context);
            allItems = null;
            restItems = schema.has("additionalItems") ? from(schema.get("additionalItems"), context) : null;
        }
        else
        {
            tuple = null;
            allItems = itemsValue != null ? from(itemsValue, context) : null;
            restItems = null;
        }

        JsonNode additional = schema.get("additionalProperties");
        boolean additionalAllowed = additional == null || !additional.isFalse();
        JsonSchemaImpl additionalSchema = additional != null && additional.isObject()
            ? from(additional, context)
            : null;

        this.deny = false;
        this.ref = null;
        this.refApplicator = is2019Plus(context.draft()) && schema.has("$ref")
            ? context.resolveRef(schema.get("$ref").string())
            : null;
        this.dynamicRef = is2019Plus(context.draft()) && schema.has("$dynamicRef")
            ? schema.get("$dynamicRef").string()
            : null;
        this.recursiveRef = is2019Plus(context.draft()) && schema.has("$recursiveRef");
        this.context = context;
        this.types = parseTypes(schema.get("type"));
        this.enumCanons = parseEnumCanons(schema.get("enum"));
        this.constantCanon = schema.has("const") ? canonicalizeNode(schema.get("const")) : null;
        JsonNode constNode = schema.get("const");
        this.constString = enumCanons == null && constNode != null && constNode.kind() == JsonNode.Kind.STRING
            ? constNode.string() : null;
        this.enumStrings = constantCanon == null ? enumStrings(schema.get("enum")) : null;
        BigDecimal minimumValue = number(schema, "minimum");
        BigDecimal maximumValue = number(schema, "maximum");
        BigDecimal exclusiveMin = exclusiveBound(schema, "exclusiveMinimum", minimumValue, context.draft());
        BigDecimal exclusiveMax = exclusiveBound(schema, "exclusiveMaximum", maximumValue, context.draft());
        boolean minSuppressed = exclusiveMin != null && context.draft() == Draft.DRAFT_04 &&
            schema.has("exclusiveMinimum") && schema.get("exclusiveMinimum").isTrue();
        boolean maxSuppressed = exclusiveMax != null && context.draft() == Draft.DRAFT_04 &&
            schema.has("exclusiveMaximum") && schema.get("exclusiveMaximum").isTrue();
        this.minimum = minSuppressed ? null : minimumValue;
        this.maximum = maxSuppressed ? null : maximumValue;
        this.exclusiveMinimum = exclusiveMin;
        this.exclusiveMaximum = exclusiveMax;
        this.multipleOf = number(schema, "multipleOf");
        this.integerBounds = longInteger(this.minimum) && longInteger(this.maximum) &&
            longInteger(this.exclusiveMinimum) && longInteger(this.exclusiveMaximum) &&
            (this.multipleOf == null || longInteger(this.multipleOf) && this.multipleOf.signum() != 0);
        this.minLength = integer(schema, "minLength");
        this.maxLength = integer(schema, "maxLength");
        this.pattern = schema.has("pattern") ? compilePattern(schema.get("pattern").string()) : null;
        this.items = allItems;
        this.itemsTuple = tuple;
        this.additionalItems = restItems;
        this.contains = schema.has("contains") ? from(schema.get("contains"), context) : null;
        this.minContains = integer(schema, "minContains");
        this.maxContains = integer(schema, "maxContains");
        this.uniqueItems = schema.has("uniqueItems") && schema.get("uniqueItems").isTrue();
        this.minItems = integer(schema, "minItems");
        this.maxItems = integer(schema, "maxItems");
        this.properties = parseProperties(schema.get("properties"), context);
        this.required = parseRequired(schema.get("required"));
        this.additionalAllowed = additionalAllowed;
        this.hasAdditional = additional != null;
        this.additionalSchema = additionalSchema;
        this.unevaluatedProperties = schema.has("unevaluatedProperties")
            ? from(schema.get("unevaluatedProperties"), context)
            : null;
        this.unevaluatedItems = schema.has("unevaluatedItems")
            ? from(schema.get("unevaluatedItems"), context)
            : null;
        this.minProperties = integer(schema, "minProperties");
        this.maxProperties = integer(schema, "maxProperties");
        this.patternProperties = parsePatternProperties(schema.get("patternProperties"), context);
        this.propertyNames = schema.has("propertyNames") ? from(schema.get("propertyNames"), context) : null;
        this.dependentRequired = parseDependentRequired(schema);
        this.dependentSchemas = parseDependentSchemas(schema, context);
        this.simpleObject = patternProperties == null && propertyNames == null &&
            unevaluatedProperties == null && dependentRequired == null && dependentSchemas == null;
        this.requiredKeys = required != null ? required.toArray(NO_KEYS) : NO_KEYS;
        this.propertyKeys = properties != null ? properties.keySet().toArray(NO_KEYS) : NO_KEYS;
        this.propertySchemas = properties != null ? properties.values().toArray(NO_SCHEMAS) : NO_SCHEMAS;
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
        this.refApplicator = null;
        this.dynamicRef = null;
        this.recursiveRef = false;
        this.context = context;
        this.types = null;
        this.enumCanons = null;
        this.constantCanon = null;
        this.constString = null;
        this.enumStrings = null;
        this.minimum = null;
        this.maximum = null;
        this.exclusiveMinimum = null;
        this.exclusiveMaximum = null;
        this.multipleOf = null;
        this.integerBounds = false;
        this.minLength = -1;
        this.maxLength = -1;
        this.pattern = null;
        this.items = null;
        this.itemsTuple = null;
        this.additionalItems = null;
        this.contains = null;
        this.minContains = -1;
        this.maxContains = -1;
        this.uniqueItems = false;
        this.minItems = -1;
        this.maxItems = -1;
        this.properties = null;
        this.required = null;
        this.additionalAllowed = true;
        this.hasAdditional = false;
        this.additionalSchema = null;
        this.unevaluatedProperties = null;
        this.unevaluatedItems = null;
        this.minProperties = -1;
        this.maxProperties = -1;
        this.patternProperties = null;
        this.propertyNames = null;
        this.dependentRequired = null;
        this.dependentSchemas = null;
        this.simpleObject = false;
        this.requiredKeys = NO_KEYS;
        this.propertyKeys = NO_KEYS;
        this.propertySchemas = NO_SCHEMAS;
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
        this.refApplicator = null;
        this.dynamicRef = null;
        this.recursiveRef = false;
        this.context = null;
        this.types = null;
        this.enumCanons = null;
        this.constantCanon = null;
        this.constString = null;
        this.enumStrings = null;
        this.minimum = null;
        this.maximum = null;
        this.exclusiveMinimum = null;
        this.exclusiveMaximum = null;
        this.multipleOf = null;
        this.integerBounds = false;
        this.minLength = -1;
        this.maxLength = -1;
        this.pattern = null;
        this.items = null;
        this.itemsTuple = null;
        this.additionalItems = null;
        this.contains = null;
        this.minContains = -1;
        this.maxContains = -1;
        this.uniqueItems = false;
        this.minItems = -1;
        this.maxItems = -1;
        this.properties = null;
        this.required = null;
        this.additionalAllowed = true;
        this.hasAdditional = false;
        this.additionalSchema = null;
        this.unevaluatedProperties = null;
        this.unevaluatedItems = null;
        this.minProperties = -1;
        this.maxProperties = -1;
        this.patternProperties = null;
        this.propertyNames = null;
        this.dependentRequired = null;
        this.dependentSchemas = null;
        this.simpleObject = false;
        this.requiredKeys = NO_KEYS;
        this.propertyKeys = NO_KEYS;
        this.propertySchemas = NO_SCHEMAS;
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
        return eval(Trace.NONE, DynScope.ROOT);
    }

    private Eval eval(
        Trace trace)
    {
        return eval(trace, DynScope.ROOT);
    }

    private Eval eval(
        Trace trace,
        DynScope parentScope)
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
        return schema.evalDirect(trace, parentScope);
    }

    private Eval evalDirect(
        Trace trace,
        DynScope parentScope)
    {
        return new Eval(trace, parentScope);
    }

    private static final Pattern UNICODE_PROPERTY = Pattern.compile("\\\\([pP])\\{([A-Za-z_]+)\\}");

    private static final Map<String, String> UNICODE_CATEGORIES = Map.ofEntries(
        Map.entry("Letter", "L"),
        Map.entry("Uppercase_Letter", "Lu"),
        Map.entry("Lowercase_Letter", "Ll"),
        Map.entry("Titlecase_Letter", "Lt"),
        Map.entry("Modifier_Letter", "Lm"),
        Map.entry("Other_Letter", "Lo"),
        Map.entry("Mark", "M"),
        Map.entry("Nonspacing_Mark", "Mn"),
        Map.entry("Spacing_Mark", "Mc"),
        Map.entry("Enclosing_Mark", "Me"),
        Map.entry("Number", "N"),
        Map.entry("Decimal_Number", "Nd"),
        Map.entry("Letter_Number", "Nl"),
        Map.entry("Other_Number", "No"),
        Map.entry("Punctuation", "P"),
        Map.entry("Connector_Punctuation", "Pc"),
        Map.entry("Dash_Punctuation", "Pd"),
        Map.entry("Open_Punctuation", "Ps"),
        Map.entry("Close_Punctuation", "Pe"),
        Map.entry("Initial_Punctuation", "Pi"),
        Map.entry("Final_Punctuation", "Pf"),
        Map.entry("Other_Punctuation", "Po"),
        Map.entry("Symbol", "S"),
        Map.entry("Math_Symbol", "Sm"),
        Map.entry("Currency_Symbol", "Sc"),
        Map.entry("Modifier_Symbol", "Sk"),
        Map.entry("Other_Symbol", "So"),
        Map.entry("Separator", "Z"),
        Map.entry("Space_Separator", "Zs"),
        Map.entry("Line_Separator", "Zl"),
        Map.entry("Paragraph_Separator", "Zp"),
        Map.entry("Other", "C"),
        Map.entry("Control", "Cc"),
        Map.entry("Format", "Cf"),
        Map.entry("Surrogate", "Cs"),
        Map.entry("Private_Use", "Co"),
        Map.entry("Unassigned", "Cn"));

    private static Pattern compilePattern(
        String regex)
    {
        Matcher matcher = UNICODE_PROPERTY.matcher(regex);
        StringBuilder result = new StringBuilder();
        while (matcher.find())
        {
            String code = UNICODE_CATEGORIES.get(matcher.group(2));
            String replacement = code != null
                ? "\\\\" + matcher.group(1) + "{" + code + "}"
                : Matcher.quoteReplacement(matcher.group());
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return Pattern.compile(result.toString());
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
                result.put(compilePattern(entry.getKey()), from(entry.getValue(), context));
            }
        }
        return result;
    }

    private static Map<String, Set<String>> parseDependentRequired(
        JsonNode schema)
    {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        collectDependentRequired(schema.get("dependencies"), result);
        collectDependentRequired(schema.get("dependentRequired"), result);
        return result.isEmpty() ? null : result;
    }

    private static void collectDependentRequired(
        JsonNode value,
        Map<String, Set<String>> result)
    {
        if (value != null)
        {
            for (Map.Entry<String, JsonNode> entry : value.members().entrySet())
            {
                if (entry.getValue().isArray())
                {
                    Set<String> names = new LinkedHashSet<>();
                    for (JsonNode name : entry.getValue().elements())
                    {
                        names.add(name.string());
                    }
                    result.put(entry.getKey(), names);
                }
            }
        }
    }

    private static Map<String, JsonSchemaImpl> parseDependentSchemas(
        JsonNode schema,
        Context context)
    {
        Map<String, JsonSchemaImpl> result = new LinkedHashMap<>();
        collectDependentSchemas(schema.get("dependencies"), context, result);
        collectDependentSchemas(schema.get("dependentSchemas"), context, result);
        return result.isEmpty() ? null : result;
    }

    private static void collectDependentSchemas(
        JsonNode value,
        Context context,
        Map<String, JsonSchemaImpl> result)
    {
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
    }

    private static JsonSchemaImpl from(
        JsonNode value,
        Context context)
    {
        JsonSchemaImpl result;
        if (value.isObject())
        {
            Context scope = context.scope(value);
            if (value.has("$ref") && !is2019Plus(scope.draft()))
            {
                String absolute = scope.resolveRef(value.get("$ref").string());
                result = new JsonSchemaImpl(absolute, scope);
            }
            else
            {
                result = new JsonSchemaImpl(value, scope);
            }
        }
        else
        {
            result = value.isFalse() ? NONE : ANY;
        }
        return result;
    }

    private static boolean is2019Plus(
        Draft draft)
    {
        return draft.ordinal() >= Draft.DRAFT_2019_09.ordinal();
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

    private static Set<String> parseEnumCanons(
        JsonNode value)
    {
        Set<String> result = null;
        if (value != null)
        {
            result = new HashSet<>();
            for (JsonNode candidate : value.elements())
            {
                result.add(canonicalizeNode(candidate));
            }
        }
        return result;
    }

    // the decoded enum candidates when every one is a scalar string, else null (canonical path)
    private static String[] enumStrings(
        JsonNode enumNode)
    {
        String[] result = null;
        if (enumNode != null)
        {
            List<String> strings = new ArrayList<>();
            boolean allStrings = true;
            for (JsonNode candidate : enumNode.elements())
            {
                if (candidate.kind() == JsonNode.Kind.STRING)
                {
                    strings.add(candidate.string());
                }
                else
                {
                    allStrings = false;
                    break;
                }
            }
            result = allStrings ? strings.toArray(NO_KEYS) : null;
        }
        return result;
    }

    private static boolean containsString(
        String[] candidates,
        CharSequence value)
    {
        boolean found = false;
        for (int i = 0; !found && i < candidates.length; i++)
        {
            found = charsEqual(candidates[i], value);
        }
        return found;
    }

    private static String canonicalizeNode(
        JsonNode node)
    {
        String result;
        switch (node.kind())
        {
        case OBJECT:
        {
            List<String> members = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : node.members().entrySet())
            {
                members.add(quote(entry.getKey()) + ":" + canonicalizeNode(entry.getValue()));
            }
            members.sort(null);
            result = "{" + String.join(",", members) + "}";
            break;
        }
        case ARRAY:
        {
            List<String> elements = new ArrayList<>();
            for (JsonNode element : node.elements())
            {
                elements.add(canonicalizeNode(element));
            }
            result = "[" + String.join(",", elements) + "]";
            break;
        }
        case STRING:
            result = quote(node.string());
            break;
        case NUMBER:
            result = normalizeNumber(node.string());
            break;
        case TRUE:
            result = "true";
            break;
        case FALSE:
            result = "false";
            break;
        default:
            result = "null";
            break;
        }
        return result;
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
                else if (text.contains("draft-07"))
                {
                    draft = Draft.DRAFT_07;
                }
                else if (text.contains("2019-09"))
                {
                    draft = Draft.DRAFT_2019_09;
                }
                else if (text.contains("2020-12"))
                {
                    draft = Draft.DRAFT_2020_12;
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
        JsonSource parser)
    {
        return event == KEY_NAME || event == VALUE_STRING || event == VALUE_NUMBER ? parser.getString() : null;
    }

    private static final class ParserSource implements JsonSource
    {
        private JsonParser parser;
        // non-null only when the delegate offers the zero-alloc char view; else getStringView falls back
        private JsonParserEx parserEx;

        private ParserSource wrap(
            JsonParser parser)
        {
            this.parser = parser;
            this.parserEx = parser instanceof JsonParserEx ex ? ex : null;
            return this;
        }

        @Override
        public String getString()
        {
            return parser.getString();
        }

        @Override
        public CharSequence getStringView()
        {
            return parserEx != null ? parserEx.getStringView() : parser.getString();
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            return parser.getBigDecimal();
        }

        @Override
        public boolean isIntegralNumber()
        {
            return parser.isIntegralNumber();
        }

        @Override
        public int getInt()
        {
            return parser.getInt();
        }

        @Override
        public long getLong()
        {
            return parser.getLong();
        }

        @Override
        public JsonLocation getLocation()
        {
            return parser.getLocation();
        }

        @Override
        public DirectBuffer getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }
    }

    private static String quote(
        String value)
    {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String normalizeNumber(
        CharSequence text)
    {
        String result;
        if (canonicalInteger(text))
        {
            // a plain integer literal is already canonical, so no BigDecimal
            result = text.toString();
        }
        else
        {
            BigDecimal value = new BigDecimal(text.toString());
            result = value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
        }
        return result;
    }

    private static boolean canonicalInteger(
        CharSequence text)
    {
        int length = text.length();
        boolean digits = length > 0;
        for (int i = 0; digits && i < length; i++)
        {
            char c = text.charAt(i);
            digits = c >= '0' && c <= '9' || i == 0 && c == '-';
        }
        boolean canonical = digits;
        if (canonical && text.charAt(0) == '-')
        {
            // "-0" normalizes to "0", so it is not canonical as-is
            canonical = length >= 2 && !(length == 2 && text.charAt(1) == '0');
        }
        return canonical;
    }

    private static boolean charsEqual(
        String name,
        CharSequence key)
    {
        int length = name.length();
        boolean equal = length == key.length();
        for (int i = 0; equal && i < length; i++)
        {
            equal = name.charAt(i) == key.charAt(i);
        }
        return equal;
    }

    // absent, or an integer within long range
    private static boolean longInteger(
        BigDecimal value)
    {
        return value == null ||
            value.stripTrailingZeros().scale() <= 0 &&
            value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0;
    }

    private static URI baseOf(
        JsonNode node,
        URI parentBase,
        Draft draft)
    {
        URI base = parentBase;
        JsonNode idNode = draft == Draft.DRAFT_04 ? node.get("id") : node.get("$id");
        if (idNode != null && idNode.isString())
        {
            String id = idNode.string();
            if (!id.isEmpty() && !id.startsWith("#"))
            {
                base = stripFragment(parentBase.resolve(id));
            }
        }
        return base;
    }

    private static URI stripFragment(
        URI uri)
    {
        String text = uri.toString();
        int hash = text.indexOf('#');
        return hash < 0 ? uri : URI.create(text.substring(0, hash));
    }

    private static String anchorKey(
        URI base,
        String anchor)
    {
        return base.toString() + "#" + anchor;
    }

    private static void index(
        JsonNode node,
        URI base,
        Registry registry)
    {
        if (node != null && node.isObject())
        {
            URI scope = base;
            JsonNode idNode = registry.draft == Draft.DRAFT_04 ? node.get("id") : node.get("$id");
            if (idNode != null && idNode.isString())
            {
                String id = idNode.string();
                if (id.startsWith("#"))
                {
                    registry.anchors.put(anchorKey(base, id.substring(1)), node);
                }
                else if (!id.isEmpty())
                {
                    URI full = base.resolve(id);
                    scope = stripFragment(full);
                    registry.ids.put(scope.toString(), node);
                    if (full.getRawFragment() != null && !full.getRawFragment().isEmpty())
                    {
                        registry.anchors.put(anchorKey(scope, full.getRawFragment()), node);
                    }
                }
            }
            JsonNode anchorNode = node.get("$anchor");
            if (anchorNode != null && anchorNode.isString())
            {
                registry.anchors.put(anchorKey(scope, anchorNode.string()), node);
            }
            JsonNode dynamicAnchorNode = node.get("$dynamicAnchor");
            if (dynamicAnchorNode != null && dynamicAnchorNode.isString())
            {
                registry.dynamicAnchors.put(anchorKey(scope, dynamicAnchorNode.string()), node);
            }
            JsonNode recursiveAnchorNode = node.get("$recursiveAnchor");
            if (recursiveAnchorNode != null && recursiveAnchorNode.isTrue())
            {
                registry.recursiveResources.add(scope.toString());
            }
            indexChildren(node, scope, registry);
        }
    }

    private static void indexChildren(
        JsonNode node,
        URI scope,
        Registry registry)
    {
        for (String keyword : SINGLE_SUBSCHEMAS)
        {
            index(node.get(keyword), scope, registry);
        }
        JsonNode items = node.get("items");
        if (items != null && items.isArray())
        {
            indexArray(items, scope, registry);
        }
        else
        {
            index(items, scope, registry);
        }
        for (String keyword : ARRAY_SUBSCHEMAS)
        {
            indexArray(node.get(keyword), scope, registry);
        }
        for (String keyword : MAP_SUBSCHEMAS)
        {
            indexMap(node.get(keyword), scope, registry);
        }
        index(node.get("dependencies"), scope, registry);
    }

    private static void indexArray(
        JsonNode value,
        URI scope,
        Registry registry)
    {
        if (value != null && value.isArray())
        {
            for (JsonNode element : value.elements())
            {
                index(element, scope, registry);
            }
        }
    }

    private static void indexMap(
        JsonNode value,
        URI scope,
        Registry registry)
    {
        if (value != null && value.isObject())
        {
            for (JsonNode member : value.members().values())
            {
                index(member, scope, registry);
            }
        }
    }

    private static final class Registry
    {
        private final JsonRefResolver resolver;
        private final Draft draft;
        private final Map<String, JsonNode> ids;
        private final Map<String, JsonNode> anchors;
        private final Map<String, JsonNode> dynamicAnchors;
        private final Set<String> recursiveResources;
        private final Map<String, JsonSchemaImpl> cache;

        private Registry(
            JsonRefResolver resolver,
            Draft draft)
        {
            this.resolver = resolver;
            this.draft = draft;
            this.ids = new HashMap<>();
            this.anchors = new HashMap<>();
            this.dynamicAnchors = new HashMap<>();
            this.recursiveResources = new HashSet<>();
            this.cache = new HashMap<>();
        }
    }

    private static final class Context
    {
        private final Registry registry;
        private final URI base;

        private Context(
            Registry registry,
            URI base)
        {
            this.registry = registry;
            this.base = base;
        }

        private Draft draft()
        {
            return registry.draft;
        }

        private Context scope(
            JsonNode node)
        {
            URI childBase = baseOf(node, base, registry.draft);
            return childBase.equals(base) ? this : new Context(registry, childBase);
        }

        private String resolveRef(
            String ref)
        {
            return base.resolve(ref).toString();
        }

        private JsonSchemaImpl resolve(
            String ref)
        {
            JsonSchemaImpl schema = registry.cache.get(ref);
            if (schema == null)
            {
                int hash = ref.indexOf('#');
                String uri = hash < 0 ? ref : ref.substring(0, hash);
                String fragment = hash < 0 ? "" : ref.substring(hash + 1);
                URI resourceBase = URI.create(uri);
                JsonNode resource = resource(uri, resourceBase);
                JsonNode node;
                if (fragment.isEmpty())
                {
                    node = resource;
                }
                else if (fragment.startsWith("/"))
                {
                    node = pointer(resource, fragment, ref);
                }
                else
                {
                    node = registry.anchors.get(uri + "#" + fragment);
                    if (node == null)
                    {
                        node = registry.dynamicAnchors.get(uri + "#" + fragment);
                    }
                    if (node == null)
                    {
                        throw new IllegalArgumentException("unresolved $ref: " + ref);
                    }
                }
                schema = from(node, new Context(registry, resourceBase));
                registry.cache.put(ref, schema);
            }
            return schema;
        }

        private JsonNode resource(
            String uri,
            URI resourceBase)
        {
            JsonNode resource = registry.ids.get(uri);
            if (resource == null)
            {
                String text = registry.resolver.resolve(uri);
                if (text == null)
                {
                    throw new IllegalArgumentException("unresolved $ref: " + uri);
                }
                resource = JsonNode.parse(text);
                registry.ids.put(uri, resource);
                index(resource, resourceBase, registry);
            }
            return resource;
        }

        private JsonNode pointer(
            JsonNode resource,
            String fragment,
            String ref)
        {
            JsonNode node = resource;
            for (String segment : fragment.substring(1).split("/", -1))
            {
                String key = segment.replace("~1", "/").replace("~0", "~");
                node = node.isArray() ? node.elements().get(Integer.parseInt(key)) : node.get(key);
                if (node == null)
                {
                    throw new IllegalArgumentException("unresolved $ref: " + ref);
                }
            }
            return node;
        }

        private JsonSchemaImpl resolveDynamic(
            String dynamicRef,
            DynScope scope)
        {
            String absolute = resolveRef(dynamicRef);
            int hash = absolute.indexOf('#');
            String targetBase = hash < 0 ? absolute : absolute.substring(0, hash);
            String name = hash < 0 ? "" : absolute.substring(hash + 1);
            JsonSchemaImpl result;
            boolean bookended = !name.isEmpty() && registry.dynamicAnchors.containsKey(targetBase + "#" + name);
            if (bookended)
            {
                String outerBase = scope.dynamicAnchorBase(registry, name);
                String resolvedBase = outerBase != null ? outerBase : targetBase;
                JsonNode node = registry.dynamicAnchors.get(resolvedBase + "#" + name);
                result = from(node, new Context(registry, URI.create(resolvedBase)));
            }
            else
            {
                result = resolve(absolute);
            }
            return result;
        }

        private JsonSchemaImpl resolveRecursive(
            DynScope scope)
        {
            String outerBase = registry.recursiveResources.contains(base.toString())
                ? scope.recursiveBase(registry)
                : null;
            URI target = outerBase != null ? URI.create(outerBase) : base;
            return from(registry.ids.get(target.toString()), new Context(registry, target));
        }
    }

    private static final class DynScope
    {
        private static final DynScope ROOT = new DynScope(null, null);

        private final DynScope parent;
        private final String base;

        private DynScope(
            DynScope parent,
            String base)
        {
            this.parent = parent;
            this.base = base;
        }

        private DynScope push(
            String base)
        {
            return base.equals(this.base) ? this : new DynScope(this, base);
        }

        private String dynamicAnchorBase(
            Registry registry,
            String name)
        {
            String outer = parent != null ? parent.dynamicAnchorBase(registry, name) : null;
            return outer != null ? outer
                : base != null && registry.dynamicAnchors.containsKey(base + "#" + name) ? base : null;
        }

        private String recursiveBase(
            Registry registry)
        {
            String outer = parent != null ? parent.recursiveBase(registry) : null;
            return outer != null ? outer : base != null && registry.recursiveResources.contains(base) ? base : null;
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

    // Builds an array element's canonical text incrementally (no retained Token list, no recursive pass)
    // for uniqueItems. Output matches canonicalize(List<Token>), so duplicate detection is unchanged.
    private static final class UniqueCanon
    {
        private static final int MAX_DEPTH = 64;

        private final Frame[] frames = new Frame[MAX_DEPTH];
        private final StringBuilder quoted = new StringBuilder();
        private int depth;
        private String result;

        private void reset()
        {
            depth = 0;
            result = null;
        }

        private void feed(
            Event event,
            JsonSource parser)
        {
            switch (event)
            {
            case START_OBJECT:
                frame(depth++).begin(true);
                break;
            case START_ARRAY:
                frame(depth++).begin(false);
                break;
            case END_OBJECT:
            case END_ARRAY:
                emit(frames[--depth].render());
                break;
            case KEY_NAME:
                frames[depth - 1].key(quoted(parser.getStringView()));
                break;
            case VALUE_STRING:
                emit(quoted(parser.getStringView()));
                break;
            case VALUE_NUMBER:
                emit(normalizeNumber(parser.getStringView()));
                break;
            case VALUE_TRUE:
                emit("true");
                break;
            case VALUE_FALSE:
                emit("false");
                break;
            default:
                emit("null");
                break;
            }
        }

        private void emit(
            String canon)
        {
            if (depth == 0)
            {
                result = canon;
            }
            else
            {
                frames[depth - 1].add(canon);
            }
        }

        private Frame frame(
            int index)
        {
            Frame frame = frames[index];
            if (frame == null)
            {
                frame = new Frame();
                frames[index] = frame;
            }
            return frame;
        }

        // mirrors quote(String): escapes only backslash and double-quote
        private String quoted(
            CharSequence value)
        {
            StringBuilder out = quoted;
            out.setLength(0);
            out.append('"');
            for (int i = 0; i < value.length(); i++)
            {
                char c = value.charAt(i);
                if (c == '"' || c == '\\')
                {
                    out.append('\\');
                }
                out.append(c);
            }
            out.append('"');
            return out.toString();
        }
    }

    private static final class Frame
    {
        // arrays stream into buffer in order; objects collect members as parallel key/value lists and
        // assemble them sorted at render, so only one canonical String per container is allocated
        private final StringBuilder buffer = new StringBuilder();
        private final List<String> keys = new ArrayList<>();
        private final List<String> values = new ArrayList<>();
        private int[] order = new int[16];
        private boolean object;
        private boolean first;
        private String key;

        private void begin(
            boolean object)
        {
            this.object = object;
            this.first = true;
            this.key = null;
            buffer.setLength(0);
            if (object)
            {
                keys.clear();
                values.clear();
            }
            else
            {
                buffer.append('[');
            }
        }

        private void key(
            String quotedKey)
        {
            this.key = quotedKey;
        }

        private void add(
            String canon)
        {
            if (object)
            {
                keys.add(key);
                values.add(canon);
                key = null;
            }
            else
            {
                if (!first)
                {
                    buffer.append(',');
                }
                buffer.append(canon);
                first = false;
            }
        }

        private String render()
        {
            String result;
            if (object)
            {
                int count = keys.size();
                sortMembers(count);
                buffer.append('{');
                for (int i = 0; i < count; i++)
                {
                    if (i > 0)
                    {
                        buffer.append(',');
                    }
                    int member = order[i];
                    buffer.append(keys.get(member)).append(':').append(values.get(member));
                }
                buffer.append('}');
            }
            else
            {
                buffer.append(']');
            }
            result = buffer.toString();
            return result;
        }

        // sort member indices by (quoted key, then value); a quoted key is self-delimiting, so this
        // matches sorting the "key:value" members — the canonical form is unchanged
        private void sortMembers(
            int count)
        {
            if (count > order.length)
            {
                order = new int[Math.max(count, order.length << 1)];
            }
            for (int i = 0; i < count; i++)
            {
                order[i] = i;
            }
            for (int i = 1; i < count; i++)
            {
                int current = order[i];
                int j = i - 1;
                while (j >= 0 && compareMembers(order[j], current) > 0)
                {
                    order[j + 1] = order[j];
                    j--;
                }
                order[j + 1] = current;
            }
        }

        private int compareMembers(
            int left,
            int right)
        {
            int result = keys.get(left).compareTo(keys.get(right));
            if (result == 0)
            {
                result = values.get(left).compareTo(values.get(right));
            }
            return result;
        }
    }

    // Open-addressed set of canonical uniqueItems strings: probe table rather than HashSet, so no node
    // per element. The string is kept and compared, so a hash collision never reports a false duplicate.
    private static final class CanonSet
    {
        private String[] table;
        private int mask;
        private int size;

        private CanonSet()
        {
            table = new String[16];
            mask = table.length - 1;
        }

        private void clear()
        {
            Arrays.fill(table, null);
            size = 0;
        }

        // false if an equal value was already present (a duplicate element)
        private boolean add(
            String value)
        {
            if (size + 1 > (table.length >> 1) + (table.length >> 2))
            {
                resize();
            }
            int index = value.hashCode() & mask;
            String existing = table[index];
            boolean added = true;
            while (existing != null)
            {
                if (existing.equals(value))
                {
                    added = false;
                    break;
                }
                index = index + 1 & mask;
                existing = table[index];
            }
            if (added)
            {
                table[index] = value;
                size++;
            }
            return added;
        }

        private void resize()
        {
            String[] old = table;
            table = new String[old.length << 1];
            mask = table.length - 1;
            size = 0;
            for (String value : old)
            {
                if (value != null)
                {
                    add(value);
                }
            }
        }
    }

    private boolean validateTokens(
        List<Token> tokens,
        DynScope scope)
    {
        Eval eval = eval(Trace.NONE, scope);
        TokenCursor cursor = new TokenCursor();
        Verdict verdict = Verdict.PENDING;
        for (Token token : tokens)
        {
            cursor.token = token;
            verdict = eval.feed(token.event, cursor);
        }
        return verdict == Verdict.VALID;
    }

    private static final class TokenCursor implements JsonSource
    {
        private Token token;

        @Override
        public String getString()
        {
            return token.text;
        }

        @Override
        public CharSequence getStringView()
        {
            return token.text;
        }

        @Override
        public boolean isIntegralNumber()
        {
            return !token.text.contains(".") && !token.text.contains("e") && !token.text.contains("E");
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            return new BigDecimal(token.text);
        }

        @Override
        public int getInt()
        {
            return Integer.parseInt(token.text);
        }

        @Override
        public long getLong()
        {
            return Long.parseLong(token.text);
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBuffer getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
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
            JsonSource parser)
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

    // wraps a delegate parser, validating as the caller pulls via next(); a single thread reuses one
    // instance across documents by re-wrapping the delegate and calling reset()
    private final class ValidatingParser implements JsonParserEx
    {
        private final JsonParser delegate;
        private final JsonParserEx delegateEx;
        private final boolean throwing;
        private final Consumer<JsonSchemaDiagnostic> reporter;
        private final List<JsonSchemaDiagnostic> diagnostics;
        private final Eval eval;
        private final ParserSource source;

        private Verdict verdict;

        private ValidatingParser(
            boolean throwing,
            JsonParser delegate,
            Consumer<JsonSchemaDiagnostic> reporter)
        {
            this.delegate = delegate;
            this.delegateEx = delegate instanceof JsonParserEx ex ? ex : null;
            this.throwing = throwing;
            this.reporter = reporter;
            this.diagnostics = throwing || reporter != null ? new ArrayList<>() : null;
            this.eval = diagnostics != null ? eval(new Trace(this::report)) : eval();
            this.source = new ParserSource().wrap(delegate);
            this.verdict = Verdict.PENDING;
        }

        @Override
        public Event next()
        {
            Event event = delegate.next();
            if (verdict == Verdict.PENDING)
            {
                verdict = eval.feed(event, source);
                if (verdict == Verdict.INVALID && throwing)
                {
                    throw new JsonValidationException(diagnostics);
                }
            }
            return event;
        }

        @Override
        public boolean hasNext()
        {
            return delegate.hasNext();
        }

        @Override
        public String getString()
        {
            return delegate.getString();
        }

        @Override
        public boolean isIntegralNumber()
        {
            return delegate.isIntegralNumber();
        }

        @Override
        public int getInt()
        {
            return delegate.getInt();
        }

        @Override
        public long getLong()
        {
            return delegate.getLong();
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            return delegate.getBigDecimal();
        }

        @Override
        public JsonLocation getLocation()
        {
            return delegate.getLocation();
        }

        @Override
        public void close()
        {
            delegate.close();
        }

        // rearms for the next document: resets the delegate's parse state and this validation pass
        @Override
        public void reset()
        {
            if (delegateEx != null)
            {
                delegateEx.reset();
            }
            eval.reset();
            verdict = Verdict.PENDING;
            if (diagnostics != null)
            {
                diagnostics.clear();
            }
        }

        // streaming-over-buffers surface, delegated; validation runs on the next()/getString() pull path
        @Override
        public JsonParserEx wrap(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            delegateEx.wrap(buffer, offset, length);
            return this;
        }

        @Override
        public JsonParserEx wrap(
            DirectBuffer buffer,
            int offset,
            int length,
            boolean last)
        {
            delegateEx.wrap(buffer, offset, length, last);
            return this;
        }

        @Override
        public long position()
        {
            return delegateEx.position();
        }

        @Override
        public int remaining()
        {
            return delegateEx.remaining();
        }

        @Override
        public boolean hasNextEvent()
        {
            return delegateEx.hasNextEvent();
        }

        @Override
        public JsonEvent nextEvent(
            Mode mode)
        {
            return delegateEx.nextEvent(mode);
        }

        @Override
        public CharSequence getStringView()
        {
            return delegateEx != null ? delegateEx.getStringView() : delegate.getString();
        }

        @Override
        public DirectBuffer getSegment()
        {
            return delegateEx.getSegment();
        }

        @Override
        public boolean deferredBytes()
        {
            return delegateEx.deferredBytes();
        }

        private void report(
            JsonSchemaDiagnostic diagnostic)
        {
            diagnostics.add(diagnostic);
            if (reporter != null)
            {
                reporter.accept(diagnostic);
            }
        }
    }

    private final class Validator implements JsonTransform
    {
        // declines segmentable() (validation needs structured events) but relays consumed() upstream
        private final class Decline implements JsonController
        {
            @Override
            public void segmentable()
            {
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
                upstreamControl.consumed(sourceBytes);
            }
        }

        private final JsonController decline = new Decline();

        private JsonController upstreamControl;
        private Eval eval;

        private Validator()
        {
            this.eval = eval();
        }

        @Override
        public Status feed(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            upstreamControl = control;
            Status downstream = sink.feed(decline, source, event);
            Status status;
            if (event.segmented() || event == JsonEvent.START_DOCUMENT || event == JsonEvent.END_DOCUMENT)
            {
                status = downstream == Status.REJECTED ? Status.REJECTED
                    : downstream == Status.SUSPENDED ? Status.SUSPENDED : Status.ADVANCED;
            }
            else
            {
                Verdict verdict = eval.feed(toEvent(event), source);
                if (downstream == Status.REJECTED || verdict == Verdict.INVALID)
                {
                    status = Status.REJECTED;
                }
                else if (verdict == Verdict.VALID)
                {
                    status = Status.COMPLETED;
                }
                else
                {
                    status = downstream == Status.SUSPENDED ? Status.SUSPENDED : Status.ADVANCED;
                }
            }
            return status;
        }

        @Override
        public void reset()
        {
            eval = eval();
        }
    }

    private static Event toEvent(
        JsonEvent event)
    {
        return switch (event)
        {
        case START_OBJECT -> Event.START_OBJECT;
        case END_OBJECT -> Event.END_OBJECT;
        case START_ARRAY -> Event.START_ARRAY;
        case END_ARRAY -> Event.END_ARRAY;
        case KEY_NAME -> Event.KEY_NAME;
        case VALUE_STRING -> Event.VALUE_STRING;
        case VALUE_NUMBER -> Event.VALUE_NUMBER;
        case VALUE_TRUE -> Event.VALUE_TRUE;
        case VALUE_FALSE -> Event.VALUE_FALSE;
        case VALUE_NULL -> Event.VALUE_NULL;
        default -> throw new IllegalArgumentException("not a structured event: " + event);
        };
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
        private int currentIndex;
        private Eval directChild;
        private int directChildMark;
        private Eval[] directChildren;
        private Eval containsChild;
        private int containsMatched;
        private CanonSet uniqueSeen;
        private UniqueCanon uniqueCanon;
        private boolean uniqueElement;
        private final UniqueCanon constCanon;
        private boolean scalarStringOk;
        private Eval refEval;
        private Eval dynEval;
        // items/contains evals reused across this array's elements (reset on next use, not by reset())
        private Eval itemEval;
        private JsonSchemaImpl itemEvalSchema;
        private Eval containsEval;
        // property evals reused across an object's keys, by applicable schema; gated on reused so a
        // single-use object never pays for the map
        private Map<JsonSchemaImpl, Eval> propEvals;
        private boolean reused;

        private final boolean annotate;
        // keys matched/tracked as a CharSequence (no String): a simple object, with neither annotation
        // nor diagnostics needing the materialized key
        private final boolean fastKeys;
        private boolean[] requiredSeen;
        private Set<String> evaluatedProps;
        private BitSet evaluatedItems;
        private String currentKey;
        private Map<String, List<Token>> propValues;
        private List<List<Token>> itemValues;
        private List<Token> childTokens;

        private final DynScope dynScope;

        private Eval(
            Trace trace,
            DynScope parentScope)
        {
            this.trace = trace;
            this.dynScope = context != null ? parentScope.push(context.base.toString()) : parentScope;
            this.annotate = context != null && is2019Plus(context.draft());
            this.constCanon = (constantCanon != null || enumCanons != null) && constString == null && enumStrings == null
                ? new UniqueCanon() : null;
            // allOf branches must all match, so a failing branch is a genuine failure and reports
            // through the shared trace; the remaining combinators select among candidate branches
            // where a non-matching branch is expected, so they evaluate under Trace.NONE and only
            // the combine() summary diagnostic surfaces when the combinator as a whole fails.
            this.allOfEvals = evalsOf(allOf, trace);
            this.anyOfEvals = evalsOf(anyOf, Trace.NONE);
            this.oneOfEvals = evalsOf(oneOf, Trace.NONE);
            this.notEval = notSchema != null ? notSchema.eval(Trace.NONE, dynScope) : null;
            this.ifEval = ifSchema != null ? ifSchema.eval(Trace.NONE, dynScope) : null;
            this.thenEval = thenSchema != null ? thenSchema.eval(Trace.NONE, dynScope) : null;
            this.elseEval = elseSchema != null ? elseSchema.eval(Trace.NONE, dynScope) : null;
            this.dependentSchemaEvals = evalsOfMap(dependentSchemas, Trace.NONE);
            this.trackKeys = required != null || dependentRequired != null || dependentSchemaEvals != null;
            this.fastKeys = simpleObject && !annotate && !trace.active();
        }

        // resets to pre-feed state for reuse: eager combinator children reset in place, lazy children
        // (directChild, refEval, dynEval) dropped, the itemEval/containsEval/propEvals caches left intact
        private void reset()
        {
            reused = true;
            started = false;
            done = false;
            result = null;
            depth = 0;
            directInvalid = false;
            object = false;
            array = false;
            count = 0;
            currentIndex = 0;
            directChild = null;
            directChildMark = 0;
            directChildren = null;
            containsChild = null;
            containsMatched = 0;
            uniqueElement = false;
            scalarStringOk = false;
            refEval = null;
            dynEval = null;
            currentKey = null;
            childTokens = null;
            // per-instance collections are cleared and reused, allocated once lazily in onOpen
            if (seen != null)
            {
                seen.clear();
            }
            if (uniqueSeen != null)
            {
                uniqueSeen.clear();
            }
            if (constCanon != null)
            {
                constCanon.reset();
            }
            if (evaluatedProps != null)
            {
                evaluatedProps.clear();
            }
            if (evaluatedItems != null)
            {
                evaluatedItems.clear();
            }
            if (propValues != null)
            {
                propValues.clear();
            }
            if (itemValues != null)
            {
                itemValues.clear();
            }
            resetEach(allOfEvals);
            resetEach(anyOfEvals);
            resetEach(oneOfEvals);
            resetOne(notEval);
            resetOne(ifEval);
            resetOne(thenEval);
            resetOne(elseEval);
            if (dependentSchemaEvals != null)
            {
                for (Eval eval : dependentSchemaEvals.values())
                {
                    eval.reset();
                }
            }
        }

        private static void resetOne(
            Eval eval)
        {
            if (eval != null)
            {
                eval.reset();
            }
        }

        private static void resetEach(
            Eval[] evals)
        {
            if (evals != null)
            {
                for (Eval eval : evals)
                {
                    eval.reset();
                }
            }
        }

        // reuses the cached element eval when the item schema is index-independent (a single items schema
        // or the ANY fallback); a prefixItems tuple varies per index and falls through to a fresh eval
        private Eval itemEvalFor(
            JsonSchemaImpl schema)
        {
            Eval result;
            if (itemEval != null && itemEvalSchema == schema)
            {
                itemEval.reset();
                result = itemEval;
            }
            else
            {
                result = schema.eval(trace, dynScope);
                itemEval = result;
                itemEvalSchema = schema;
            }
            return result;
        }

        // reuses the cached eval for a property's applicable schema, so object keys (and the same keys
        // across array-of-object elements) need no fresh eval per key
        private Eval propEvalFor(
            JsonSchemaImpl schema)
        {
            Eval result;
            if (reused)
            {
                if (propEvals == null)
                {
                    propEvals = new IdentityHashMap<>();
                }
                result = propEvals.get(schema);
                if (result == null)
                {
                    result = schema.eval(trace, dynScope);
                    propEvals.put(schema, result);
                }
                else
                {
                    result.reset();
                }
            }
            else
            {
                // single-use object: no reuse to amortize a cache, so evaluate fresh
                result = schema.eval(trace, dynScope);
            }
            return result;
        }

        private Verdict feed(
            Event event,
            JsonSource parser)
        {
            Verdict verdict;
            if (done)
            {
                verdict = result;
            }
            else
            {
                if (constCanon != null)
                {
                    constCanon.feed(event, parser);
                }
                if (refApplicator != null && refEval == null)
                {
                    refEval = context.resolve(refApplicator).eval(trace, dynScope);
                }
                if (dynamicRef != null && dynEval == null)
                {
                    dynEval = context.resolveDynamic(dynamicRef, dynScope).eval(trace, dynScope);
                }
                if (recursiveRef && dynEval == null)
                {
                    dynEval = context.resolveRecursive(dynScope).eval(trace, dynScope);
                }
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
            JsonSource parser)
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
            JsonSource parser)
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
                if (fastKeys)
                {
                    if (requiredSeen == null)
                    {
                        requiredSeen = new boolean[requiredKeys.length];
                    }
                    else
                    {
                        Arrays.fill(requiredSeen, false);
                    }
                }
                else if (trackKeys && seen == null)
                {
                    seen = new HashSet<>();
                }
                if (annotate && evaluatedProps == null)
                {
                    evaluatedProps = new HashSet<>();
                }
                if (unevaluatedProperties != null && propValues == null)
                {
                    propValues = new LinkedHashMap<>();
                }
                if (types != null && !types.contains(JsonType.OBJECT))
                {
                    directInvalid = true;
                    trace.report("type", "expected " + typesText() + " but was object", parser);
                }
                break;
            case START_ARRAY:
                array = true;
                if (annotate && evaluatedItems == null)
                {
                    evaluatedItems = new BitSet();
                }
                if (unevaluatedItems != null && itemValues == null)
                {
                    itemValues = new ArrayList<>();
                }
                if (types != null && !types.contains(JsonType.ARRAY))
                {
                    directInvalid = true;
                    trace.report("type", "expected " + typesText() + " but was array", parser);
                }
                break;
            case VALUE_STRING:
                checkStringReport(parser);
                if (constString != null || enumStrings != null)
                {
                    CharSequence value = parser.getStringView();
                    scalarStringOk = constString != null
                        ? charsEqual(constString, value)
                        : containsString(enumStrings, value);
                }
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
                break;
            case VALUE_NULL:
                if (types != null && !types.contains(JsonType.NULL))
                {
                    directInvalid = true;
                    trace.report("type", "expected " + typesText() + " but was null", parser);
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
            JsonSource parser)
        {
            CharSequence value = parser.getStringView();
            int length = Character.codePointCount(value, 0, value.length());
            if (types != null && !types.contains(JsonType.STRING))
            {
                directInvalid = true;
                trace.report("type", "expected " + typesText() + " but was string", parser);
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
            JsonSource parser)
        {
            boolean lexicalIntegral = parser.isIntegralNumber();
            boolean bounded = minimum != null || maximum != null ||
                exclusiveMinimum != null || exclusiveMaximum != null || multipleOf != null;
            if (bounded && integerBounds && lexicalIntegral && parser.getStringView().length() <= LONG_DIGITS)
            {
                // integer instance against integer bounds: compare with long arithmetic, no BigDecimal
                checkIntegerBounds(parser, parser.getLong());
            }
            else
            {
                checkDecimalReport(parser, lexicalIntegral, bounded);
            }
        }

        private void checkIntegerBounds(
            JsonSource parser,
            long value)
        {
            boolean typeOk = types == null || types.contains(JsonType.NUMBER) || types.contains(JsonType.INTEGER);
            if (!typeOk)
            {
                directInvalid = true;
                trace.report("type", "expected " + typesText() + " but was number", parser);
            }
            else if (minimum != null && value < minimum.longValue())
            {
                directInvalid = true;
                trace.report("minimum", value + " < minimum " + minimum, parser);
            }
            else if (maximum != null && value > maximum.longValue())
            {
                directInvalid = true;
                trace.report("maximum", value + " > maximum " + maximum, parser);
            }
            else if (exclusiveMinimum != null && value <= exclusiveMinimum.longValue())
            {
                directInvalid = true;
                trace.report("exclusiveMinimum", value + " <= exclusiveMinimum " + exclusiveMinimum, parser);
            }
            else if (exclusiveMaximum != null && value >= exclusiveMaximum.longValue())
            {
                directInvalid = true;
                trace.report("exclusiveMaximum", value + " >= exclusiveMaximum " + exclusiveMaximum, parser);
            }
            else if (multipleOf != null && value % multipleOf.longValue() != 0)
            {
                directInvalid = true;
                trace.report("multipleOf", value + " is not a multiple of " + multipleOf, parser);
            }
        }

        private void checkDecimalReport(
            JsonSource parser,
            boolean lexicalIntegral,
            boolean bounded)
        {
            boolean modernDraft = context != null && context.draft() != Draft.DRAFT_04;
            // a BigDecimal is needed only for a bounds check, or to decide integrality of a
            // fractional-looking lexeme (1.0, 6e2) under a modern draft — never for an unbounded integer
            BigDecimal value = bounded || modernDraft && !lexicalIntegral ? parser.getBigDecimal() : null;
            boolean integral = modernDraft && value != null
                ? value.signum() == 0 || value.stripTrailingZeros().scale() <= 0
                : lexicalIntegral;
            boolean typeOk = types == null ||
                types.contains(JsonType.NUMBER) ||
                integral && types.contains(JsonType.INTEGER);
            if (!typeOk)
            {
                directInvalid = true;
                trace.report("type", "expected " + typesText() + " but was number", parser);
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
            JsonSource parser)
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
            JsonSource parser)
        {
            if (event == END_OBJECT)
            {
                if (fastKeys)
                {
                    checkRequiredFast();
                }
                else if (required != null && !seen.containsAll(required))
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
            else if (fastKeys)
            {
                onFastKey(parser);
            }
            else
            {
                String key = parser.getString();
                count++;
                currentKey = key;
                if (seen != null)
                {
                    seen.add(key);
                }
                if (propValues != null)
                {
                    childTokens = new ArrayList<>();
                }
                if (propertyNames != null && propertyNames.eval(trace, dynScope).feed(VALUE_STRING, parser) != Verdict.VALID)
                {
                    directInvalid = true;
                    trace.report("propertyNames", "property name '" + key + "' violates propertyNames", parser);
                }
                setApplicableFor(key);
            }
        }

        // matches the key as a CharSequence against the schema's property and required key arrays, so a
        // simple object's keys are validated and required-tracked without materializing a String
        private void onFastKey(
            JsonSource parser)
        {
            CharSequence key = parser.getStringView();
            count++;
            for (int i = 0; i < requiredKeys.length; i++)
            {
                if (charsEqual(requiredKeys[i], key))
                {
                    requiredSeen[i] = true;
                    break;
                }
            }
            JsonSchemaImpl schema = null;
            for (int i = 0; i < propertyKeys.length; i++)
            {
                if (charsEqual(propertyKeys[i], key))
                {
                    schema = propertySchemas[i];
                    break;
                }
            }
            if (schema == null)
            {
                schema = additionalSchema != null ? additionalSchema : additionalAllowed ? ANY : NONE;
            }
            directChildMark = 0;
            directChild = propEvalFor(schema);
            directChildren = null;
        }

        private void checkRequiredFast()
        {
            for (int i = 0; i < requiredKeys.length; i++)
            {
                if (!requiredSeen[i])
                {
                    directInvalid = true;
                    break;
                }
            }
        }

        private void onArrayInner(
            Event event,
            JsonSource parser)
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
                if (contains != null)
                {
                    int min = minContains >= 0 ? minContains : 1;
                    if (containsMatched < min)
                    {
                        directInvalid = true;
                        trace.report("contains", "matched " + containsMatched +
                            " elements, fewer than minContains " + min, parser);
                    }
                    if (maxContains >= 0 && containsMatched > maxContains)
                    {
                        directInvalid = true;
                        trace.report("maxContains", "matched " + containsMatched +
                            " elements, more than maxContains " + maxContains, parser);
                    }
                }
            }
            else
            {
                int index = count;
                count++;
                currentIndex = index;
                directChildMark = trace.push(index);
                directChild = itemEvalFor(elementSchema(index));
                if (annotate && coversItem(index))
                {
                    evaluatedItems.set(index);
                }
                if (contains != null)
                {
                    if (containsEval == null)
                    {
                        containsEval = contains.eval(Trace.NONE, dynScope);
                    }
                    else
                    {
                        containsEval.reset();
                    }
                    containsChild = containsEval;
                }
                if (uniqueItems)
                {
                    if (uniqueSeen == null)
                    {
                        uniqueSeen = new CanonSet();
                        uniqueCanon = new UniqueCanon();
                    }
                    uniqueCanon.reset();
                    uniqueElement = true;
                }
                if (itemValues != null)
                {
                    childTokens = new ArrayList<>();
                }
                routeChildren(event, parser);
            }
        }

        private boolean coversItem(
            int index)
        {
            return items != null ||
                itemsTuple != null && (index < itemsTuple.size() || additionalItems != null);
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
            JsonSource parser)
        {
            if (uniqueElement)
            {
                uniqueCanon.feed(event, parser);
            }
            if (childTokens != null)
            {
                childTokens.add(new Token(event, tokenText(event, parser)));
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
                        if (annotate)
                        {
                            evaluatedItems.set(currentIndex);
                        }
                    }
                    containsChild = null;
                }
            }
            if (complete)
            {
                directChild = null;
                directChildren = null;
                if (uniqueElement)
                {
                    if (!uniqueSeen.add(uniqueCanon.result))
                    {
                        directInvalid = true;
                        trace.report("uniqueItems", "duplicate array element", parser);
                    }
                    uniqueElement = false;
                }
                if (childTokens != null)
                {
                    if (propValues != null && currentKey != null)
                    {
                        propValues.put(currentKey, childTokens);
                    }
                    else if (itemValues != null)
                    {
                        itemValues.add(childTokens);
                    }
                    childTokens = null;
                }
            }
        }

        private void setApplicableFor(
            String key)
        {
            directChildMark = trace.push(key);
            boolean matched;
            if (patternProperties == null)
            {
                JsonSchemaImpl schema = properties != null ? properties.get(key) : null;
                matched = schema != null;
                if (schema == null)
                {
                    schema = additionalSchema != null ? additionalSchema : additionalAllowed ? ANY : NONE;
                }
                directChild = propEvalFor(schema);
                directChildren = null;
            }
            else
            {
                List<JsonSchemaImpl> applicable = new ArrayList<>();
                matched = false;
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
            if (annotate && (matched || hasAdditional))
            {
                evaluatedProps.add(key);
            }
        }

        private void setApplicable(
            List<JsonSchemaImpl> applicable)
        {
            if (applicable.size() == 1)
            {
                directChild = propEvalFor(applicable.get(0));
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
            JsonSource parser)
        {
            feedOne(refEval, event, parser);
            feedOne(dynEval, event, parser);
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
            JsonSource parser)
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
            JsonSource parser)
        {
            if (eval != null && !eval.done)
            {
                eval.feed(event, parser);
            }
        }

        private Verdict combine(
            JsonSource parser)
        {
            boolean valid = !directInvalid;
            if (valid && refEval != null && refEval.result != Verdict.VALID)
            {
                valid = false;
                trace.report("$ref", "instance failed referenced schema", parser);
            }
            if (valid && dynEval != null && dynEval.result != Verdict.VALID)
            {
                valid = false;
                trace.report("$dynamicRef", "instance failed dynamically referenced schema", parser);
            }
            if (valid && constString != null)
            {
                valid = scalarStringOk;
                if (!valid)
                {
                    trace.report("const", "value does not equal const", parser);
                }
            }
            else if (valid && enumStrings != null)
            {
                valid = scalarStringOk;
                if (!valid)
                {
                    trace.report("enum", "value not in enum", parser);
                }
            }
            else if (valid && constCanon != null)
            {
                String canon = constCanon.result;
                if (constantCanon != null && !constantCanon.equals(canon))
                {
                    valid = false;
                    trace.report("const", "value does not equal const", parser);
                }
                else if (enumCanons != null && !enumCanons.contains(canon))
                {
                    valid = false;
                    trace.report("enum", "value not in enum", parser);
                }
            }
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
            if (valid && annotate)
            {
                mergeAnnotations();
            }
            if (valid && propValues != null)
            {
                valid = checkUnevaluatedProperties(parser);
            }
            if (valid && itemValues != null)
            {
                valid = checkUnevaluatedItems(parser);
            }
            return valid ? Verdict.VALID : Verdict.INVALID;
        }

        private void mergeAnnotations()
        {
            mergeAnnotation(refEval);
            mergeAnnotation(dynEval);
            mergeAnnotations(allOfEvals);
            mergeAnnotations(anyOfEvals);
            mergeAnnotations(oneOfEvals);
            if (ifEval != null && ifEval.result == Verdict.VALID)
            {
                mergeAnnotation(ifEval);
                mergeAnnotation(thenEval);
            }
            else if (ifEval != null)
            {
                mergeAnnotation(elseEval);
            }
            if (dependentSchemaEvals != null && seen != null)
            {
                for (Map.Entry<String, Eval> entry : dependentSchemaEvals.entrySet())
                {
                    if (seen.contains(entry.getKey()))
                    {
                        mergeAnnotation(entry.getValue());
                    }
                }
            }
        }

        private void mergeAnnotations(
            Eval[] evals)
        {
            if (evals != null)
            {
                for (Eval eval : evals)
                {
                    mergeAnnotation(eval);
                }
            }
        }

        private void mergeAnnotation(
            Eval eval)
        {
            if (eval != null && eval.result == Verdict.VALID)
            {
                if (eval.evaluatedProps != null && evaluatedProps != null)
                {
                    evaluatedProps.addAll(eval.evaluatedProps);
                }
                if (eval.evaluatedItems != null && evaluatedItems != null)
                {
                    evaluatedItems.or(eval.evaluatedItems);
                }
            }
        }

        private boolean checkUnevaluatedProperties(
            JsonSource parser)
        {
            boolean valid = true;
            for (Map.Entry<String, List<Token>> entry : propValues.entrySet())
            {
                if (!evaluatedProps.contains(entry.getKey()))
                {
                    if (unevaluatedProperties.validateTokens(entry.getValue(), dynScope))
                    {
                        evaluatedProps.add(entry.getKey());
                    }
                    else
                    {
                        valid = false;
                        trace.report("unevaluatedProperties",
                            "property '" + entry.getKey() + "' failed unevaluatedProperties", parser);
                    }
                }
            }
            return valid;
        }

        private boolean checkUnevaluatedItems(
            JsonSource parser)
        {
            boolean valid = true;
            for (int index = 0; index < itemValues.size(); index++)
            {
                if (!evaluatedItems.get(index))
                {
                    if (unevaluatedItems.validateTokens(itemValues.get(index), dynScope))
                    {
                        evaluatedItems.set(index);
                    }
                    else
                    {
                        valid = false;
                        trace.report("unevaluatedItems",
                            "item at index " + index + " failed unevaluatedItems", parser);
                    }
                }
            }
            return valid;
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
                    result[i] = schemas.get(i).eval(childTrace, dynScope);
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
                    result.put(entry.getKey(), entry.getValue().eval(childTrace, dynScope));
                }
            }
            return result;
        }
    }
}
