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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.stream;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.JsonObject;

/**
 * The bundled, LLM-tuned tool-name to Schema Registry OpenAPI operation mapping described in
 * https://github.com/aklivity/zilla/issues/1672. Fixed and hard-coded, matching the bundled
 * classpath spec resource this binding ships; not derived from operator configuration.
 */
public final class McpSchemaRegistryOperations
{
    private static final Map<String, McpSchemaRegistryOperation> BY_TOOL = new LinkedHashMap<>();

    static
    {
        add("list_subjects", "list", "GET", "/subjects", false, "subjects", "List subjects");
        add("describe_subject", "getSchemaVersions", "GET", "/subjects/{subject}/versions", false, "versions",
            "Describe subject");
        add("get_schema", "getSchemaByVersion", "GET", "/subjects/{subject}/versions/{version}", false, null,
            "Get schema");
        add("register_schema", "register", "POST", "/subjects/{subject}/versions", true, null, "Register schema");
        add("delete_subject", "deleteSubject", "DELETE", "/subjects/{subject}", false, "versions", "Delete subject");
        add("delete_schema_version", "deleteSchemaVersion", "DELETE", "/subjects/{subject}/versions/{version}", false,
            "version", "Delete schema version");
        add("check_compatibility", "testCompatibilityBySubjectName_1", "POST",
            "/compatibility/subjects/{subject}/versions/{version}", true, null, "Check compatibility");
        add("get_compatibility", "getSubjectLevelConfig", "GET", "/config/{subject}", false, null,
            "Get compatibility");
        add("set_compatibility", "updateSubjectLevelConfig", "PUT", "/config/{subject}", true, null,
            "Set compatibility");
        add("list_contexts", "getContexts", "GET", "/contexts", false, "contexts", "List contexts");
    }

    private McpSchemaRegistryOperations()
    {
    }

    public static McpSchemaRegistryOperation lookup(
        String tool)
    {
        return BY_TOOL.get(tool);
    }

    public static String resolvePath(
        McpSchemaRegistryOperation operation,
        JsonObject args)
    {
        String path = operation.pathTemplate;
        if (path.contains("{subject}"))
        {
            path = path.replace("{subject}", encode(args.getString("subject", "")));
        }
        if (path.contains("{version}"))
        {
            path = path.replace("{version}", encode(args.getString("version", "")));
        }
        return path;
    }

    public static JsonObject resolveRequestBody(
        JsonObject args)
    {
        jakarta.json.JsonObjectBuilder body = jakarta.json.Json.createObjectBuilder();
        for (Map.Entry<String, jakarta.json.JsonValue> entry : args.entrySet())
        {
            if (!"subject".equals(entry.getKey()) && !"version".equals(entry.getKey()))
            {
                body.add(entry.getKey(), entry.getValue());
            }
        }
        return body.build();
    }

    private static String encode(
        String value)
    {
        String encoded;
        try
        {
            encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        }
        catch (UnsupportedEncodingException ex)
        {
            encoded = value;
        }
        return encoded;
    }

    private static void add(
        String tool,
        String operationId,
        String method,
        String pathTemplate,
        boolean hasRequestBody,
        String wrapKey,
        String summary)
    {
        BY_TOOL.put(tool, new McpSchemaRegistryOperation(
            tool, operationId, method, pathTemplate, hasRequestBody, wrapKey, summary));
    }
}
