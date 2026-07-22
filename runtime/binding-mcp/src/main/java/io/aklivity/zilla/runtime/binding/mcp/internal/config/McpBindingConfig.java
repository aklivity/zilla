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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.config.McpElicitationConfig.DEFAULT_CALLBACK_PATH;
import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Object2IntHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBearerError;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

public final class McpBindingConfig
{
    private static final String HTTP_HEADER_SCHEME = ":scheme";
    private static final String HTTP_HEADER_AUTHORITY = ":authority";
    private static final String HTTP_HEADER_PATH = ":path";
    private static final String HTTP_HEADER_AUTHORIZATION = "authorization";

    private static final String SCHEME_HTTPS = "https";
    private static final int PORT_HTTP = 80;
    private static final int PORT_HTTPS = 443;
    private static final String PATH_ROOT = "/";

    private static final String MODEL_NAME = "model";
    private static final String CATALOG_NAME = "catalog";
    private static final String SCHEMA_ID = "id";

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    private static final Map<String, List<String>> EMPTY_ROLES = Map.of();

    public static final String CREDENTIALS_PLACEHOLDER = "{credentials}";

    public final long id;
    public final McpOptionsConfig options;
    public final GuardHandler guard;
    public final GuardHandler filterGuard;
    public final String credentials;
    public final boolean needsCredentials;
    public final Pattern credentialsPattern;
    public final McpProxyCache cache;
    public final Map<String, McpProxySession> sessions;
    public final Map<String, McpRouteConfig> routeByPrefix;
    public final McpAggregateRoute[] aggregateRoutes;

    private final List<McpRouteConfig> routes;
    private final String serverScheme;
    private final String serverAuthority;
    private final String serverPath;
    private final CatalogHandler toolsCatalog;
    private final ModelConfig toolsModel;
    private final boolean validates;
    private final ModelConfigAdapter modelConfig;
    private final Function<ModelConfig, ModelHandler> supplyModel;
    private final Int2ObjectHashMap<ModelPipeline> decodersBySchemaId;
    private final Object2IntHashMap<String> toolSchemaIdsByName;
    private final MutableDirectBufferEx scratch;
    private final MutableDirectBufferEx argsScratch;

    public McpBindingConfig(
        BindingConfig binding,
        McpConfiguration config,
        EngineContext context)
    {
        this.id = binding.id;
        this.options = (McpOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(McpRouteConfig::new)
            .collect(Collectors.toList());

        this.routeByPrefix = computeRouteByPrefix(routes);

        this.aggregateRoutes = routeByPrefix.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new McpAggregateRoute(e.getKey(), e.getValue().id))
            .toArray(McpAggregateRoute[]::new);

        this.guard = Optional.ofNullable(options)
            .map(o -> o.authorization)
            .map(a -> a.name)
            .map(binding.resolveId::applyAsLong)
            .map(context::supplyGuard)
            .orElse(null);

        // per-tool scope filtering verifies against the binding authorization guard when configured,
        // otherwise against the guard referenced by a route's guarded condition (e.g. the proxy kind)
        this.filterGuard = guard != null ? guard : routes.stream()
            .flatMap(r -> r.guarded.stream())
            .map(g -> g.id)
            .map(context::supplyGuard)
            .filter(g -> g != null)
            .findFirst()
            .orElse(null);

        this.credentials = Optional.ofNullable(options)
            .map(o -> o.authorization)
            .map(a -> a.credentials)
            .filter(c -> !c.isEmpty())
            .orElse(null);

        this.needsCredentials = credentials == null || credentials.contains(CREDENTIALS_PLACEHOLDER);

        this.credentialsPattern = credentials != null
            ? Pattern.compile(credentials.replace(CREDENTIALS_PLACEHOLDER, "(?<credentials>[^\\s]+)"))
            : null;

        this.cache = Optional.ofNullable(options)
            .map(o -> o.cache)
            .map(cache -> new McpProxyCache(binding, config, context, cache))
            .orElse(null);
        this.sessions = new Object2ObjectHashMap<>();

        final URI server = Optional.ofNullable(options)
            .map(o -> o.server)
            .map(URI::create)
            .orElse(null);
        this.serverScheme = server != null ? server.getScheme() : null;
        this.serverAuthority = server != null ? authorityOf(server) : null;
        this.serverPath = server != null ? pathOf(server) : null;

        this.toolsModel = Optional.ofNullable(options)
            .map(o -> o.tools)
            .orElse(null);
        this.validates = toolsModel != null && !toolsModel.cataloged.isEmpty();
        this.toolsCatalog = validates
            ? context.supplyCatalog(toolsModel.cataloged.get(0).id)
            : CatalogHandler.NONE;
        this.modelConfig = new ModelConfigAdapter();
        this.supplyModel = context::supplyModel;
        this.decodersBySchemaId = new Int2ObjectHashMap<>();
        this.toolSchemaIdsByName = new Object2IntHashMap<>(NO_SCHEMA_ID);
        this.scratch = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.argsScratch = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
    }

