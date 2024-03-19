/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiItem;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiParameter;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiServer;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig.Method;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class AsyncapiHttpProtocol extends AsyncapiProtocol
{
    private static final Map<String, ModelConfig> MODELS = Map.of(
        "string", StringModelConfig.builder().build(),
        "integer", Int32ModelConfig.builder().build()
    );
    private static final String SCHEME = "http";
    private static final String SECURE_SCHEME = "https";
    private final Map<String, String> securitySchemes;
    private final String authorizationHeader;
    private final boolean isJwtEnabled;
    private final String guardName;
    private final HttpAuthorizationConfig authorization;

    protected AsyncapiHttpProtocol(
        String qname,
        Asyncapi asyncApi,
        AsyncapiOptionsConfig options)
    {
        super(qname, asyncApi, SCHEME, SECURE_SCHEME);
        this.securitySchemes = resolveSecuritySchemes();
        this.authorizationHeader = resolveAuthorizationHeader();
        this.isJwtEnabled = !securitySchemes.isEmpty();
        final HttpOptionsConfig httpOptions = options.http;
        this.guardName = httpOptions != null ? String.format("%s:%s", qname, httpOptions.authorization.name) : null;
        this.authorization = httpOptions != null ?  httpOptions.authorization : null;
    }


    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerOptions(
        BindingConfigBuilder<C> binding)
    {
        return binding
                    .options(HttpOptionsConfig::builder)
                        .access()
                            .policy(CROSS_ORIGIN)
                            .build()
                    .inject(this::injectHttpServerOptions)
                    .inject(this::injectHttpServerRequests)
                    .build();
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerRoutes(
        BindingConfigBuilder<C> binding)
    {
        for (Map.Entry<String, AsyncapiServer> entry : asyncApi.servers.entrySet())
        {
            AsyncapiServerView server = AsyncapiServerView.of(entry.getValue());
            for (String name : asyncApi.operations.keySet())
            {
                AsyncapiOperation operation = asyncApi.operations.get(name);
                AsyncapiChannelView channel = AsyncapiChannelView.of(asyncApi.channels, operation.channel);
                String path = channel.address().replaceAll("\\{[^}]+\\}", "*");
                String method = operation.bindings.get("http").method;
                binding
                    .route()
                        .exit(qname)
                        .when(HttpConditionConfig::builder)
                            .header(":scheme", server.scheme())
                            .header(":authority", server.authority())
                            .header(":path", path)
                            .header(":method", method)
                            .build()
                        .inject(route -> injectHttpServerRouteGuarded(route, server))
                    .build();
            }
        }
        return binding;
    }

    @Override
    protected boolean isSecure()
    {
        return findFirstServerUrlWithScheme(SECURE_SCHEME) != null;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpServerOptions(
        HttpOptionsConfigBuilder<C> options)
    {
        if (isJwtEnabled)
        {
            options.authorization(authorization).build();
        }
        return options;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpServerRequests(
        HttpOptionsConfigBuilder<C> options)
    {
        for (String name : asyncApi.operations.keySet())
        {
            AsyncapiOperation operation = asyncApi.operations.get(name);
            AsyncapiChannelView channel = AsyncapiChannelView.of(asyncApi.channels, operation.channel);
            String path = channel.address();
            Method method = Method.valueOf(operation.bindings.get("http").method);
            if (channel.messages() != null && !channel.messages().isEmpty() ||
                channel.parameters() != null && !channel.parameters().isEmpty())
            {
                options
                    .request()
                        .path(path)
                        .method(method)
                        .inject(request -> injectContent(request, channel.messages()))
                        .inject(request -> injectPathParams(request, channel.parameters()))
                        .build();
            }
        }
        return options;
    }

    private <C> HttpRequestConfigBuilder<C> injectContent(
        HttpRequestConfigBuilder<C> request,
        Map<String, AsyncapiMessage> messages)
    {
        if (messages != null)
        {
            if (hasJsonContentType())
            {
                request.
                    content(JsonModelConfig::builder)
                        .catalog()
                            .name(INLINE_CATALOG_NAME)
                            .inject(catalog -> injectSchemas(catalog, messages))
                            .build()
                        .build();
            }
        }
        return request;
    }

    private <C> CatalogedConfigBuilder<C> injectSchemas(
        CatalogedConfigBuilder<C> catalog,
        Map<String, AsyncapiMessage> messages)
    {
        for (String name : messages.keySet())
        {
            AsyncapiMessageView message = AsyncapiMessageView.of(asyncApi.components.messages, messages.get(name));
            String subject = message.refKey() != null ? message.refKey() : name;
            catalog
                .schema()
                    .subject(subject)
                    .build()
                .build();
        }
        return catalog;
    }

    private <C> HttpRequestConfigBuilder<C> injectPathParams(
        HttpRequestConfigBuilder<C> request,
        Map<String, AsyncapiParameter> parameters)
    {
        if (parameters != null)
        {
            for (String name : parameters.keySet())
            {
                AsyncapiParameter parameter = parameters.get(name);
                if (parameter.schema != null && parameter.schema.type != null)
                {
                    ModelConfig model = MODELS.get(parameter.schema.type);
                    if (model != null)
                    {
                        request
                            .pathParam()
                                .name(name)
                                .model(model)
                                .build();
                    }
                }
            }
        }
        return request;
    }

    private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
        RouteConfigBuilder<C> route,
        AsyncapiServerView server)
    {
        if (server.security() != null)
        {
            for (Map<String, List<String>> securityItem : server.security())
            {
                for (String securityItemLabel : securityItem.keySet())
                {
                    if (isJwtEnabled && "jwt".equals(securitySchemes.get(securityItemLabel)))
                    {
                        route
                            .guarded()
                                .name(guardName)
                                .inject(guarded -> injectGuardedRoles(guarded, securityItem.get(securityItemLabel)))
                                .build();
                        break;
                    }
                }
            }
        }
        return route;
    }

    private <C> GuardedConfigBuilder<C> injectGuardedRoles(
        GuardedConfigBuilder<C> guarded,
        List<String> roles)
    {
        for (String role : roles)
        {
            guarded.role(role);
        }
        return guarded;
    }

    private Map<String, String> resolveSecuritySchemes()
    {
        requireNonNull(asyncApi);
        Map<String, String> result = new HashMap<>();
        if (asyncApi.components != null && asyncApi.components.securitySchemes != null)
        {
            for (String securitySchemeName : asyncApi.components.securitySchemes.keySet())
            {
                String guardType = asyncApi.components.securitySchemes.get(securitySchemeName).bearerFormat;
                if ("jwt".equals(guardType))
                {
                    result.put(securitySchemeName, guardType);
                }
            }
        }
        return result;
    }

    private String resolveAuthorizationHeader()
    {
        requireNonNull(asyncApi);
        requireNonNull(asyncApi.components);
        String result = null;
        if (asyncApi.components.messages != null)
        {
            for (Map.Entry<String, AsyncapiMessage> entry : asyncApi.components.messages.entrySet())
            {
                AsyncapiMessage message = entry.getValue();
                if (message.headers != null && message.headers.properties != null)
                {
                    AsyncapiItem authorization = message.headers.properties.get("authorization");
                    if (authorization != null)
                    {
                        result = authorization.description;
                        break;
                    }
                }
            }
        }
        return result;
    }
}
