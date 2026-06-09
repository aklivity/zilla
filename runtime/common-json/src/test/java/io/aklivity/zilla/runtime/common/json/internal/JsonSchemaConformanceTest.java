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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.json.stream.JsonParser;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchema.Draft;
import io.aklivity.zilla.runtime.common.json.StreamingJson;

/**
 * Runs the vendored subset of the official JSON Schema Test Suite (see
 * {@code conformance/README.md}) against {@link JsonSchema} for drafts 04, 06, and 07.
 * <p>
 * Each suite file is an array of groups {@code [{description, schema, tests:[{description,
 * data, valid}]}]}. A group whose schema uses a keyword outside the validator's surface
 * (e.g. a structural {@code enum}/{@code const}) compiles to an
 * {@link UnsupportedOperationException} and is reported as a skipped node rather than a failure.
 */
class JsonSchemaConformanceTest
{
    private static final String ROOT = "io/aklivity/zilla/runtime/common/json/conformance/";

    private static final String[] DRAFT7_FILES =
    {
        "type", "enum", "const", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
        "multipleOf", "minLength", "maxLength", "pattern", "items", "additionalItems", "contains",
        "uniqueItems", "minItems", "maxItems", "properties", "required", "additionalProperties",
        "minProperties", "maxProperties", "patternProperties", "propertyNames", "dependencies",
        "allOf", "anyOf", "oneOf", "not", "if-then-else", "boolean_schema", "definitions"
    };

    private static final String[] DRAFT6_FILES =
    {
        "type", "enum", "const", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
        "multipleOf", "minLength", "maxLength", "pattern", "items", "additionalItems", "contains",
        "uniqueItems", "minItems", "maxItems", "properties", "required", "additionalProperties",
        "minProperties", "maxProperties", "patternProperties", "propertyNames", "dependencies",
        "allOf", "anyOf", "oneOf", "not", "boolean_schema", "definitions"
    };

    private static final String[] DRAFT4_FILES =
    {
        "type", "enum", "minimum", "maximum", "multipleOf", "minLength", "maxLength", "pattern",
        "items", "additionalItems", "uniqueItems", "minItems", "maxItems", "properties", "required",
        "additionalProperties", "minProperties", "maxProperties", "patternProperties", "dependencies",
        "allOf", "anyOf", "oneOf", "not", "definitions"
    };

    private static final String[] DRAFT2019_FILES =
    {
        "dependentRequired", "dependentSchemas"
    };

    private static final String[] DRAFT2020_FILES =
    {
        "dependentRequired", "dependentSchemas"
    };

    @TestFactory
    Stream<DynamicNode> draft7()
    {
        return suite(Draft.DRAFT_07, "draft7", DRAFT7_FILES);
    }

    @TestFactory
    Stream<DynamicNode> draft6()
    {
        return suite(Draft.DRAFT_06, "draft6", DRAFT6_FILES);
    }

    @TestFactory
    Stream<DynamicNode> draft4()
    {
        return suite(Draft.DRAFT_04, "draft4", DRAFT4_FILES);
    }

    @TestFactory
    Stream<DynamicNode> draft201909()
    {
        return suite(Draft.DRAFT_2019_09, "draft2019-09", DRAFT2019_FILES);
    }

    @TestFactory
    Stream<DynamicNode> draft202012()
    {
        return suite(Draft.DRAFT_2020_12, "draft2020-12", DRAFT2020_FILES);
    }

    private static Stream<DynamicNode> suite(
        Draft draft,
        String dir,
        String[] files)
    {
        return Stream.of(files)
            .map(file -> dynamicContainer(file, groups(draft, dir, file)));
    }

    private static Stream<DynamicNode> groups(
        Draft draft,
        String dir,
        String file)
    {
        JsonNode root = JsonNode.parse(read(ROOT + dir + "/" + file + ".json"));
        List<DynamicNode> nodes = new ArrayList<>();
        for (JsonNode group : root.elements())
        {
            nodes.add(group(draft, group));
        }
        return nodes.stream();
    }