    public boolean validatesTools()
    {
        return validates;
    }

    public int registerToolSchema(
        String subject,
        String schema)
    {
        return toolsCatalog.register(subject, schema);
    }

    public void unregisterToolSchema(
        String subject)
    {
        toolsCatalog.unregister(subject);
    }

    public boolean validatesTool(
        int schemaId)
    {
        return schemaId != NO_SCHEMA_ID;
    }

    public boolean validateToolArgs(
        int schemaId,
        long traceId,
        long bindingId,
        DirectBufferEx data,
        int index,
        int limit)
    {
        boolean valid = true;
        final ModelPipeline decoder = schemaId != NO_SCHEMA_ID
            ? decodersBySchemaId.computeIfAbsent(schemaId, this::newToolDecoder)
            : null;
        if (decoder != null)
        {
            int srcAt = index;
            int produced = 0;
            int flags = FLAGS_INIT | FLAGS_FIN;
            boolean done = false;
            while (!done)
            {
                final ModelPipelineResult result = decoder.transform(traceId, bindingId, flags,
                    data, srcAt, limit, scratch, produced, scratch.capacity());
                final ModelStatus status = result.status();
                if (status == ModelStatus.REJECTED)
                {
                    valid = false;
                    done = true;
                }
                else
                {
                    produced += result.produced();
                    if (status == ModelStatus.COMPLETE)
                    {
                        done = true;
                    }
                    else
                    {
                        srcAt += result.consumed();
                        flags = FLAGS_FIN;
                    }
                }
            }
            decoder.reset();
        }
        return valid;
    }

    public void rebuildToolSchemaIndex(
        String toolsListJson)
    {
        final Map<String, String> schemasByName = new LinkedHashMap<>();
        if (toolsListJson != null)
        {
            final byte[] bytes = toolsListJson.getBytes(StandardCharsets.UTF_8);
            final JsonParserEx parser = JsonEx.createParser();
            parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
            try
            {
                scanToolsList(parser, schemasByName);
            }
            catch (Exception ex)
            {
                schemasByName.clear();
            }
        }

        final List<String> stale = new ArrayList<>();
        for (String name : toolSchemaIdsByName.keySet())
        {
            if (!schemasByName.containsKey(name))
            {
                stale.add(name);
            }
        }
        for (String name : stale)
        {
            unregisterToolSchema(name);
            toolSchemaIdsByName.removeKey(name);
        }

        for (Map.Entry<String, String> entry : schemasByName.entrySet())
        {
            final int schemaId = registerToolSchema(entry.getKey(), entry.getValue());
            toolSchemaIdsByName.put(entry.getKey(), schemaId);
        }
    }

    public int toolSchemaId(
        String toolName)
    {
        return toolSchemaIdsByName.getValue(toolName);
    }

    public boolean validateToolCall(
        int schemaId,
        long traceId,
        long bindingId,
        DirectBufferEx params,
        int index,
        int limit)
    {
        boolean valid = false;
        final String arguments = extractArguments(params, index, limit);
        if (arguments != null)
        {
            final int length = argsScratch.putStringWithoutLengthUtf8(0, arguments);
            valid = validateToolArgs(schemaId, traceId, bindingId, argsScratch, 0, length);
        }
        return valid;
    }

