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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpResourceConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpToolConfig;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class McpHttpBindingConfig
{
    private static final String CREDENTIALS_TOKEN = "{credentials}";
    private static final String IDENTITY_TOKEN = "{identity}";

    public final long id;
    public final McpHttpOptionsConfig options;
    public final List<McpHttpRouteConfig> routes;
    public final GuardHandler guard;

    private final EngineContext context;
    private final ToLongFunction<String> resolveId;
    private final Map<String, McpHttpToolConfig> toolsByName;
    private final Map<String, McpHttpResourceConfig> resourcesByName;
    private final List<ResourceMatcher> resourceMatchers;
    private final Map<String, List<String>> resourceCaptures;
    private final Map<ModelConfig, JsonSchema> jsonSchemas;
    private final Map<McpHttpRouteConfig, List<String>> unsatisfiedAccessors;
    private final Function<ModelConfig, JsonSchema> resolveJsonSchemaFn = this::resolveJsonSchema;

    // memoized list replies; derived solely from static binding config, built once on first request
    private byte[] toolsListJson;
    private byte[] resourcesListJson;
    private byte[] resourcesTemplatesListJson;

    public McpHttpBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        this.id = binding.id;
        this.context = context;
        this.resolveId = binding.resolveId;
        this.options = (McpHttpOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(McpHttpRouteConfig::new)
            .collect(toList());

        this.toolsByName = Optional.ofNullable(options)
            .map(o -> o.tools)
            .stream()
            .flatMap(List::stream)
            .collect(toMap(
                tool -> tool.name,
                tool -> tool,
                (existing, replacement) -> existing,
                LinkedHashMap::new));

        this.resourcesByName = Optional.ofNullable(options)
            .map(o -> o.resources)
            .stream()
            .flatMap(List::stream)
            .collect(toMap(
                resource -> resource.name,
                resource -> resource,
                (existing, replacement) -> existing,
                LinkedHashMap::new));

        this.resourceMatchers = resourcesByName.values().stream()
            .filter(resource -> resource.uri != null)
            .map(ResourceMatcher::new)
            .collect(toList());

        this.resourceCaptures = resourceMatchers.stream()
            .collect(toMap(
                matcher -> matcher.resource.name,
                matcher -> List.copyOf(matcher.paramNames),
                (existing, replacement) -> existing,
                LinkedHashMap::new));

        GuardHandler guard = null;
        if (options != null && options.authorization != null)
        {
            final long guardId = resolveId.applyAsLong(options.authorization.name);
            guard = context.supplyGuard(guardId);
        }
        this.guard = guard;

        this.jsonSchemas = new IdentityHashMap<>();
        this.unsatisfiedAccessors = new IdentityHashMap<>();
    }

    public McpHttpRouteConfig resolveTool(
        String name,
        long authorization)
    {
        McpHttpRouteConfig mapping = null;
        boolean authorized = true;
        for (McpHttpRouteConfig route : routes)
        {
            if (route.appliesToTool(name))
            {
                authorized &= route.authorized(authorization);
                if (route.with != null && mapping == null)
                {
                    mapping = route;
                }
            }
        }
        return authorized && mapping != null ? mapping : null;
    }

    public McpHttpResourceConfig resolveResource(
        String uri,
        Map<String, String> params)
    {
        McpHttpResourceConfig matched = null;
        for (ResourceMatcher matcher : resourceMatchers)
        {
            if (matcher.matches(uri, params))
            {
                matched = matcher.resource;
                break;
            }
        }
        return matched;
    }

    public McpHttpRouteConfig resolveResourceRoute(
        String name,
        long authorization)
    {
        McpHttpRouteConfig mapping = null;
        boolean authorized = true;
        for (McpHttpRouteConfig route : routes)
        {
            if (route.appliesToResource(name))
            {
                authorized &= route.authorized(authorization);
                if (route.with != null && mapping == null)
                {
                    mapping = route;
                }
            }
        }
        return authorized && mapping != null ? mapping : null;
    }

    public List<GuardedConfig> toolGuarded(
        String name)
    {
        final List<GuardedConfig> result = new ArrayList<>();
        for (McpHttpRouteConfig route : routes)
        {
            if (route.appliesToTool(name))
            {
                result.addAll(route.guarded);
            }
        }
        return result;
    }

    public List<GuardedConfig> resourceGuarded(
        String name)
    {
        final List<GuardedConfig> result = new ArrayList<>();
        for (McpHttpRouteConfig route : routes)
        {
            if (route.appliesToResource(name))
            {
                result.addAll(route.guarded);
            }
        }
        return result;
    }

    public Collection<McpHttpToolConfig> tools()
    {
        return toolsByName.values();
    }

    public Collection<McpHttpResourceConfig> resources()
    {
        return resourcesByName.values();
    }

    public byte[] toolsListJson()
    {
        return toolsListJson;
    }

    public void toolsListJson(
        byte[] json)
    {
        this.toolsListJson = json;
    }

    public byte[] resourcesListJson()
    {
        return resourcesListJson;
    }

    public void resourcesListJson(
        byte[] json)
    {
        this.resourcesListJson = json;
    }

    public byte[] resourcesTemplatesListJson()
    {
        return resourcesTemplatesListJson;
    }

    public void resourcesTemplatesListJson(
        byte[] json)
    {
        this.resourcesTemplatesListJson = json;
    }

    public McpHttpResourceConfig resource(
        String name)
    {
        return resourcesByName.get(name);
    }

    public void resolveCredentials(
        long authorization,
        Map<String, String> headers)
    {
        final McpHttpAuthorizationConfig authorizationConfig = options != null ? options.authorization : null;
        if (authorizationConfig != null && authorizationConfig.headers != null && guard != null)
        {
            final String credentials = guard.credentials(authorization);
            final String identity = guard.identity(authorization);
            for (Map.Entry<String, String> entry : authorizationConfig.headers.entrySet())
            {
                final String value = substitute(entry.getValue(), credentials, identity);
                if (value != null)
                {
                    headers.put(entry.getKey(), value);
                }
            }
        }
    }

    private static String substitute(
        String template,
        String credentials,
        String identity)
    {
        String value = template;
        if (value.contains(CREDENTIALS_TOKEN))
        {
            value = credentials != null ? value.replace(CREDENTIALS_TOKEN, credentials) : null;
        }
        if (value != null && value.contains(IDENTITY_TOKEN))
        {
            value = identity != null ? value.replace(IDENTITY_TOKEN, identity) : null;
        }
        return value;
    }

    public McpHttpToolConfig tool(
        String name)
    {
        return toolsByName.get(name);
    }

    public JsonSchema jsonSchema(
        ModelConfig model)
    {
        return model != null
            ? jsonSchemas.computeIfAbsent(model, resolveJsonSchemaFn)
            : null;
    }

    private JsonSchema resolveJsonSchema(
        ModelConfig model)
    {
        final String text = schemaText(model);
        return text != null ? JsonSchema.of(text) : null;
    }

    public String schemaText(
        ModelConfig model)
    {
        String text = null;
        if (model.cataloged != null && !model.cataloged.isEmpty())
        {
            final CatalogedConfig cataloged = model.cataloged.get(0);
            final long catalogId = cataloged.id != 0L
                ? cataloged.id
                : resolveId.applyAsLong(cataloged.name);
            final CatalogHandler handler = context.supplyCatalog(catalogId);

            if (handler != null && cataloged.schemas != null && !cataloged.schemas.isEmpty())
            {
                final SchemaConfig subject = cataloged.schemas.get(0);
                int schemaId = subject.id != CatalogHandler.NO_SCHEMA_ID
                    ? subject.id
                    : handler.resolve(subject.subject, subject.version);
                text = handler.resolve(schemaId);
            }
        }
        return text;
    }

    public List<String> unsatisfiedAccessors(
        McpHttpRouteConfig route)
    {
        List<String> verdict = unsatisfiedAccessors.get(route);
        if (verdict == null)
        {
            List<String> unsatisfied = null;
            boolean deferred = false;

            if (route.tool != null && !route.argAccessors.isEmpty())
            {
                final McpHttpToolConfig tool = toolsByName.get(route.tool);
                final ModelConfig input = tool != null ? tool.input : null;
                final String text = input != null ? schemaText(input) : null;
                if (input != null && (text == null || text.isBlank()))
                {
                    deferred = true;
                }
                else if (text != null)
                {
                    for (String accessor : route.argAccessors)
                    {
                        if (!argPathValid(text, accessor))
                        {
                            if (unsatisfied == null)
                            {
                                unsatisfied = new ArrayList<>();
                            }
                            unsatisfied.add("args." + accessor);
                        }
                    }
                }
            }

            if (!route.paramAccessors.isEmpty())
            {
                final List<String> captures = resourceCaptures.getOrDefault(route.resource, List.of());
                for (String accessor : route.paramAccessors)
                {
                    if (!captures.contains(accessor))
                    {
                        if (unsatisfied == null)
                        {
                            unsatisfied = new ArrayList<>();
                        }
                        unsatisfied.add("params." + accessor);
                    }
                }
            }

            verdict = unsatisfied != null ? unsatisfied : List.of();
            if (!deferred)
            {
                unsatisfiedAccessors.put(route, verdict);
            }
        }
        return verdict;
    }

    static boolean argPathValid(
        String schemaText,
        String dottedPath)
    {
        boolean valid = true;
        if (schemaText != null && !schemaText.isBlank())
        {
            try (JsonReader reader = Json.createReader(new StringReader(schemaText)))
            {
                final JsonStructure structure = reader.read();
                if (structure.getValueType() == JsonValue.ValueType.OBJECT)
                {
                    valid = pathExists(structure.asJsonObject(), dottedPath.split("\\."), 0);
                }
            }
            catch (Exception ex)
            {
                valid = true;
            }
        }
        return valid;
    }

    private static boolean pathExists(
        JsonObject node,
        String[] segments,
        int index)
    {
        boolean exists;
        final JsonValue additional = node.get("additionalProperties");
        final boolean openAdditional = additional != null &&
            (additional.getValueType() == JsonValue.ValueType.TRUE ||
             additional.getValueType() == JsonValue.ValueType.OBJECT);
        if (index >= segments.length)
        {
            exists = true;
        }
        else if (node.containsKey("$ref") || node.containsKey("allOf") || node.containsKey("anyOf") ||
            node.containsKey("oneOf") || node.containsKey("patternProperties") || node.containsKey("items") ||
            openAdditional)
        {
            exists = true;
        }
        else if (node.containsKey("properties") &&
            node.get("properties").getValueType() == JsonValue.ValueType.OBJECT)
        {
            final JsonObject properties = node.getJsonObject("properties");
            final String segment = segments[index];
            if (properties.containsKey(segment) &&
                properties.get(segment).getValueType() == JsonValue.ValueType.OBJECT)
            {
                exists = pathExists(properties.getJsonObject(segment), segments, index + 1);
            }
            else
            {
                exists = properties.containsKey(segment);
            }
        }
        else
        {
            exists = true;
        }
        return exists;
    }

    private static final class ResourceMatcher
    {
        private static final Pattern PARAM_PATTERN = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)\\}");

        private final McpHttpResourceConfig resource;
        private final Pattern pattern;
        private final List<String> paramNames;

        private ResourceMatcher(
            McpHttpResourceConfig resource)
        {
            this.resource = resource;
            this.paramNames = new ArrayList<>();
            this.pattern = compile(resource.uri, paramNames);
        }

        private boolean matches(
            String uri,
            Map<String, String> params)
        {
            boolean matched = false;
            if (uri != null)
            {
                final Matcher matcher = pattern.matcher(uri);
                if (matcher.matches())
                {
                    for (String name : paramNames)
                    {
                        params.put(name, matcher.group(name));
                    }
                    matched = true;
                }
            }
            return matched;
        }

        private static Pattern compile(
            String template,
            List<String> names)
        {
            final StringBuilder regex = new StringBuilder("^");
            final Matcher matcher = PARAM_PATTERN.matcher(template);
            int last = 0;
            while (matcher.find())
            {
                regex.append(Pattern.quote(template.substring(last, matcher.start())));
                final String name = matcher.group(1);
                names.add(name);
                regex.append("(?<").append(name).append(">[^/]+)");
                last = matcher.end();
            }
            regex.append(Pattern.quote(template.substring(last)));
            regex.append("$");
            return Pattern.compile(regex.toString());
        }
    }
}
