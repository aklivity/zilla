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

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static java.util.stream.Collectors.toList;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.agrona.collections.MutableInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiServerConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiServer;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiTrait;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfigBuilder;

public abstract class AsyncapiNamespaceGenerator
{
    protected static final String INLINE_CATALOG_NAME = "catalog0";
    protected static final String INLINE_CATALOG_TYPE = "inline";
    protected static final String VERSION_LATEST = "latest";
    protected static final String APPLICATION_JSON = "application/json";
    protected static final AsyncapiOptionsConfig EMPTY_OPTION =
        new AsyncapiOptionsConfig(null, null, null, null, null, null, null);
    protected static final Pattern VARIABLE = Pattern.compile("\\{([^}]*.?)\\}");
    protected final Matcher variable = VARIABLE.matcher("");

    protected Map<String, Asyncapi> asyncapis;
    protected boolean isTlsEnabled;
    protected String qname;
    protected String namespace;
    protected String qvault;
    protected String vault;

    public void init(
        BindingConfig binding)
    {
        this.qname = binding.qname;
        this.namespace = binding.namespace;
        this.qvault = binding.qvault;
        this.vault = binding.vault;
    }

    public NamespaceConfig generate(
        BindingConfig binding,
        AsyncapiNamespaceConfig namespaceConfig)
    {
        return null;
    }

    public NamespaceConfig generateProxy(
        BindingConfig binding,
        Map<String, Asyncapi> asyncapis,
        ToLongFunction<String> resolveApiId,
        List<String> labels)
    {
        return null;
    }

    protected AsyncapiProtocol resolveProtocol(
        String protocolName,
        AsyncapiOptionsConfig options,
        List<Asyncapi> asyncapis,
        List<AsyncapiServerView> servers)
    {
        Pattern pattern = Pattern.compile("(http|sse|mqtt|kafka)");
        Matcher matcher = pattern.matcher(protocolName);
        AsyncapiProtocol protocol = null;
        if (matcher.find())
        {
            switch (matcher.group())
            {
            case "http":
                protocol = new AsyncapiHttpProtocol(qname, asyncapis, options, protocolName);
                break;
            case "sse":
            case "secure-sse":
                final boolean httpServerAvailable = servers.stream().anyMatch(s -> "http".equals(s.protocol()));
                protocol = new AsyncapiSseProtocol(qname, httpServerAvailable, asyncapis, options, protocolName);
                break;
            case "mqtt":
                protocol = new AsyncapiMqttProtocol(qname, asyncapis, options, protocolName, namespace);
                break;
            case "kafka":
            case "kafka-secure":
                protocol = new AyncapiKafkaProtocol(qname, asyncapis, servers, options, protocolName);
                break;
            }
        }
        else
        {
            // TODO: should we do something?
        }
        return protocol;
    }

