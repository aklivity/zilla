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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;


import static java.util.stream.Collectors.toList;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiServer;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiServerView;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public abstract class OpenapiNamespaceGenerator
{
    protected static final String INLINE_CATALOG_NAME = "catalog0";
    protected static final String INLINE_CATALOG_TYPE = "inline";
    protected static final String VERSION_LATEST = "latest";
    protected static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");
    protected static final OpenapiOptionsConfig EMPTY_OPTIONS = OpenapiOptionsConfig.builder().build();

    protected final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");
    protected final Map<String, ModelConfig> models = Map.of(
        "string", StringModelConfig.builder().build(),
        "integer", Int32ModelConfig.builder().build()
    );

    public abstract NamespaceConfig generate(
        BindingConfig binding,
        Openapi openapi);

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

    protected  <C> BindingConfigBuilder<C> injectMetrics(
        BindingConfigBuilder<C> binding,
        List<MetricRefConfig> metricRefs,
        String protocol)
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

    protected int[] resolvePortsForScheme(
        String scheme,
        List<OpenapiServerView> serverUrls)
    {
        List<URI> servers = findServersUrlWithScheme(scheme, serverUrls);
        int[] ports = new int[servers.size()];
        MutableInteger index = new MutableInteger();
        servers.forEach(s -> ports[index.value++] = s.getPort());
        return ports;
    }

    protected List<URI> findServersUrlWithScheme(
        String scheme,
        List<OpenapiServerView> servers)
    {
        return servers.stream()
            .map(OpenapiServerView::url)
            .filter(url -> scheme == null || url.getScheme().equals(scheme))
            .collect(toList());
    }

    protected List<OpenapiServerView> filterOpenapiServers(
        List<OpenapiServer> servers,
        List<OpenapiServerConfig> serverConfigs)
    {
        List<OpenapiServerView> filtered;
        List<OpenapiServerView> serverViews = servers.stream()
            .map(s -> OpenapiServerView.of(s, s.variables))
            .collect(toList());
        if (serverConfigs != null && !serverConfigs.isEmpty())
        {
            filtered = new ArrayList<>();
            serverConfigs.forEach(sc ->
                filtered.addAll(serverViews.stream()
                    .filter(e ->
                    {
                        e.resolveURL(sc.url);
                        return e.urlMatcher.reset(sc.url).matches();
                    })
                    .collect(toList())));
        }
        else
        {
            filtered = new ArrayList<>(serverViews);
            filtered.forEach(s -> s.resolveURL(""));
        }

        return filtered;
    }
}
