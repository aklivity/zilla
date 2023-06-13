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
package io.aklivity.zilla.runtime.exporter.otlp.internal.serializer;

import java.util.Map;
import java.util.function.Function;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricDescriptor;

public class OtlpMetricsDescriptor implements MetricDescriptor
{
    private static final Map<String, String> SERVER_METRIC_NAMES = Map.of(
        "http.request.size", "http.server.request.size",
        "http.response.size", "http.server.response.size",
        "http.duration", "http.server.duration",
        "http.active.requests", "http.server.active_requests"
    );
    private static final Map<String, String> CLIENT_METRIC_NAMES = Map.of(
        "http.request.size", "http.client.request.size",
        "http.response.size", "http.client.response.size",
        "http.duration", "http.client.duration"
    );
    private static final Map<KindConfig, Map<String, String>> KIND_METRIC_NAMES = Map.of(
        KindConfig.SERVER, SERVER_METRIC_NAMES,
        KindConfig.CLIENT, CLIENT_METRIC_NAMES
    );

    private final Function<String, Metric> resolveMetric;
    private final Function<String, KindConfig> findBindingKind;
    private final Map<String, String> kinds;
    private final Map<String, String> descriptions;
    private final Map<String, String> units;

    public OtlpMetricsDescriptor(
        Function<String, Metric> resolveMetric,
        Function<String, KindConfig> findBindingKind)
    {
        this.resolveMetric = resolveMetric;
        this.findBindingKind = findBindingKind;
        this.kinds = new Object2ObjectHashMap<>();
        this.descriptions = new Object2ObjectHashMap<>();
        this.units = new Object2ObjectHashMap<>();
    }

    @Override
    public String kind(
        String internalName)
    {
        String result = kinds.get(internalName);
        if (result == null)
        {
            result = resolveMetric.apply(internalName).kind().toString().toLowerCase();
            if ("counter".equals(result))
            {
                result = "sum";
            }
            kinds.put(internalName, result);
        }
        return result;
    }

    @Override
    public String name(
        String internalName)
    {
        throw new RuntimeException("not implemented");
    }

    public String nameByBinding(
        String internalMetricName,
        String bindingName)
    {
        String result = null;
        KindConfig kind = findBindingKind.apply(bindingName);
        Map<String, String> externalNames = KIND_METRIC_NAMES.get(kind);
        if (externalNames != null)
        {
            result = externalNames.get(internalMetricName);
        }
        return result != null ? result : internalMetricName;
    }

    @Override
    public String description(
        String internalName)
    {
        String result = descriptions.get(internalName);
        if (result == null)
        {
            result = resolveMetric.apply(internalName).description();
            descriptions.put(internalName, result);
        }
        return result;
    }

    public String unit(
        String internalName)
    {
        String result = units.get(internalName);
        if (result == null)
        {
            result = resolveMetric.apply(internalName).unit().toString().toLowerCase();
            units.put(internalName, result);
        }
        return result;
    }
}
