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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.config.McpElicitationConfig.DEFAULT_CALLBACK_PATH;
import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Object2IntHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;
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

    private static final String SCHEME_HTTPS = "https";
    private static final int PORT_HTTP = 80;
    private static final int PORT_HTTPS = 443;
    private static final String PATH_ROOT = "/";

    private static final String MODEL_NAME = "model";
    private static final String CATALOG_NAME = "catalog";
    private static final String SCHEMA_ID = "id";

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

    public final long id;
    public final McpOptionsConfig options;
    public final GuardHandler guard;
    public final String credentials;
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
    private final MutableDirectBuffer scratch;
    private final MutableDirectBuffer argsScratch;

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

        final Map<String, McpRouteConfig> routeByPrefix = new LinkedHashMap<>();
        if (routes.size() > 1)
        {
            final List<String> toolkits = routes.stream()
                .map(McpRouteConfig::toolkit)
                .collect(Collectors.toList());
            final Map<String, String> prefixesByToolkit = McpAggregateEventId.computePrefixes(toolkits);
            for (McpRouteConfig route : routes)
            {
                final String prefix = prefixesByToolkit.get(route.toolkit());
                routeByPrefix.put(prefix, route);
            }
        }
        this.routeByPrefix = routeByPrefix;

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

        this.credentials = Optional.ofNullable(options)
            .map(o -> o.authorization)
            .map(a -> a.credentials)
            .filter(c -> !c.isEmpty())
            .orElse(null);

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
        DirectBuffer data,
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
            try (JsonReader reader = Json.createReader(
                new ByteArrayInputStream(toolsListJson.getBytes(StandardCharsets.UTF_8))))
            {
                final JsonObject root = reader.readObject();
                if (root.containsKey("tools"))
                {
                    final JsonArray tools = root.getJsonArray("tools");
                    for (JsonValue item : tools)
                    {
                        final JsonObject tool = item.asJsonObject();
                        if (tool.containsKey("name") && tool.containsKey("inputSchema"))
                        {
                            schemasByName.put(tool.getString("name"), tool.getJsonObject("inputSchema").toString());
                        }
                    }
                }
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
        DirectBuffer params,
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
        DirectBuffer params,
        int index,
        int limit)
    {
        String arguments;
        final byte[] bytes = new byte[limit - index];
        params.getBytes(index, bytes);
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(bytes)))
        {
            final JsonObject root = reader.readObject();
            arguments = root.containsKey("arguments")
                ? root.get("arguments").toString()
                : "{}";
        }
        catch (Exception ex)
        {
            arguments = null;
        }
        return arguments;
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

    public List<McpRoutePrefix> resolveAll(
        int kind,
        long authorization)
    {
        final String capability = McpRouteConfig.capabilityOf(kind);
        final List<McpRoutePrefix> result = new ArrayList<>();

        if (capability != null)
        {
            for (McpRouteConfig route : routes)
            {
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

}
