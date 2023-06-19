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

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Unit.COUNT;

import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.metrics.Metric;

public class OtlpMetricsDescriptor
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
    private final IntFunction<KindConfig> resolveKind;
    private final Map<String, String> kinds;
    private final Map<String, String> descriptions;
    private final Map<String, String> units;

    public OtlpMetricsDescriptor(
        Function<String, Metric> resolveMetric,
        IntFunction<KindConfig> resolveKind)
    {
        this.resolveMetric = resolveMetric;
        this.resolveKind = resolveKind;
        this.kinds = new Object2ObjectHashMap<>();
        this.descriptions = new Object2ObjectHashMap<>();
        this.units = new Object2ObjectHashMap<>();
    }

    public String kind(
        String internalName)
    {
        String result = kinds.get(internalName);
        if (result == null)
        {
            Metric.Kind kind = resolveMetric.apply(internalName).kind();
            result = kind == COUNTER ? "sum" : kind.toString().toLowerCase();
            kinds.put(internalName, result);
        }
        return result;
    }

    public String nameByBinding(
        String internalMetricName,
        int bindingId)
    {
        String result = null;
        KindConfig kind = resolveKind.apply(bindingId);
        Map<String, String> externalNames = KIND_METRIC_NAMES.get(kind);
        if (externalNames != null)
        {
            result = externalNames.get(internalMetricName);
        }
        return result != null ? result : internalMetricName;
    }

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
            Metric.Unit unit = resolveMetric.apply(internalName).unit();
            result = unit == COUNT ? "" : unit.toString().toLowerCase();
            units.put(internalName, result);
        }
        return result;
    }
}