    private static DynamicNode group(
        Draft draft,
        JsonNode group)
    {
        String description = group.get("description").string();
        String schema = toJson(group.get("schema"));
        DynamicNode node;
        try
        {
            JsonSchema compiled = JsonSchema.of(schema, JsonSchemaConformanceTest::resolveRemote, draft);
            List<DynamicNode> cases = new ArrayList<>();
            for (JsonNode test : group.get("tests").elements())
            {
                cases.add(testCase(compiled, schema, test));
            }
            node = dynamicContainer(description, cases.stream());
        }
        catch (UnsupportedOperationException ex)
        {
            node = dynamicTest(description, () -> abort("unsupported keyword: " + ex.getMessage()));
        }
        return node;
    }

    private static DynamicNode testCase(
        JsonSchema compiled,
        String schema,
        JsonNode test)
    {
        String description = test.get("description").string();
        String data = toJson(test.get("data"));
        boolean expected = test.get("valid").isTrue();
        return dynamicTest(description, () ->
            assertEquals(expected, compiled.validate(parserFor(data + " ")),
                () -> "schema verdict mismatch for schema: " + schema + " data: " + data));
    }

    private static String toJson(
        JsonNode node)
    {
        StringBuilder sb = new StringBuilder();
        write(node, sb);
        return sb.toString();
    }

    private static void write(
        JsonNode node,
        StringBuilder sb)
    {
        switch (node.kind())
        {
        case OBJECT:
            sb.append('{');
            boolean firstMember = true;
            for (Map.Entry<String, JsonNode> entry : node.members().entrySet())
            {
                if (!firstMember)
                {
                    sb.append(',');
                }
                quote(entry.getKey(), sb);
                sb.append(':');
                write(entry.getValue(), sb);
                firstMember = false;
            }
            sb.append('}');
            break;
        case ARRAY:
            sb.append('[');
            boolean firstElement = true;
            for (JsonNode element : node.elements())
            {
                if (!firstElement)
                {
                    sb.append(',');
                }
                write(element, sb);
                firstElement = false;
            }
            sb.append(']');
            break;
        case STRING:
            quote(node.string(), sb);
            break;
        case NUMBER:
            sb.append(node.string());
            break;
        case TRUE:
            sb.append("true");
            break;
        case FALSE:
            sb.append("false");
            break;
        default:
            sb.append("null");
            break;
        }
    }

    private static void quote(
        String value,
        StringBuilder sb)
    {
        sb.append('"');
        for (int i = 0; i < value.length(); i++)
        {
            char ch = value.charAt(i);
            switch (ch)
            {
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\f':
                sb.append("\\f");
                break;
            default:
                if (ch < 0x20)
                {
                    sb.append(String.format("\\u%04x", (int) ch));
                }
                else
                {
                    sb.append(ch);
                }
                break;
            }
        }
        sb.append('"');
    }

    private static String resolveRemote(
        String uri)
    {
        String resource = null;
        if (uri.startsWith("http://json-schema.org/") && uri.endsWith("/schema"))
        {
            String version = uri.substring("http://json-schema.org/".length(), uri.length() - "/schema".length());
            resource = ROOT + "metaschema/" + version + ".json";
        }
        return resource != null && exists(resource) ? read(resource) : null;
    }

    private static boolean exists(
        String resource)
    {
        return JsonSchemaConformanceTest.class.getClassLoader().getResource(resource) != null;
    }

    private static String read(
        String resource)
    {
        try (InputStream in = JsonSchemaConformanceTest.class.getClassLoader().getResourceAsStream(resource))
        {
            if (in == null)
            {
                throw new IllegalStateException("missing conformance resource: " + resource);
            }
            return new String(in.readAllBytes(), UTF_8);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return StreamingJson.createParser(in);
    }
}
