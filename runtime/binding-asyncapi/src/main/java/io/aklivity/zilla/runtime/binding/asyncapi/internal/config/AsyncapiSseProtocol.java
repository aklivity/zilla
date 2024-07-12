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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSecurityScheme;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSecuritySchemeView;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.sse.config.SseConditionConfig;
import io.aklivity.zilla.runtime.binding.sse.config.SseOptionsConfig;
import io.aklivity.zilla.runtime.binding.sse.config.SseOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.sse.config.SsePathConfigBuilder;
import io.aklivity.zilla.runtime.common.feature.Incubating;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

@Incubating
public class AsyncapiSseProtocol extends AsyncapiProtocol
{
    private static final String SCHEME = "sse";
    private static final String SECURE_PROTOCOL = "secure-sse";

    private final boolean httpServerAvailable;
    private final AsyncapiOptionsConfig options;

    protected AsyncapiSseProtocol(
        String qname,
        boolean httpServerAvailable,
        List<Asyncapi> asyncapis,
        AsyncapiOptionsConfig options,
        String protocol)
    {
        super(qname, asyncapis, protocol, SCHEME, "http");
        this.httpServerAvailable = httpServerAvailable;
        this.options = options;
    }

    @Override
    public <C> NamespaceConfigBuilder<C> injectProtocolRelatedServerBindings(
        NamespaceConfigBuilder<C> namespace,
        List<MetricRefConfig> metricRefs)
    {
        if (!httpServerAvailable)
        {
            final AsyncapiProtocol httpProtocol = new AsyncapiHttpProtocol(qname, asyncapis, options, "http");

            namespace
                .binding()
                    .name(String.format("%s_server0", httpProtocol.scheme))
                    .type(httpProtocol.scheme)
                    .inject(b -> this.injectMetrics(b, metricRefs))
                    .kind(SERVER)
                    .inject(httpProtocol::injectProtocolServerOptions)
                    .inject(b -> httpProtocol.injectProtocolServerRoutes(b, qname, options))
                .build();
        }
        return namespace;
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerOptions(
        BindingConfigBuilder<C> binding)
    {
        binding
            .options(SseOptionsConfig::builder)
            .inject(this::injectSsePathsOptions)
            .build();
        return binding;
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerRoutes(
        BindingConfigBuilder<C> binding,
        String qname,
        AsyncapiOptionsConfig options)
    {
        for (Asyncapi asyncapi : asyncapis)
        {
            for (String name : asyncapi.operations.keySet())
            {
                AsyncapiOperation operation = asyncapi.operations.get(name);
                if (operation.bindings == null)
                {
                    AsyncapiChannelView channel = AsyncapiChannelView.of(asyncapi.channels, operation.channel);
                    String path = channel.address().replaceAll("\\{[^}]+\\}", "*");
                    binding
                        .route()
                        .exit(qname)
                        .when(SseConditionConfig::builder)
                            .path(path)
                            .build()
                        .inject(route -> injectHttpServerRouteGuarded(
                                        route, qname, options.http, asyncapi, operation.security))
                        .build();
                }
            }
        }
        return binding;
    }

    @Override
    public <C> NamespaceConfigBuilder<C> injectProtocolRelatedClientBindings(
        NamespaceConfigBuilder<C> namespace,
        List<MetricRefConfig> metricRefs,
        boolean isTlsEnabled)
    {
        if (!httpServerAvailable)
        {
            namespace
                .binding()
                .name(String.format("%s_client0", "http"))
                .type("http")
                .kind(CLIENT)
                .inject(b -> this.injectMetrics(b, metricRefs))
                .exit(isTlsEnabled ? "tls_client0" : "tcp_client0")
                .build();
        }
        return namespace;
    }

    @Override
    protected boolean isSecure()
    {
        return protocol.equals(SECURE_PROTOCOL);
    }


    private <C> SseOptionsConfigBuilder<C> injectSsePathsOptions(
        SseOptionsConfigBuilder<C> options)
    {
        for (Asyncapi asyncapi : asyncapis)
        {
            for (Map.Entry<String, AsyncapiChannel> channelEntry : asyncapi.channels.entrySet())
            {
                String path = channelEntry.getValue().address.replaceAll("\\{[^}]+\\}", "*");
                Map<String, AsyncapiMessage> messages = channelEntry.getValue().messages;
                if (messages != null)
                {
                    options
                        .request()
                        .path(path)
                        .inject(c -> injectValue(c, asyncapi, messages))
                        .build();
                }
            }
        }
        return options;
    }

    private <C> SsePathConfigBuilder<C> injectValue(
        SsePathConfigBuilder<C> request,
        Asyncapi asyncapi,
        Map<String, AsyncapiMessage> messages)
    {
        if (messages != null)
        {
            request.content(JsonModelConfig::builder)
                .catalog()
                .name(INLINE_CATALOG_NAME)
                .inject(cataloged -> injectValueSchemas(cataloged, asyncapi, messages))
                .build()
                .build();
        }
        return request;
    }

    private <C> RouteConfigBuilder<C> injectHttpServerRouteGuarded(
        RouteConfigBuilder<C> route,
        String qname,
        HttpOptionsConfig options,
        Asyncapi asyncapi,
        List<AsyncapiSecurityScheme> securities)
    {
        if (securities != null && !securities.isEmpty())
        {
            AsyncapiSecuritySchemeView security =
                AsyncapiSecuritySchemeView.of(asyncapi.components.securitySchemes, securities.get(0));

            if ("oauth2".equals(security.type()))
            {
                route
                    .guarded()
                    .name(String.format("%s:%s", qname, options.authorization.name))
                    .inject(guarded -> injectGuardedRoles(guarded, security.scopes()))
                    .build();
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
}
