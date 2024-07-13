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

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfigBuilder;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public abstract class AsyncapiProtocol
{
    protected static final String INLINE_CATALOG_NAME = "catalog0";
    protected static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");
    protected static final Pattern AVRO_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?avro$");
    protected static final Pattern PROTOBUF_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?protobuf$");
    protected static final String VERSION_LATEST = "latest";

    protected final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");
    protected final Matcher avroContentType = AVRO_CONTENT_TYPE.matcher("");
    protected final Matcher protobufContentType = PROTOBUF_CONTENT_TYPE.matcher("");
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
        BindingConfigBuilder<C> binding,
        AsyncapiOptionsConfig options);

    public <C> NamespaceConfigBuilder<C> injectProtocolClientCache(
        NamespaceConfigBuilder<C> namespace,
        List<MetricRefConfig> metricRefs,
        AsyncapiOptionsConfig options)
    {
        return namespace;
    }

    public <C>BindingConfigBuilder<C> injectProtocolClientOptions(
        BindingConfigBuilder<C> binding)
    {
        return binding;
    }

    protected <C> CatalogedConfigBuilder<C> injectKeySchema(
        CatalogedConfigBuilder<C> cataloged,
        Asyncapi asyncapi,
        AsyncapiMessageView message)
    {
        String schema = AsyncapiSchemaView.of(asyncapi.components.schemas, message.key()).refKey();
        cataloged.schema()
            .version(VERSION_LATEST)
            .subject(schema)
            .build();
        return cataloged;
    }

    protected <C> CatalogedConfigBuilder<C> injectValueSchema(
        CatalogedConfigBuilder<C> cataloged,
        Asyncapi asyncapi,
        AsyncapiMessageView message)
    {
        String schema = AsyncapiSchemaView.of(asyncapi.components.schemas, message.payload()).refKey();
        cataloged.schema()
            .version(VERSION_LATEST)
            .subject(schema)
            .build();
        return cataloged;
    }

    protected <C> CatalogedConfigBuilder<C> injectValueSchemas(
        CatalogedConfigBuilder<C> cataloged,
        Asyncapi asyncapi,
        Map<String, AsyncapiMessage> messages)
    {
        for (Map.Entry<String, AsyncapiMessage> messageEntry : messages.entrySet())
        {
            AsyncapiMessageView message =
                AsyncapiMessageView.of(asyncapi.components.messages, messageEntry.getValue());
            String schema = AsyncapiSchemaView.of(asyncapi.components.schemas, message.payload()).refKey();
            cataloged.schema()
                .version(VERSION_LATEST)
                .subject(schema)
                .build();
        }

        return cataloged;
    }

    protected ModelConfig injectModel(
        Asyncapi asyncapi,
        AsyncapiMessageView message)
    {
        String contentType = message.contentType() == null ? asyncapi.defaultContentType : message.contentType();
        ModelConfig model = null;

        if (contentType == null)
        {
            model = null;
        }
        else if (jsonContentType.reset(contentType).matches())
        {
            model = JsonModelConfig.builder()
                    .catalog()
                    .name(INLINE_CATALOG_NAME)
                    .inject(catalog -> injectValueSchema(catalog, asyncapi, message))
                    .build()
                .build();
        }
        else if (avroContentType.reset(contentType).matches())
        {
            model = AvroModelConfig.builder()
                .view("json")
                .catalog()
                    .name(INLINE_CATALOG_NAME)
                    .inject(catalog -> injectValueSchema(catalog, asyncapi, message))
                    .build()
                .build();
        }
        else if (protobufContentType.reset(contentType).matches())
        {
            model = ProtobufModelConfig.builder()
                .view("json")
                .catalog()
                    .name(INLINE_CATALOG_NAME)
                    .inject(catalog -> injectValueSchema(catalog, asyncapi, message))

                    .build()
                .build();
        }

        return model;
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
                    //TODO: change when jwt support added for mqtt in asyncapi
                    //String guardType = asyncapi.components.securitySchemes.get(securitySchemeName).bearerFormat;
                    //if ("jwt".equals(guardType))
                    //{
                    //    result.put(securitySchemeName, guardType);
                    //}
                    result.put(securitySchemeName, "");
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
