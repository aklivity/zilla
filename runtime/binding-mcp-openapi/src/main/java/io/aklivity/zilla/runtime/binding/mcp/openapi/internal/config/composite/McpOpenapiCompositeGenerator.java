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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.composite;

import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.config.binding.mcp.http.McpHttpAuthorizationConfig;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpBodyConfig;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpConditionConfig;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpOptionsConfig;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpResourceConfig;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpResourceConfigBuilder;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpToolConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiAuthorizationConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiCatalogConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiConditionConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiResourceConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiResourceConfigBuilder;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiSpecificationConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiToolConfig;
import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiToolConfigBuilder;
import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfig;
import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.BindingConfigBuilder;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.GuardedConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.NamespaceConfigBuilder;
import io.aklivity.zilla.config.engine.RouteConfigBuilder;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiRouteConfig;
import io.aklivity.zilla.runtime.common.json.JsonOverlay;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiParser;
import io.aklivity.zilla.runtime.common.openapi.security.GuardedRef;
import io.aklivity.zilla.runtime.common.openapi.security.GuardedResolution;
import io.aklivity.zilla.runtime.common.openapi.security.OpenapiGuardResolver;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiMediaTypeView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiParameterView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiResponseView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public final class McpOpenapiCompositeGenerator
{
    private static final String CATALOG_NAME = "catalog0";
    private static final String BINDING_NAME = "mcp_http0";
    private static final String CAPABILITY_TOOL = "tool";
    private static final String CAPABILITY_RESOURCE = "resource";
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern JSON_CONTENT_TYPE_PATTERN = Pattern.compile("^application/(?:.+\\+)?json$");

    private final String httpClientExit;
    private final List<String> denied;
    private final Matcher jsonContentType;

    public McpOpenapiCompositeGenerator(
        String httpClientExit)
    {
        this.httpClientExit = httpClientExit;
        this.denied = new ArrayList<>();
        this.jsonContentType = JSON_CONTENT_TYPE_PATTERN.matcher("");
    }

    public McpOpenapiCompositeConfig generate(
        McpOpenapiBindingConfig binding)
    {
        denied.clear();

        final OpenapiParser parser = new OpenapiParser();
        final Map<String, OpenapiView> specsByLabel = new LinkedHashMap<>();
        final Map<String, Map<String, String>> securityByLabel = new LinkedHashMap<>();
        final Map<String, String> serverByLabel = new LinkedHashMap<>();

        int tagIndex = 1;
        if (binding.options != null && binding.options.specs != null)
        {
            for (McpOpenapiSpecificationConfig specification : binding.options.specs)
            {
                final String label = specification.label;
                securityByLabel.put(label, specification.security);
                serverByLabel.put(label, specification.server);
                for (McpOpenapiCatalogConfig catalog : specification.catalogs)
                {
                    final long catalogId = binding.resolveId.applyAsLong(catalog.name);
                    final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
                    final int schemaId = handler.resolve(catalog.subject, catalog.version);
                    final String payload = handler.resolve(schemaId);
                    final String materialized = materialize(binding, specification, payload);
                    final OpenapiView openapi = OpenapiView.of(tagIndex++, label, parser.parse(materialized));
                    specsByLabel.put(label, openapi);
                }
            }
        }

        final Set<OpenapiOperationView> claimed = new HashSet<>();
        final Set<String> usedNames = new HashSet<>();
        final List<RoutedOperation> routed = new LinkedList<>();
        for (McpOpenapiRouteConfig route : binding.routes)
        {
            final McpOpenapiWithConfig with = route.with;
            if (with == null)
            {
                continue;
            }
            final OpenapiView openapi = specsByLabel.get(with.spec);
            if (openapi == null)
            {
                continue;
            }

            String tool = null;
            String resource = null;
            List<String> capability = null;
            for (McpOpenapiConditionConfig when : route.when)
            {
                if (when.tool != null)
                {
                    tool = when.tool;
                }
                if (when.resource != null)
                {
                    resource = when.resource;
                }
                if (when.capability != null)
                {
                    capability = when.capability;
                }
            }

            final boolean wantsResource = resource != null;
            if (capability != null && !capability.contains(wantsResource ? CAPABILITY_RESOURCE : CAPABILITY_TOOL))
            {
                continue;
            }

            if (tool != null)
            {
                usedNames.add(tool);
            }

            for (OpenapiOperationView operation : candidateOperations(openapi, route, claimed))
            {
                claimed.add(operation);

                final String routeTool = tool != null || resource != null
                    ? tool
                    : McpOpenapiToolNamer.defaultName(operation, usedNames);

                final GuardedResolution resolution = OpenapiGuardResolver.resolve(
                    operation.id, with.spec, operation.security, securityByLabel.get(with.spec),
                    binding.resolveId, binding.supplyQName);
                if (resolution.denied())
                {
                    denied.add(resolution.reason);
                    continue;
                }

                final List<GuardedRef> guarded = combineGuarded(binding, route, resolution.guarded);

                routed.add(new RoutedOperation(toolConfig(binding, routeTool), resourceConfig(binding, resource),
                    operation, guarded, serverByLabel.get(with.spec), with.params, with.body));
            }
        }

        final NamespaceConfig namespace = NamespaceConfig.builder()
            .name("%s/mcp_http".formatted(binding.qname))
            .inject(n -> injectCatalog(n, routed))
            .inject(n -> injectBinding(n, binding, routed))
            .build();

        final List<McpOpenapiCompositeRouteConfig> routes = new LinkedList<>();
        namespace.bindings.stream()
            .filter(b -> BINDING_NAME.equals(b.name))
            .forEach(b ->
            {
                final long routeId = binding.supplyBindingId.applyAsLong(namespace, b);
                routes.add(new McpOpenapiCompositeRouteConfig(routeId));
            });

        return new McpOpenapiCompositeConfig(List.of(namespace), routes);
    }

    public List<String> deniedOperations()
    {
        return denied;
    }

    private String materialize(
        McpOpenapiBindingConfig binding,
        McpOpenapiSpecificationConfig specification,
        String payload)
    {
        String materialized = payload;
        if (specification.overlay != null)
        {
            final long catalogId = binding.resolveId.applyAsLong(specification.overlay.name);
            final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
            final int schemaId = handler.resolve(specification.overlay.subject, specification.overlay.version);
            final String overlayPayload = handler.resolve(schemaId);

            final JsonObject document = YamlJson.createReader(new StringReader(payload)).readObject();
            final JsonObject overlayDocument = YamlJson.createReader(new StringReader(overlayPayload)).readObject();
            materialized = JsonOverlay.of(overlayDocument).apply(document).toString();
        }

        return materialized;
    }

    private McpOpenapiToolConfig toolConfig(
        McpOpenapiBindingConfig binding,
        String tool)
    {
        return tool != null
            ? McpOpenapiToolConfig.builder()
                .name(tool)
                .inject(t -> injectToolOverrides(t, binding, tool))
                .build()
            : null;
    }

    private <C> McpOpenapiToolConfigBuilder<C> injectToolOverrides(
        McpOpenapiToolConfigBuilder<C> tool,
        McpOpenapiBindingConfig binding,
        String name)
    {
        if (binding.options != null && binding.options.tools != null)
        {
            binding.options.tools.stream()
                .filter(t -> name.equals(t.name))
                .findFirst()
                .ifPresent(override -> tool.description(override.description)
                    .summary(override.summary)
                    .input(override.input)
                    .output(override.output));
        }

        return tool;
    }

    private McpOpenapiResourceConfig resourceConfig(
        McpOpenapiBindingConfig binding,
        String resource)
    {
        return resource != null
            ? McpOpenapiResourceConfig.builder()
                .uri(resource)
                .inject(r -> injectResourceOverrides(r, binding, resource))
                .build()
            : null;
    }

    private <C> McpOpenapiResourceConfigBuilder<C> injectResourceOverrides(
        McpOpenapiResourceConfigBuilder<C> resource,
        McpOpenapiBindingConfig binding,
        String uri)
    {
        if (binding.options != null && binding.options.resources != null)
        {
            binding.options.resources.stream()
                .filter(r -> uri.equals(r.uri))
                .findFirst()
                .ifPresent(override -> resource.description(override.description)
                    .mimeType(override.mimeType)
                    .output(override.output));
        }

        return resource;
    }

    private <C> NamespaceConfigBuilder<C> injectCatalog(
        NamespaceConfigBuilder<C> namespace,
        List<RoutedOperation> routed)
    {
        namespace
            .catalog()
                .name(CATALOG_NAME)
                .type("inline")
                .options(InlineOptionsConfig::builder)
                    .inject(o -> injectSubjects(o, routed))
                    .build()
                .build();

        return namespace;
    }

    private <C> InlineOptionsConfigBuilder<C> injectSubjects(
        InlineOptionsConfigBuilder<C> options,
        List<RoutedOperation> routed)
    {
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            for (RoutedOperation entry : routed)
            {
                final String name = entry.subjectName();
                final OpenapiOperationView operation = entry.operation;

                // derived unconditionally, even when schemas.input/output is authored -- the derived
                // subject then simply goes unreferenced, exactly like the pre-existing output behavior
                options.schema()
                    .subject("%s-input".formatted(name))
                    .version("latest")
                    .schema(inputSchema(operation))
                    .build();

                if (operation.hasRequestBody())
                {
                    options.schema()
                        .subject("%s-body".formatted(name))
                        .version("latest")
                        .schema(bodySchema(jsonb, operation))
                        .build();
                }

                final OpenapiResponseView success = successResponse(operation);
                if (success != null && success.content != null)
                {
                    for (OpenapiMediaTypeView typed : success.content.values())
                    {
                        if (typed.schema != null)
                        {
                            final String schemaJson = toSchemaJson(jsonb, typed.schema.model);
                            // only a tool's outputSchema is advertised as MCP structured-output JSON Schema
                            // (and must therefore be object-typed); a resource's output schema is purely an
                            // internal projector for its read body and carries no such constraint, so only
                            // tool responses get wrapped when the OpenAPI response itself is non-object
                            final boolean wrap = entry.tool != null && !isObjectSchema(typed.schema.model);
                            options.schema()
                                .subject("%s-output".formatted(name))
                                .version("latest")
                                .schema(wrap ? wrapAsObjectSchema(schemaJson) : schemaJson)
                                .build();
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }

        return options;
    }

    private <C> NamespaceConfigBuilder<C> injectBinding(
        NamespaceConfigBuilder<C> namespace,
        McpOpenapiBindingConfig binding,
        List<RoutedOperation> routed)
    {
        return namespace
            .binding()
                .name(BINDING_NAME)
                .type("mcp_http")
                .kind(PROXY)
                .options(mcpHttpOptions(binding, routed))
                .inject(b -> injectRoutes(b, routed))
                .build();
    }

    private McpHttpOptionsConfig mcpHttpOptions(
        McpOpenapiBindingConfig binding,
        List<RoutedOperation> routed)
    {
        final List<McpHttpToolConfig> tools = new ArrayList<>();
        final List<McpHttpResourceConfig> resources = new ArrayList<>();

        for (RoutedOperation entry : routed)
        {
            final String name = entry.subjectName();
            final ModelConfig input = entry.tool != null && entry.tool.input != null
                ? qualifyModel(binding, entry.tool.input)
                : jsonModel("%s-input".formatted(name));

            if (entry.tool != null)
            {
                final boolean outputMaybeWrapped = entry.tool.output == null && !hasObjectOutputSchema(entry.operation);
                final ModelConfig output = entry.tool.output != null
                    ? qualifyModel(binding, entry.tool.output)
                    : jsonModel("%s-output".formatted(name));
                final String description = entry.tool.description != null
                    ? entry.tool.description
                    : entry.operation.description != null
                        ? entry.operation.description
                        : entry.operation.id;
                // mcp_http requires a non-null tool summary; prefer an authored override, then OpenAPI's
                // own optional summary field, then a plain literal string naming the operation (not a
                // ${...} template -- mcp_http only understands ${result.*} references, not operationId)
                final String summary = entry.tool.summary != null
                    ? entry.tool.summary
                    : entry.operation.summary != null
                        ? entry.operation.summary
                        : "Call %s".formatted(entry.operation.id);
                tools.add(new McpHttpToolConfig(entry.tool.name, summary, description, input, output, outputMaybeWrapped));
            }
            else
            {
                final ModelConfig output = entry.resource.output != null
                    ? qualifyModel(binding, entry.resource.output)
                    : jsonModel("%s-output".formatted(name));
                final String uri = resourceUri(entry.operation);
                final boolean template = uri.indexOf('{') >= 0;
                resources.add(McpHttpResourceConfig.builder()
                    .name(entry.resource.uri)
                    .uri(uri)
                    .template(template)
                    .description(entry.resource.description)
                    .output(output)
                    .inject(r -> injectMimeType(r, entry.resource, entry.operation))
                    .build());
            }
        }

        return new McpHttpOptionsConfig(mcpHttpAuthorization(binding),
            tools.isEmpty() ? null : tools,
            resources.isEmpty() ? null : resources);
    }

    private McpHttpAuthorizationConfig mcpHttpAuthorization(
        McpOpenapiBindingConfig binding)
    {
        final McpOpenapiAuthorizationConfig authorization =
            binding.options != null ? binding.options.authorization : null;

        return authorization != null
            ? new McpHttpAuthorizationConfig(authorization.qname, authorization.headers)
            : null;
    }

    private <C> BindingConfigBuilder<C> injectRoutes(
        BindingConfigBuilder<C> binding,
        List<RoutedOperation> routed)
    {
        for (RoutedOperation entry : routed)
        {
            final McpHttpConditionConfig when = new McpHttpConditionConfig(
                entry.tool != null ? entry.tool.name : null,
                entry.resource != null ? entry.resource.uri : null);
            final McpHttpWithConfig with = withConfig(entry);

            binding.route()
                .when(when)
                .with(with)
                .exit(httpClientExit)
                .inject(r -> injectGuarded(r, entry))
                .build();
        }

        return binding;
    }

    private <C> RouteConfigBuilder<C> injectGuarded(
        RouteConfigBuilder<C> route,
        RoutedOperation entry)
    {
        for (GuardedRef ref : entry.guarded)
        {
            route.guarded()
                .name(ref.qname)
                .inject(g -> injectRoles(g, ref.roles))
                .build();
        }

        return route;
    }

    private <C> McpHttpResourceConfigBuilder<C> injectMimeType(
        McpHttpResourceConfigBuilder<C> resource,
        McpOpenapiResourceConfig configured,
        OpenapiOperationView operation)
    {
        if (configured.mimeType != null)
        {
            resource.mimeType(configured.mimeType);
        }
        else
        {
            final OpenapiResponseView success = successResponse(operation);
            if (success != null && success.content != null && !success.content.isEmpty())
            {
                final String mimeType = success.content.values().stream()
                    .filter(typed -> jsonContentType.reset(typed.name).matches())
                    .findFirst()
                    .map(typed -> typed.name)
                    .orElseGet(() -> success.content.values().iterator().next().name);
                resource.mimeType(mimeType);
            }
        }

        return resource;
    }

    private <C> GuardedConfigBuilder<C> injectRoles(
        GuardedConfigBuilder<C> guarded,
        List<String> roles)
    {
        roles.forEach(guarded::role);
        return guarded;
    }

    private static List<OpenapiOperationView> candidateOperations(
        OpenapiView openapi,
        McpOpenapiRouteConfig route,
        Set<OpenapiOperationView> claimed)
    {
        final McpOpenapiWithConfig with = route.with;
        final List<OpenapiOperationView> candidates = route.isBulk()
            ? bulkCandidates(openapi, with)
            : explicitCandidate(openapi, with);

        return candidates.stream()
            .filter(operation -> !claimed.contains(operation))
            .toList();
    }

    private static List<OpenapiOperationView> explicitCandidate(
        OpenapiView openapi,
        McpOpenapiWithConfig with)
    {
        final OpenapiOperationView operation = openapi.operations != null
            ? openapi.operations.get(with.operation)
            : null;

        return operation != null ? List.of(operation) : List.of();
    }

    private static List<OpenapiOperationView> bulkCandidates(
        OpenapiView openapi,
        McpOpenapiWithConfig with)
    {
        final Predicate<OpenapiOperationView> matches;
        if (with.tag != null)
        {
            matches = operation -> operation.tags != null && operation.tags.contains(with.tag);
        }
        else if (with.operation != null)
        {
            final Pattern pattern = compileGlob(with.operation);
            matches = operation -> operation.id != null && pattern.matcher(operation.id).matches();
        }
        else
        {
            matches = operation -> true;
        }

        return allOperations(openapi).stream()
            .filter(matches)
            .sorted(Comparator.comparing((OpenapiOperationView operation) -> operation.path)
                .thenComparing(operation -> operation.method))
            .toList();
    }

    private static List<OpenapiOperationView> allOperations(
        OpenapiView openapi)
    {
        return openapi.paths == null
            ? List.of()
            : openapi.paths.values().stream()
                .flatMap(path -> path.methods.values().stream())
                .toList();
    }

    private static Pattern compileGlob(
        String glob)
    {
        final StringBuilder regex = new StringBuilder();
        final String[] literals = glob.split("\\*", -1);
        for (int index = 0; index < literals.length; index++)
        {
            if (index > 0)
            {
                regex.append(".*");
            }
            if (!literals[index].isEmpty())
            {
                regex.append(Pattern.quote(literals[index]));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static List<GuardedRef> combineGuarded(
        McpOpenapiBindingConfig binding,
        McpOpenapiRouteConfig route,
        List<GuardedRef> derived)
    {
        List<GuardedRef> combined = derived;

        if (!route.guarded.isEmpty())
        {
            final Map<String, List<String>> rolesByQName = new LinkedHashMap<>();
            for (GuardedRef ref : derived)
            {
                rolesByQName.computeIfAbsent(ref.qname, q -> new ArrayList<>()).addAll(ref.roles);
            }
            for (GuardedConfig guarded : route.guarded)
            {
                final String qname = binding.supplyQName.apply(binding.resolveId.applyAsLong(guarded.name));
                rolesByQName.computeIfAbsent(qname, q -> new ArrayList<>()).addAll(guarded.roles);
            }

            combined = rolesByQName.entrySet().stream()
                .map(e -> new GuardedRef(e.getKey(), e.getValue().stream().distinct().toList()))
                .toList();
        }

        return combined;
    }

    private McpHttpWithConfig withConfig(
        RoutedOperation entry)
    {
        final OpenapiOperationView operation = entry.operation;
        final ResolvedServer resolved = entry.server != null
            ? resolveServerOverride(entry.server)
            : resolveServerFromSpec(operation);

        final String accessor = entry.tool != null ? "args" : "params";
        final StringBuilder path = new StringBuilder(resolved.base);
        path.append(lowerPathParams(operation.path, accessor, entry.params));

        final String query = queryString(operation, accessor, entry.params);
        if (!query.isEmpty())
        {
            path.append('?').append(query);
        }

        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":method", operation.method);
        if (resolved.scheme != null)
        {
            headers.put(":scheme", resolved.scheme);
        }
        if (resolved.authority != null)
        {
            headers.put(":authority", resolved.authority);
        }
        headers.put(":path", path.toString());
        injectHeaderParams(headers, operation, accessor, entry.params);

        final Map<String, String> cookies = new LinkedHashMap<>();
        injectCookieParams(cookies, operation, accessor, entry.params);

        final boolean bodyTemplated = !entry.body.isEmpty();
        final ModelConfig bodyModel = !bodyTemplated && operation.hasRequestBody()
            ? jsonModel("%s-body".formatted(entry.subjectName()))
            : null;

        final McpHttpBodyConfig body = bodyTemplated
            ? McpHttpBodyConfig.builder().template(entry.body).build()
            : bodyModel != null ? McpHttpBodyConfig.builder().model(bodyModel).build() : null;

        return McpHttpWithConfig.builder()
            .headers(headers)
            .cookies(cookies.isEmpty() ? null : cookies)
            .body(body)
            .build();
    }

    private static ResolvedServer resolveServerFromSpec(
        OpenapiOperationView operation)
    {
        final OpenapiServerView server = operation.servers != null && !operation.servers.isEmpty()
            ? operation.servers.get(0)
            : null;

        final String authority = server != null && server.url != null
            ? server.url.getPort() != -1
                ? "%s:%d".formatted(server.url.getHost(), server.url.getPort())
                : server.url.getHost()
            : null;
        final String scheme = server != null && server.url != null ? server.url.getScheme() : "https";
        final String base = server != null && server.url != null && server.url.getPath() != null
            ? server.url.getPath()
            : "";

        return new ResolvedServer(scheme, authority, base);
    }

    private static ResolvedServer resolveServerOverride(
        String server)
    {
        final URI uri = URI.create(server);
        final int port = uri.getPort() != -1 ? uri.getPort() : defaultPort(uri.getScheme());
        final String authority = port != -1
            ? "%s:%d".formatted(uri.getHost(), port)
            : uri.getHost();
        final String base = uri.getPath() != null ? uri.getPath() : "";

        return new ResolvedServer(uri.getScheme(), authority, base);
    }

    private static int defaultPort(
        String scheme)
    {
        return switch (scheme)
        {
        case "http" -> 80;
        case "https" -> 443;
        default -> -1;
        };
    }

    private static String lowerPathParams(
        String path,
        String accessor,
        Map<String, String> params)
    {
        String result = path;
        if (path != null)
        {
            final Matcher matcher = PATH_PARAM_PATTERN.matcher(path);
            final StringBuilder builder = new StringBuilder();
            int last = 0;
            while (matcher.find())
            {
                builder.append(path, last, matcher.start());
                builder.append("${").append(paramExpression(accessor, matcher.group(1), params)).append('}');
                last = matcher.end();
            }
            builder.append(path, last, path.length());
            result = builder.toString();
        }
        return result;
    }

    private static String paramExpression(
        String accessor,
        String name,
        Map<String, String> params)
    {
        final String override = params.get(name);
        return override != null ? innerExpression(override) : "%s.%s".formatted(accessor, name);
    }

    private static String innerExpression(
        String expression)
    {
        String inner = expression.trim();
        if (inner.startsWith("${") && inner.endsWith("}"))
        {
            inner = inner.substring(2, inner.length() - 1);
        }
        return inner;
    }

    private static String resourceUri(
        OpenapiOperationView operation)
    {
        final StringBuilder uri = new StringBuilder(operation.path);
        if (operation.parameters != null)
        {
            final List<String> names = new ArrayList<>();
            for (OpenapiParameterView parameter : operation.parameters)
            {
                if ("query".equals(parameter.in))
                {
                    names.add(parameter.name);
                }
            }
            if (!names.isEmpty())
            {
                uri.append("{?").append(String.join(",", names)).append('}');
            }
        }
        return uri.toString();
    }

    private static String queryString(
        OpenapiOperationView operation,
        String accessor,
        Map<String, String> params)
    {
        final StringBuilder query = new StringBuilder();
        if (operation.parameters != null)
        {
            for (OpenapiParameterView parameter : operation.parameters)
            {
                if ("query".equals(parameter.in))
                {
                    if (query.length() > 0)
                    {
                        query.append('&');
                    }
                    final String expression = paramExpression(accessor, parameter.name, params);
                    if (parameter.required)
                    {
                        query.append(parameter.name)
                            .append("=${")
                            .append(expression)
                            .append('}');
                    }
                    else
                    {
                        query.append("${?")
                            .append(expression)
                            .append('=')
                            .append(parameter.name)
                            .append('}');
                    }
                }
            }
        }
        return query.toString();
    }

    // Header params, unlike query, get one uniform treatment regardless of OpenAPI's required flag:
    // the header is included when its accessor resolves, omitted entirely otherwise (McpHttpRouteConfig.
    // resolveHeaders enforces this at request time) -- so no required-vs-optional branching is needed here.
    private static void injectHeaderParams(
        Map<String, String> headers,
        OpenapiOperationView operation,
        String accessor,
        Map<String, String> params)
    {
        if (operation.parameters != null)
        {
            for (OpenapiParameterView parameter : operation.parameters)
            {
                if ("header".equals(parameter.in))
                {
                    final String expression = paramExpression(accessor, parameter.name, params);
                    headers.put(parameter.name.toLowerCase(), "${%s}".formatted(expression));
                }
            }
        }
    }

    // Cookie names, unlike header names, are case-sensitive (RFC 6265) -- keep the OpenAPI-declared
    // casing verbatim rather than lowercasing as injectHeaderParams does for headers.
    private static void injectCookieParams(
        Map<String, String> cookies,
        OpenapiOperationView operation,
        String accessor,
        Map<String, String> params)
    {
        if (operation.parameters != null)
        {
            for (OpenapiParameterView parameter : operation.parameters)
            {
                if ("cookie".equals(parameter.in))
                {
                    final String expression = paramExpression(accessor, parameter.name, params);
                    cookies.put(parameter.name, "${%s}".formatted(expression));
                }
            }
        }
    }

    private static ModelConfig jsonModel(
        String subject)
    {
        return JsonModelConfig.builder()
            .catalog()
                .name(CATALOG_NAME)
                .schema()
                    .subject(subject)
                    .version("latest")
                    .build()
                .build()
            .build();
    }

    private static ModelConfig qualifyModel(
        McpOpenapiBindingConfig binding,
        ModelConfig model)
    {
        // an authored schemas.input/output's catalog reference names a catalog in the caller's own
        // namespace (e.g. "catalog0"), but this ModelConfig is forwarded as-is into the generated
        // mcp_http binding, which lives in its own freshly-generated namespace that has its own,
        // unrelated, same-named "catalog0" -- rewrite the reference to be namespace-qualified
        // (qname) so it resolves absolutely, against the caller's namespace, from anywhere
        ModelConfig qualified = model;
        if (model.cataloged != null && !model.cataloged.isEmpty())
        {
            final ModelConfigAdapter adapter = new ModelConfigAdapter();
            adapter.adaptType(model.model);
            final JsonValue value = adapter.adaptToJson(model);
            if (value instanceof JsonObject && ((JsonObject) value).containsKey("catalog"))
            {
                final JsonObject object = (JsonObject) value;
                final JsonObject catalog = object.getJsonObject("catalog");
                final JsonObjectBuilder qualifiedCatalog = Json.createObjectBuilder();
                for (Map.Entry<String, JsonValue> entry : catalog.entrySet())
                {
                    final long catalogId = binding.resolveId.applyAsLong(entry.getKey());
                    final String qname = binding.supplyQName.apply(catalogId);
                    qualifiedCatalog.add(qname, entry.getValue());
                }
                final JsonObject rewritten = Json.createObjectBuilder(object)
                    .remove("catalog")
                    .add("catalog", qualifiedCatalog)
                    .build();
                qualified = adapter.adaptFromJson(rewritten);
            }
        }
        return qualified;
    }

    private static OpenapiResponseView successResponse(
        OpenapiOperationView operation)
    {
        OpenapiResponseView result = null;
        if (operation.hasResponses())
        {
            result = operation.responses.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().startsWith("2"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        }
        return result;
    }

    // MCP's tool outputSchema (and the structuredContent it describes) must be a JSON object; an OpenAPI
    // response whose own schema is array- or primitive-typed is wrapped as {"result": <schema>} instead
    // of advertised as-is -- see wrapAsObjectSchema and McpHttpResultWrap, which wraps the real response
    // body the same way so structuredContent still matches the advertised schema. A resource's own output
    // schema has no such constraint (it is never advertised, only used to project the read body), so this
    // check and the wrapping it drives applies to tools only
    private static boolean hasObjectOutputSchema(
        OpenapiOperationView operation)
    {
        boolean result = false;
        final OpenapiResponseView success = successResponse(operation);
        if (success != null && success.content != null)
        {
            for (OpenapiMediaTypeView typed : success.content.values())
            {
                if (typed.schema != null && isObjectSchema(typed.schema.model))
                {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private static boolean isObjectSchema(
        OpenapiSchemaView.OpenapiJsonSchema schema)
    {
        return schema.type == null || "object".equals(schema.type);
    }

    private static String wrapAsObjectSchema(
        String schemaJson)
    {
        final JsonValue schema = Json.createReader(new StringReader(schemaJson)).readValue();
        return Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", Json.createObjectBuilder().add("result", schema))
            .add("required", Json.createArrayBuilder().add("result"))
            .build()
            .toString();
    }

    private static String inputSchema(
        OpenapiOperationView operation)
    {
        final JsonObjectBuilder properties = Json.createObjectBuilder();
        final List<String> required = new LinkedList<>();
        final List<String> propertyNames = new LinkedList<>();

        if (operation.parameters != null)
        {
            for (OpenapiParameterView parameter : operation.parameters)
            {
                final boolean supported = "path".equals(parameter.in) || "query".equals(parameter.in) ||
                    "header".equals(parameter.in) || "cookie".equals(parameter.in);
                if (supported && parameter.schema != null)
                {
                    properties.add(parameter.name, schemaObject(parameter.schema));
                    propertyNames.add(parameter.name);
                    if (parameter.required)
                    {
                        required.add(parameter.name);
                    }
                }
            }
        }

        if (operation.hasRequestBody())
        {
            for (OpenapiMediaTypeView typed : operation.requestBody.content.values())
            {
                final OpenapiSchemaView schema = typed.schema;
                if (schema != null && schema.properties != null)
                {
                    for (Map.Entry<String, OpenapiSchemaView> entry : new TreeMap<>(schema.properties).entrySet())
                    {
                        String key = entry.getKey();
                        if (propertyNames.contains(key))
                        {
                            key = "%s_body".formatted(key);
                        }
                        properties.add(key, schemaObject(entry.getValue()));
                    }
                    if (schema.required != null)
                    {
                        for (String req : schema.required)
                        {
                            String key = propertyNames.contains(req) ? "%s_body".formatted(req) : req;
                            required.add(key);
                        }
                    }
                }
                break;
            }
        }

        final JsonObjectBuilder object = Json.createObjectBuilder();
        object.add("type", "object");
        object.add("properties", properties);
        if (!required.isEmpty())
        {
            final JsonArrayBuilder requiredArray = Json.createArrayBuilder();
            required.forEach(requiredArray::add);
            object.add("required", requiredArray);
        }

        return object.build().toString();
    }

    private static String bodySchema(
        Jsonb jsonb,
        OpenapiOperationView operation)
    {
        String result = "{\"type\":\"object\"}";
        for (OpenapiMediaTypeView typed : operation.requestBody.content.values())
        {
            if (typed.schema != null)
            {
                result = toSchemaJson(jsonb, typed.schema.model);
            }
            break;
        }
        return result;
    }

    private static JsonObject schemaObject(
        OpenapiSchemaView schema)
    {
        final JsonObjectBuilder object = Json.createObjectBuilder();
        if (schema.type != null)
        {
            object.add("type", schema.type);
        }
        if (schema.format != null)
        {
            object.add("format", schema.format);
        }
        return object.build();
    }

    private static String toSchemaJson(
        Jsonb jsonb,
        OpenapiSchemaView.OpenapiJsonSchema schema)
    {
        String schemaJson = jsonb.toJson(schema);

        JsonReader reader = Json.createReader(new StringReader(schemaJson));
        JsonValue jsonValue = reader.readValue();

        if (jsonValue instanceof JsonObject)
        {
            JsonObject jsonObject = (JsonObject) jsonValue;
            if (jsonObject.containsKey("schema"))
            {
                JsonValue modifiedJsonValue = jsonObject.get("schema");
                StringWriter stringWriter = new StringWriter();
                JsonWriter jsonWriter = Json.createWriter(stringWriter);
                jsonWriter.write(modifiedJsonValue);
                jsonWriter.close();
                schemaJson = stringWriter.toString();
            }
        }

        return schemaJson;
    }

    private static final class RoutedOperation
    {
        private final McpOpenapiToolConfig tool;
        private final McpOpenapiResourceConfig resource;
        private final OpenapiOperationView operation;
        private final List<GuardedRef> guarded;
        private final String server;
        private final Map<String, String> params;
        private final Map<String, String> body;

        private RoutedOperation(
            McpOpenapiToolConfig tool,
            McpOpenapiResourceConfig resource,
            OpenapiOperationView operation,
            List<GuardedRef> guarded,
            String server,
            Map<String, String> params,
            Map<String, String> body)
        {
            this.tool = tool;
            this.resource = resource;
            this.operation = operation;
            this.guarded = guarded;
            this.server = server;
            this.params = params != null ? params : Map.of();
            this.body = body != null ? body : Map.of();
        }

        private String subjectName()
        {
            return tool != null ? tool.name : resource.uri;
        }
    }

    private static final class ResolvedServer
    {
        private final String scheme;
        private final String authority;
        private final String base;

        private ResolvedServer(
            String scheme,
            String authority,
            String base)
        {
            this.scheme = scheme;
            this.authority = authority;
            this.base = base;
        }
    }
}