    private String extractArguments(
        DirectBufferEx params,
        int index,
        int limit)
    {
        String arguments;
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(params, index, limit);
        try
        {
            arguments = scanArguments(parser);
        }
        catch (Exception ex)
        {
            arguments = null;
        }
        return arguments;
    }

    private String scanArguments(
        JsonParserEx parser)
    {
        String arguments = "{}";
        if (parser.hasNext() && parser.next() == JsonParser.Event.START_OBJECT)
        {
            int depth = 1;
            while (depth > 0 && parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    if (depth == 1 && "arguments".contentEquals(parser.getStringView()))
                    {
                        parser.next();
                        arguments = parser.getValue().toString();
                    }
                    break;
                default:
                    break;
                }
            }
        }
        return arguments;
    }

    private void scanToolsList(
        JsonParserEx parser,
        Map<String, String> schemasByName)
    {
        if (parser.hasNext() && parser.next() == JsonParser.Event.START_OBJECT)
        {
            int depth = 1;
            while (depth > 0 && parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    if (depth == 1 && "tools".contentEquals(parser.getStringView()))
                    {
                        scanTools(parser, schemasByName);
                    }
                    break;
                default:
                    break;
                }
            }
        }
    }

    private void scanTools(
        JsonParserEx parser,
        Map<String, String> schemasByName)
    {
        if (parser.hasNext() && parser.next() == JsonParser.Event.START_ARRAY)
        {
            boolean items = true;
            while (items && parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
                switch (event)
                {
                case START_OBJECT:
                    scanTool(parser, schemasByName);
                    break;
                case END_ARRAY:
                    items = false;
                    break;
                default:
                    break;
                }
            }
        }
    }

    private void scanTool(
        JsonParserEx parser,
        Map<String, String> schemasByName)
    {
        String name = null;
        String inputSchema = null;
        int depth = 1;
        while (depth > 0 && parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                break;
            case KEY_NAME:
                if (depth == 1)
                {
                    final CharSequence key = parser.getStringView();
                    if ("name".contentEquals(key))
                    {
                        parser.next();
                        name = parser.getString();
                    }
                    else if ("inputSchema".contentEquals(key) &&
                        parser.next() == JsonParser.Event.START_OBJECT)
                    {
                        inputSchema = parser.getObject().toString();
                    }
                }
                break;
            default:
                break;
            }
        }

        if (name != null && inputSchema != null)
        {
            schemasByName.put(name, inputSchema);
        }
    }

    private ModelPipeline newToolDecoder(
        int schemaId)
    {
        final CatalogedConfig template = toolsModel.cataloged.get(0);
        final JsonObject model = Json.createObjectBuilder()
            .add(MODEL_NAME, toolsModel.model)
            .add(CATALOG_NAME, Json.createObjectBuilder()
                .add(template.name, Json.createArrayBuilder()
                    .add(Json.createObjectBuilder().add(SCHEMA_ID, schemaId))))
            .build();
        final ModelConfig toolModel = modelConfig.adaptFromJson(model);
        toolModel.cataloged.get(0).id = template.id;
        final ModelHandler handler = supplyModel.apply(toolModel);
        return handler != null ? handler.supplyDecoder() : null;
    }

    public void injectHeaders(
        HttpBeginExFW.Builder builder)
    {
        builder.headersItem(h -> h.name(HTTP_HEADER_SCHEME).value(serverScheme))
            .headersItem(h -> h.name(HTTP_HEADER_AUTHORITY).value(serverAuthority))
            .headersItem(h -> h.name(HTTP_HEADER_PATH).value(serverPath));
    }

    static String authorityOf(
        URI server)
    {
        final int port = server.getPort() != -1 ? server.getPort() : defaultPort(server.getScheme());
        return server.getHost() + ":" + port;
    }

    static String pathOf(
        URI server)
    {
        final String path = server.getRawPath();
        return path == null || path.isEmpty() ? PATH_ROOT : path;
    }

    static int defaultPort(
        String scheme)
    {
        return SCHEME_HTTPS.equals(scheme) ? PORT_HTTPS : PORT_HTTP;
    }

    static String naturalAuthority(
        String authority,
        String scheme)
    {
        final String defaultSuffix = ":" + defaultPort(scheme);
        return authority.endsWith(defaultSuffix)
            ? authority.substring(0, authority.length() - defaultSuffix.length())
            : authority;
    }

    static Map<String, McpRouteConfig> computeRouteByPrefix(
        List<McpRouteConfig> routes)
    {
        final Map<String, McpRouteConfig> routeByPrefix = new LinkedHashMap<>();
        if (routes.size() > 1)
        {
            final List<String> toolkits = routes.stream()
                .map(McpRouteConfig::toolkit)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            final Map<String, String> prefixesByToolkit = McpAggregateEventId.computePrefixes(toolkits);
            for (McpRouteConfig route : routes)
            {
                final String toolkit = route.toolkit();
                if (toolkit != null)
                {
                    final String prefix = prefixesByToolkit.get(toolkit);
                    routeByPrefix.put(prefix, route);
                }
            }
        }
        return routeByPrefix;
    }

    public Map<String, List<String>> getRoles(
        long routeId)
    {
        Map<String, List<String>> result = EMPTY_ROLES;
        for (McpRouteConfig route : routes)
        {
            if (route.id == routeId)
            {
                result = route.roles;
                break;
            }
        }
        return result;
    }

    public Map<String, List<String>> getRoles(
        String toolName)
    {
        return rolesForTool(routes, toolName);
    }

    public boolean hasToolGuardedRoutes()
    {
        return routes.stream().anyMatch(r -> !r.roles.isEmpty());
    }

    static Map<String, List<String>> rolesForTool(
        List<McpRouteConfig> routes,
        String toolName)
    {
        Map<String, List<String>> result = EMPTY_ROLES;
        for (McpRouteConfig route : routes)
        {
            if (route.matchesTool(toolName))
            {
                result = route.roles;
                break;
            }
        }
        return result;
    }

    public McpRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public int serverCapabilities(
        long authorization)
    {
        int bits = 0;
        for (McpRouteConfig route : routes)
        {
            if (route.authorized(authorization))
            {
                bits |= route.serverCapabilities();
            }
        }
        return bits;
    }

    public McpRouteConfig resolve(
        McpBeginExFW beginEx,
        long authorization)
    {
        final String capability = McpRouteConfig.capabilityOf(beginEx);
        final String identifier = McpRouteConfig.identifierOf(beginEx);

        McpRouteConfig resolved = null;

        if (capability == null)
        {
            resolved = resolve(authorization);
        }
        else if (identifier != null)
        {
            for (McpRouteConfig route : routes)
            {
                if (route.authorized(authorization) && route.matches(capability, identifier))
                {
                    resolved = route;
                    break;
                }
            }
        }
        else
        {
            for (McpRouteConfig route : routes)
            {
                if (route.authorized(authorization) && route.serves(capability))
                {
                    resolved = route;
                    break;
                }
            }
        }

        return resolved;
    }

    public List<Long> resolveAll(
        long authorization)
    {
        final List<Long> result = new ArrayList<>();
        for (McpRouteConfig route : routes)
        {
            if (route.authorized(authorization))
            {
                result.add(route.id);
            }
        }
        return result;
    }

    public String routeCacheCredentials(
        long routedId)
    {
        String credentials = null;
        for (McpRouteConfig route : routes)
        {
            if (route.id == routedId && route.with != null && route.with.cache != null)
            {
                credentials = route.with.cache.credentials;
                break;
            }
        }
        return credentials;
    }

    // resolves the effective session for a route's south connections: its own with.cache.credentials
    // override when configured, otherwise the shared session already established via
    // options.cache.authorization. Callers must first confirm cache != null.
    public long routeCacheAuthorization(
        long traceId,
        long routedId)
    {
        long authorization = cache.authorization;
        if (cache.guard != null)
        {
            final String credentials = routeCacheCredentials(routedId);
            if (credentials != null)
            {
                authorization = cache.routeAuthorization(traceId, routedId, credentials);
            }
        }
        return authorization;
    }

    public List<McpRoutePrefix> resolveAll(
        long traceId,
        int kind)
    {
        final String capability = McpRouteConfig.capabilityOf(kind);
        final List<McpRoutePrefix> result = new ArrayList<>();

        if (capability != null)
        {
            for (McpRouteConfig route : routes)
            {
                final long authorization = routeCacheAuthorization(traceId, route.id);
                if (route.authorized(authorization) && route.serves(capability))
                {
                    result.add(new McpRoutePrefix(route.id, new String8FW(route.prefix(kind)), route));
                }
            }
            result.sort(Comparator.comparing(p -> p.prefix().asString()));
        }

        return result;
    }

    public List<McpRouteConfig> resolveAll(
        McpBeginExFW beginEx,
        long authorization)
    {
        final String capability = McpRouteConfig.capabilityOf(beginEx);
        final String identifier = McpRouteConfig.identifierOf(beginEx);
        final List<McpRouteConfig> result = new ArrayList<>();

        if (capability != null && identifier == null)
        {
            for (McpRouteConfig route : routes)
            {
                if (route.authorized(authorization) && route.serves(capability))
                {
                    result.add(route);
                }
            }
        }

        return result;
    }

    public String resolveRedirectURI(
        HttpBeginExFW httpBeginEx)
    {
        String redirectURI = null;
        if (httpBeginEx != null)
        {
            final String authority = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_AUTHORITY.equals(h.name().asString())))
                .map(h -> h.value().asString())
                .orElse(null);
            final String path = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_PATH.equals(h.name().asString())))
                .map(h -> h.value().asString())
                .orElse(null);
            if (authority != null && path != null)
            {
                final int queryAt = path.indexOf('?');
                final String pathOnly = queryAt >= 0 ? path.substring(0, queryAt) : path;
                final String callback = Optional.ofNullable(options)
                    .map(o -> o.elicitation)
                    .map(e -> e.callback)
                    .orElse(DEFAULT_CALLBACK_PATH);
                redirectURI = "https://" + naturalAuthority(authority, SCHEME_HTTPS) + pathOnly + "/" + callback;
            }
        }
        return redirectURI;
    }

    public boolean isAuthCallbackPath(
        String path)
    {
        boolean match = false;
        if (path != null)
        {
            final String callback = Optional.ofNullable(options)
                .map(o -> o.elicitation)
                .map(e -> e.callback)
                .orElse(DEFAULT_CALLBACK_PATH);
            final int queryAt = path.indexOf('?');
            final String pathOnly = queryAt >= 0 ? path.substring(0, queryAt) : path;
            final String suffix = "/" + callback;
            match = pathOnly.endsWith(suffix);
        }
        return match;
    }

    public McpAuthorizationResult authorize(
        long traceId,
        long routedId,
        long initialId,
        long authorization,
        HttpBeginExFW httpBeginEx,
        String path)
    {
        McpAuthorizationResult result = new McpAuthorizationResult(authorization, null);
        if (guard != null && !isAuthCallbackPath(path))
        {
            final String authorizationHeader = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_AUTHORIZATION.equals(h.name().asString())))
                .map(h -> h.value().asString())
                .orElse(null);

            // No Authorization header at all is not itself a rejection: leave authorization
            // unresolved (anonymous) and let a downstream guarded route decide, exactly as
            // an unauthenticated http request reaches an unguarded route. A header that was
            // sent but does not match the configured credentials pattern, or a credential
            // that fails reauthorization, are active (if unsuccessful) attempts to authenticate
            // and are rejected here.
            if (authorizationHeader != null)
            {
                final Matcher credentialsMatcher = credentialsPattern.matcher(authorizationHeader);
                final String credentials = credentialsMatcher.matches()
                    ? credentialsMatcher.group("credentials")
                    : null;

                final long sessionAuth = credentials != null
                    ? guard.reauthorize(traceId, routedId, initialId, credentials)
                    : GuardHandler.NOT_AUTHORIZED;

                result = (sessionAuth & GuardHandler.MASK_AUTHORIZED) != 0L
                    ? new McpAuthorizationResult(sessionAuth, null)
                    : new McpAuthorizationResult(authorization, credentials == null
                        ? McpBearerError.INVALID_REQUEST
                        : McpBearerError.INVALID_TOKEN);
            }
        }
        return result;
    }

}
