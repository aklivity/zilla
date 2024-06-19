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

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfigBuilder;

public abstract class AsyncapiProtocol
{
    protected static final String INLINE_CATALOG_NAME = "catalog0";
    protected static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");
    protected static final String VERSION_LATEST = "latest";

    protected final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");
    protected final List<Asyncapi> asyncapis;
    protected String qname;
    protected Map<String, String> securitySchemes;
    protected boolean isJwtEnabled;
    public final String scheme;
    public final String tcpRoute;
    public final String protocol;

    protected AsyncapiProtocol(
        String qname,
        List<Asyncapi> asyncapis,
        String protocol,
        String scheme)
    {
        this(qname, asyncapis, protocol, scheme, scheme);
    }

    protected AsyncapiProtocol(
        String qname,
        List<Asyncapi> asyncapis,
        String protocol,
        String scheme,
        String tcpRoute)
    {
        this.qname = qname;
        this.asyncapis = asyncapis;
        this.protocol = protocol;
        this.scheme = scheme;
        this.tcpRoute = tcpRoute;
        this.securitySchemes = resolveSecuritySchemes();
        this.isJwtEnabled = !securitySchemes.isEmpty();
    }

    public <C> NamespaceConfigBuilder<C> injectProtocolRelatedServerBindings(
        NamespaceConfigBuilder<C> namespace,
        List<MetricRefConfig> metricRefs)
    {
        return namespace;
    }

    public <C> NamespaceConfigBuilder<C> injectProtocolRelatedClientBindings(
        NamespaceConfigBuilder<C> namespace,
        List<MetricRefConfig> metricRefs,
        boolean isTlsEnabled)
    {
        return namespace;
    }

    public abstract <C>BindingConfigBuilder<C> injectProtocolServerOptions(
        BindingConfigBuilder<C> binding);

    public abstract <C> BindingConfigBuilder<C> injectProtocolServerRoutes(
        BindingConfigBuilder<C> binding);

    public <C> NamespaceConfigBuilder<C> injectProtocolClientCache(
        NamespaceConfigBuilder<C> namespace,
        List<MetricRefConfig> metricRefs)
    {
        return namespace;
    }

    public <C>BindingConfigBuilder<C> injectProtocolClientOptions(
        BindingConfigBuilder<C> binding)
    {
        return binding;
    }

    protected <C> CatalogedConfigBuilder<C> injectJsonSchemas(
        CatalogedConfigBuilder<C> cataloged,
        Asyncapi asyncapi,
        Map<String, AsyncapiMessage> messages,
        String contentType)
    {
        for (Map.Entry<String, AsyncapiMessage> messageEntry : messages.entrySet())
        {
            AsyncapiMessageView message =
                AsyncapiMessageView.of(asyncapi.components.messages, messageEntry.getValue());
            if (message.payload() != null)
            {
                String schema = AsyncapiSchemaView.of(asyncapi.components.schemas, message.payload()).refKey();
                if (message.contentType() != null && message.contentType().equals(contentType) ||
                    jsonContentType.reset(asyncapi.defaultContentType).matches())
                {
                    cataloged
                        .schema()
                            .version(VERSION_LATEST)
                            .subject(schema)
                            .build();
                }
                else
                {
                    throw new RuntimeException("Invalid content type");
                }
            }
        }
        return cataloged;
    }

    protected boolean hasJsonContentType(
        Asyncapi asyncapi)
    {
        String contentType = null;
        if (asyncapi.components != null && asyncapi.components.messages != null &&
            !asyncapi.components.messages.isEmpty())
        {
            AsyncapiMessage firstAsyncapiMessage = asyncapi.components.messages.entrySet().stream()
                .findFirst().get().getValue();
            contentType = AsyncapiMessageView.of(asyncapi.components.messages, firstAsyncapiMessage).contentType();
        }
        return contentType != null && jsonContentType.reset(contentType).matches() || asyncapi.defaultContentType != null &&
            jsonContentType.reset(asyncapi.defaultContentType).matches();
    }

    protected abstract boolean isSecure();

    protected Map<String, String> resolveSecuritySchemes()
    {
        requireNonNull(asyncapis);
        Map<String, String> result = new HashMap<>();
        for (Asyncapi asyncapi : asyncapis)
        {
            if (asyncapi.components != null && asyncapi.components.securitySchemes != null)
            {
                for (String securitySchemeName : asyncapi.components.securitySchemes.keySet())
                {
                    String guardType = asyncapi.components.securitySchemes.get(securitySchemeName).bearerFormat;
                    //TODO: change when jwt support added for mqtt in asyncapi
                    //if ("jwt".equals(guardType))
                    //{
                    //    result.put(securitySchemeName, guardType);
                    //}
                    result.put(securitySchemeName, guardType);
                }
            }
        }
        return result;
    }

    protected <C> BindingConfigBuilder<C> injectMetrics(
        BindingConfigBuilder<C> binding,
        List<MetricRefConfig> metricRefs)
    {
        if (metricRefs != null && !metricRefs.isEmpty())
        {
            final TelemetryRefConfigBuilder<BindingConfigBuilder<C>> telemetry = binding.telemetry();
            metricRefs.stream()
                .filter(m -> m.name.startsWith("stream."))
                .collect(Collectors.toList())
                .forEach(telemetry::metric);
            telemetry.build();
        }
        return binding;
    }
}
