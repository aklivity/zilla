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
package io.aklivity.zilla.specs.binding.mcp.schema.registry.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import org.junit.Test;

/**
 * Walks the {@code tools/list} fixture this binding's composite generates from the bundled
 * {@code mcp_openapi} spec (see {@code streams/mcp/tools.list}) and asserts the same cross-tool
 * invariants as {@code binding-mcp-kafka}'s {@code ToolSchemaConsistencyTest}: every tool declares
 * safety annotations, and tools sharing a concept name field it the same way. See
 * https://github.com/aklivity/zilla/issues/2249.
 *
 * <p>This binding's tools are derived by the {@code binding-mcp-openapi} annotation mechanism
 * (#2222), which always declares {@code openWorldHint} but only adds {@code readOnlyHint}/
 * {@code destructiveHint}/{@code idempotentHint} when the value differs from the MCP spec default.
 * The completeness check here is scoped to that binding's own convention -- a present annotations
 * object with an explicit {@code openWorldHint} -- rather than requiring specific hint keys, so it
 * does not fight the shared derivation mechanism out from under #2222.
 */
public class ToolSchemaConsistencyTest
{
    private static final String TOOLS_LIST_FIXTURE =
        "/io/aklivity/zilla/specs/binding/mcp/schema/registry/streams/mcp/tools.list/client.rpt";

    // no known field-naming clash across this binding's tools yet; seeded empty, following the same
    // "small, manually-maintained known synonym groups" convention as binding-mcp-kafka's list.
    private static final Map<String, String> LEGACY_FIELD_ALIASES = Map.of();

    @Test
    public void shouldDeclareAnnotationsForEveryTool() throws IOException
    {
        final JsonArray tools = loadTools(TOOLS_LIST_FIXTURE);

        final List<String> violations = new ArrayList<>();
        for (JsonValue value : tools)
        {
            final JsonObject tool = value.asJsonObject();
            final String name = tool.getString("name");
            final JsonObject annotations = tool.getJsonObject("annotations");

            if (annotations == null)
            {
                violations.add(name + ": missing annotations object");
            }
            else if (!annotations.containsKey("openWorldHint"))
            {
                violations.add(name + ": annotations missing openWorldHint " + annotations);
            }
        }

        assertThat(violations, empty());
    }

    @Test
    public void shouldUseConsistentFieldNamingAcrossTools() throws IOException
    {
        final JsonArray tools = loadTools(TOOLS_LIST_FIXTURE);

        final List<String> violations = new ArrayList<>();
        for (JsonValue value : tools)
        {
            final JsonObject tool = value.asJsonObject();
            final String name = tool.getString("name");

            collectFieldNames(tool.getJsonObject("inputSchema")).forEach(field ->
            {
                final String canonical = LEGACY_FIELD_ALIASES.get(field);
                if (canonical != null)
                {
                    violations.add(name + ": inputSchema field \"" + field + "\" should be \"" + canonical + "\"");
                }
            });
        }

        assertThat(violations, empty());
    }

    private static List<String> collectFieldNames(
        JsonObject schema)
    {
        final List<String> fields = new ArrayList<>();
        collectFieldNames(schema, fields);
        return fields;
    }

    private static void collectFieldNames(
        JsonObject schema,
        List<String> fields)
    {
        if (schema == null)
        {
            return;
        }

        final JsonObject properties = schema.getJsonObject("properties");
        if (properties != null)
        {
            properties.forEach((field, value) ->
            {
                fields.add(field);
                if (value.getValueType() == JsonValue.ValueType.OBJECT)
                {
                    collectFieldNames(value.asJsonObject(), fields);
                }
            });
        }

        final JsonObject items = schema.getJsonObject("items");
        if (items != null)
        {
            collectFieldNames(items, fields);
        }
    }

    private static JsonArray loadTools(
        String resourcePath) throws IOException
    {
        final String json = extractToolsListJson(resourcePath);

        try (JsonReader reader = Json.createReader(new StringReader(json)))
        {
            return reader.readObject().getJsonArray("tools");
        }
    }

    // the tools/list JSON response body is authored in the k3po .rpt fixture as a sequence of adjacent
    // single-quoted string literals (one per line, implicitly concatenated); reconstruct the JSON text by
    // collecting those literals from the line introducing the "tools" array through the line that closes it
    private static String extractToolsListJson(
        String resourcePath) throws IOException
    {
        final StringBuilder json = new StringBuilder();

        try (InputStream input = ToolSchemaConsistencyTest.class.getResourceAsStream(resourcePath))
        {
            if (input == null)
            {
                throw new IOException("Fixture not found: " + resourcePath);
            }

            final BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            boolean collecting = false;
            String line;
            while ((line = reader.readLine()) != null)
            {
                final String trimmed = line.trim();
                if (!collecting && trimmed.contains("'{\"tools\":"))
                {
                    collecting = true;
                }

                if (collecting)
                {
                    final int first = trimmed.indexOf('\'');
                    final int last = trimmed.lastIndexOf('\'');
                    if (first >= 0 && last > first)
                    {
                        final String segment = trimmed.substring(first + 1, last);
                        json.append(segment);
                        if (segment.endsWith("]}"))
                        {
                            break;
                        }
                    }
                }
            }
        }

        return json.toString();
    }
}
