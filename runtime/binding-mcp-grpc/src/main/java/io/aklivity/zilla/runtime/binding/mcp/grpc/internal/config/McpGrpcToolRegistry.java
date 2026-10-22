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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.json.JsonObject;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolAnnotationsConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolConfig;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMethod;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufService;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJsonSchema;

public final class McpGrpcToolRegistry
{
    private final List<McpGrpcTool> tools;
    private final Map<String, McpGrpcTool> toolsByName;
    private final List<String> denied;

    public static McpGrpcToolRegistry of(
        ProtobufSchema schema,
        List<McpGrpcToolConfig> overrides,
        List<McpGrpcRouteConfig> routes)
    {
        final List<McpGrpcRouteConfig> selectors = routes != null && !routes.isEmpty()
            ? routes
            : List.of(new McpGrpcRouteConfig(null, null, null));

        final List<Candidate> candidates = select(schema, selectors);
        final List<String> denied = new ArrayList<>();
        final List<Candidate> accepted = deny(candidates, denied);

        final Map<String, McpGrpcToolConfig> overridesByName = new HashMap<>();
        if (overrides != null)
        {
            overrides.forEach(override -> overridesByName.put(override.name, override));
        }

        final List<McpGrpcTool> tools = new ArrayList<>();
        for (Candidate candidate : accepted)
        {
            tools.add(tool(schema, candidate, overridesByName.get(candidate.name)));
        }

        return new McpGrpcToolRegistry(tools, denied);
    }

    public List<McpGrpcTool> tools()
    {
        return tools;
    }

    public McpGrpcTool tool(
        String name)
    {
        return toolsByName.get(name);
    }

    public List<String> denied()
    {
        return denied;
    }

    private McpGrpcToolRegistry(
        List<McpGrpcTool> tools,
        List<String> denied)
    {
        this.tools = tools;
        this.toolsByName = new LinkedHashMap<>();
        tools.forEach(tool -> toolsByName.put(tool.name, tool));
        this.denied = denied;
    }

    private static List<Candidate> select(
        ProtobufSchema schema,
        List<McpGrpcRouteConfig> routes)
    {
        final Set<String> claimed = new HashSet<>();
        final Map<String, Pattern> globs = new HashMap<>();
        final List<Candidate> candidates = new ArrayList<>();

        for (McpGrpcRouteConfig route : routes)
        {
            for (ProtobufService service : schema.services())
            {
                for (ProtobufMethod method : service.methods())
                {
                    final String key = "%s/%s".formatted(service.name(), method.name());

                    if (!method.clientStreaming() && !method.serverStreaming() &&
                        !claimed.contains(key) && matches(route, key, method, globs))
                    {
                        claimed.add(key);

                        final String name = route.tool != null
                            ? route.tool
                            : McpGrpcNames.toolName(service.name(), method.name());
                        candidates.add(new Candidate(name, service.name(), method, route));
                    }
                }
            }
        }

        return candidates;
    }

    private static boolean matches(
        McpGrpcRouteConfig route,
        String key,
        ProtobufMethod method,
        Map<String, Pattern> globs)
    {
        boolean matched = true;

        if (route.method != null)
        {
            matched = globs.computeIfAbsent(route.method, McpGrpcToolRegistry::glob).matcher(key).matches();
        }
        else if (route.option != null)
        {
            final JsonObject options = method.options();
            matched = route.option.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(options.get(entry.getKey())));
        }

        return matched;
    }

    private static Pattern glob(
        String pattern)
    {
        final StringBuilder regex = new StringBuilder();
        for (String literal : pattern.split("\\*", -1))
        {
            if (regex.length() > 0)
            {
                regex.append(".*");
            }
            regex.append(Pattern.quote(literal));
        }

        return Pattern.compile(regex.toString());
    }

    private static List<Candidate> deny(
        List<Candidate> candidates,
        List<String> denied)
    {
        final Map<String, Integer> counts = new HashMap<>();
        candidates.forEach(candidate -> counts.merge(candidate.name, 1, Integer::sum));

        final List<Candidate> accepted = new ArrayList<>();
        for (Candidate candidate : candidates)
        {
            final String key = "%s/%s".formatted(candidate.service, candidate.method.name());

            if (counts.get(candidate.name) > 1)
            {
                denied.add("%s: tool name %s collides with another method".formatted(key, candidate.name));
            }
            else if (!McpGrpcNames.validToolName(candidate.name))
            {
                denied.add("%s: tool name %s is not 1 to %d characters of letters, digits, underscore or hyphen"
                    .formatted(key, candidate.name, McpGrpcNames.MAX_TOOL_NAME_LENGTH));
            }
            else
            {
                accepted.add(candidate);
            }
        }

        return accepted;
    }

    private static McpGrpcTool tool(
        ProtobufSchema schema,
        Candidate candidate,
        McpGrpcToolConfig override)
    {
        final ProtobufMethod method = candidate.method;
        final String summary = override != null ? override.summary : null;

        final JsonObject inputSchema = ProtobufJsonSchema.of(schema, method.inputType());
        final JsonObject outputSchema = summary != null
            ? ProtobufJsonSchema.of(schema, method.outputType())
            : null;

        return new McpGrpcTool(
            candidate.name,
            candidate.service,
            method.name(),
            method.inputType(),
            method.outputType(),
            override != null ? override.title : null,
            override != null && override.description != null
                ? override.description
                : "%s/%s".formatted(candidate.service, method.name()),
            summary,
            annotations(override),
            inputSchema,
            outputSchema,
            candidate.route);
    }

    private static McpGrpcToolAnnotationsConfig annotations(
        McpGrpcToolConfig override)
    {
        final McpGrpcToolAnnotationsConfig configured = override != null ? override.annotations : null;

        return McpGrpcToolAnnotationsConfig.builder()
            .readOnlyHint(configured != null ? configured.readOnlyHint : null)
            .destructiveHint(configured != null ? configured.destructiveHint : null)
            .idempotentHint(configured != null ? configured.idempotentHint : null)
            .openWorldHint(configured != null && configured.openWorldHint != null ? configured.openWorldHint : Boolean.FALSE)
            .build();
    }

    private static final class Candidate
    {
        private final String name;
        private final String service;
        private final ProtobufMethod method;
        private final McpGrpcRouteConfig route;

        private Candidate(
            String name,
            String service,
            ProtobufMethod method,
            McpGrpcRouteConfig route)
        {
            this.name = name;
            this.service = service;
            this.method = method;
            this.route = route;
        }
    }
}