    protected List<AsyncapiServerView> filterAsyncapiServers(
        Asyncapi asyncapi,
        List<AsyncapiServerConfig> serverConfigs)
    {
        final Map<String, AsyncapiServer> servers = asyncapi.servers;
        List<AsyncapiServerView> filtered;
        Map<String, AsyncapiServerView> serverViews = servers.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey, e -> AsyncapiServerView.of(e.getValue(), asyncapi.components.serverVariables)));
        if (serverConfigs != null && !serverConfigs.isEmpty())
        {
            filtered = new ArrayList<>();
            serverConfigs.forEach(sc ->
                filtered
                    .addAll(serverViews.values().stream()
                        .filter(server ->
                        {
                            server.resolveHost(sc.host, sc.url);
                            return server.hostMatcher.reset(sc.host).matches() &&
                                server.urlMatcher.reset(sc.url).matches() &&
                                server.pathnameMatcher.reset(sc.pathname).matches();
                        })
                    .collect(Collectors.toList())));
        }
        else
        {
            filtered = new ArrayList<>(serverViews.values());
            filtered.forEach(s -> s.resolveHost("", ""));
        }

        return filtered;
    }

    public int[] resolveAllPorts(
        List<AsyncapiServerView> servers)
    {
        int[] ports = new int[servers.size()];
        for (int i = 0; i < servers.size(); i++)
        {
            AsyncapiServerView server = servers.get(i);
            final String[] hostAndPort = server.host().split(":");
            ports[i] = Integer.parseInt(hostAndPort[1]);
        }
        return ports;
    }

    public int[] resolvePorts(
        List<AsyncapiServerView> servers,
        boolean secure)
    {
        List<AsyncapiServerView> filtered =
            servers.stream().filter(s -> s.getAsyncapiProtocol().isSecure() == secure).collect(toList());
        int[] ports = new int[filtered.size()];
        MutableInteger index = new MutableInteger();
        filtered.forEach(s -> ports[index.value++] = s.getPort());
        return ports;
    }

    public int[] resolvePortForServer(
        AsyncapiServerView server,
        boolean secure)
    {
        int[] ports = {};

        if (server.getAsyncapiProtocol().isSecure() == secure)
        {
            ports = new int[] { server.getPort() };
        }

        return ports;
    }

    protected <C> NamespaceConfigBuilder<C> injectCatalog(
        NamespaceConfigBuilder<C> namespace,
        List<Asyncapi> asyncapis)
    {
        final boolean injectCatalog = asyncapis.stream()
            .anyMatch(a -> a.components != null && a.components.schemas != null && !a.components.schemas.isEmpty());
        if (injectCatalog)
        {
            namespace
                .catalog()
                    .name(INLINE_CATALOG_NAME)
                    .type(INLINE_CATALOG_TYPE)
                    .options(InlineOptionsConfig::builder)
                        .subjects()
                            .inject(s -> injectSubjects(s, asyncapis))
                            .build()
                        .build()
                    .build();
        }
        return namespace;
    }

    protected <C> InlineSchemaConfigBuilder<C> injectSubjects(
        InlineSchemaConfigBuilder<C> subjects,
        List<Asyncapi> asyncapis)
    {
        for (Asyncapi asyncapi : asyncapis)
        {
            if (asyncapi.components != null && asyncapi.components.schemas != null && !asyncapi.components.schemas.isEmpty())
            {
                try (Jsonb jsonb = JsonbBuilder.create())
                {
                    YAMLMapper yaml = YAMLMapper.builder()
                        .disable(WRITE_DOC_START_MARKER)
                        .enable(MINIMIZE_QUOTES)
                        .build();
                    for (Map.Entry<String, AsyncapiSchema> entry : asyncapi.components.schemas.entrySet())
                    {
                        AsyncapiSchemaView schema = AsyncapiSchemaView.of(asyncapi.components.schemas, entry.getValue());

                        subjects
                            .subject(entry.getKey())
                            .version(VERSION_LATEST)
                            .schema(writeSchemaYaml(jsonb, yaml, schema))
                            .build();
                    }
                    if (asyncapi.components.messageTraits != null)
                    {
                        for (Map.Entry<String, AsyncapiTrait> entry : asyncapi.components.messageTraits.entrySet())
                        {
                            entry.getValue().headers.properties.forEach((k, v) ->
                                subjects
                                    .subject(k)
                                    .version(VERSION_LATEST)
                                    .schema(writeSchemaYaml(jsonb, yaml, v))
                                    .build());
                        }
                    }
                }
                catch (Exception ex)
                {
                    rethrowUnchecked(ex);
                }
            }
        }
        return subjects;
    }

    protected static String writeSchemaYaml(
        Jsonb jsonb,
        YAMLMapper yaml,
        Object schema)
    {
        String result = null;
        try
        {
            String schemaJson = jsonb.toJson(schema);
            JsonNode json = new ObjectMapper().readTree(schemaJson);
            result = yaml.writeValueAsString(json);
        }
        catch (JsonProcessingException ex)
        {
            rethrowUnchecked(ex);
        }
        return result;
    }

    protected  <C> BindingConfigBuilder<C> injectMetrics(
        BindingConfigBuilder<C> binding,
        List<MetricRefConfig> metricRefs)
    {
        List<MetricRefConfig> metrics = metricRefs.stream()
            .filter(m -> m.name.startsWith("stream."))
            .collect(toList());

        if (!metrics.isEmpty())
        {
            final TelemetryRefConfigBuilder<BindingConfigBuilder<C>> telemetry = binding.telemetry();
            metrics.forEach(telemetry::metric);
            telemetry.build();
        }

        return binding;
    }

    protected <C> NamespaceConfigBuilder<C> injectNamespaceMetric(
         NamespaceConfigBuilder<C> namespace,
        boolean hasMetrics)
    {
        if (hasMetrics)
        {
            namespace
                .telemetry()
                    .metric()
                        .group("stream")
                        .name("stream.active.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.active.sent")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.opens.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.opens.sent")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.data.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.data.sent")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.errors.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.errors.sent")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.closes.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.closes.sent")
                        .build()
                    .build();
        }

        return namespace;
    }
}
