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

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiServer;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfigBuilder;

public abstract class AsyncapiProtocol
{
    protected static final String INLINE_CATALOG_NAME = "catalog0";
    protected static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");
    protected static final Pattern VARIABLE = Pattern.compile("\\{([^}]*.?)\\}");
    protected static final String VERSION_LATEST = "latest";

    protected final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");
    protected final Matcher variable = VARIABLE.matcher("");

    protected Asyncapi asyncApi;
    protected String qname;
    public final String scheme;
    public final String secureScheme;

    protected AsyncapiProtocol(
        String qname,
        Asyncapi asyncApi,
        String scheme,
        String secureScheme)
    {
        this.qname = qname;
        this.asyncApi = asyncApi;
        this.scheme = scheme;
        this.secureScheme = secureScheme;
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
        Map<String, AsyncapiMessage> messages,
        String contentType)
    {
        for (Map.Entry<String, AsyncapiMessage> messageEntry : messages.entrySet())
        {
            AsyncapiMessageView message =
                AsyncapiMessageView.of(asyncApi.components.messages, messageEntry.getValue());
            String schema = messageEntry.getKey();
            if (message.contentType() != null && message.contentType().equals(contentType) ||
                jsonContentType.reset(asyncApi.defaultContentType).matches())
            {
                cataloged
                    .schema()
                        .version(VERSION_LATEST)
                        .subject(schema)
                        .build()
                    .build();
            }
            else
            {
                throw new RuntimeException("Invalid content type");
            }
        }
        return cataloged;
    }

    protected boolean hasJsonContentType()
    {
        String contentType = null;
        if (asyncApi.components != null && asyncApi.components.messages != null &&
            !asyncApi.components.messages.isEmpty())
        {
            AsyncapiMessage firstAsyncapiMessage = asyncApi.components.messages.entrySet().stream()
                .findFirst().get().getValue();
            contentType = AsyncapiMessageView.of(asyncApi.components.messages, firstAsyncapiMessage).contentType();
        }
        return contentType != null && jsonContentType.reset(contentType).matches() ||
            jsonContentType.reset(asyncApi.defaultContentType).matches();
    }

    protected abstract boolean isSecure();

    protected int[] resolvePorts()
    {
        requireNonNull(scheme);
        int[] ports = null;

        for (AsyncapiServer s : asyncApi.servers.values())
        {
            String[] hostAndPort = s.host.split(":");
            String port = hostAndPort[1];
            if (variable.reset(port).find())
            {
                String resolvedPort = s.variables.get(variable.group(1)).defaultValue;
                s.host = s.host.replace(port, resolvedPort);
                ports = new int[] {Integer.parseInt(resolvedPort)};
            }
            else
            {
                ports = new int[] {Integer.parseInt(port)};
            }
            break;
        }
        return ports;
    }

    protected URI findFirstServerUrlWithScheme(
        String scheme)
    {
        requireNonNull(scheme);
        URI result = null;
        for (String key : asyncApi.servers.keySet())
        {
            AsyncapiServerView server = AsyncapiServerView.of(asyncApi.servers.get(key));
            if (scheme.equals(server.url().getScheme()))
            {
                result = server.url();
                break;
            }
        }
        return result;
    }

    protected <C> BindingConfigBuilder<C> injectMetrics(
        BindingConfigBuilder<C> binding,
        List<MetricRefConfig> metricRefs,
        String protocol)
    {
        final TelemetryRefConfigBuilder<BindingConfigBuilder<C>> telemetry = binding.telemetry();
        metricRefs.stream()
            .filter(m -> m.name.startsWith("stream."))
            .collect(Collectors.toList())
            .forEach(m -> telemetry.metric(m));
        telemetry.build();
        return binding;
    }
}
