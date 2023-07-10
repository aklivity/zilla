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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntFunction;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.reader.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.ScalarRecord;

public class OtlpMetricsSerializer
{
    private static final String SCOPE_NAME = "OtlpMetricsSerializer";
    private static final String SCOPE_VERSION = "1.0.0";
    // CUMULATIVE is an AggregationTemporality for a metric aggregator which reports changes since a fixed start time.
    private static final int CUMULATIVE = 2;
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

    private final List<MetricRecord> records;
    private final List<AttributeConfig> attributes;
    private final IntFunction<KindConfig> resolveKind;
    private final Function<String, Metric> resolveMetric;
    private final OtlpMetricsDescriptor descriptor;

    public OtlpMetricsSerializer(
        List<MetricRecord> records,
        List<AttributeConfig> attributes,
        Function<String, Metric> resolveMetric,
        IntFunction<KindConfig> resolveKind)
    {
        this.records = records;
        this.attributes = attributes;
        this.resolveMetric = resolveMetric;
        this.resolveKind = resolveKind;
        this.descriptor = new OtlpMetricsDescriptor();
    }

    public String serializeAll()
    {
        JsonArrayBuilder attributesArray = Json.createArrayBuilder();
        attributes.forEach(attr -> attributesArray.add(attributeToJson(attr)));
        JsonArrayBuilder metricsArray = Json.createArrayBuilder();
        records.forEach(metric -> metricsArray.add(serialize(metric)));
        return createJson(attributesArray, metricsArray);
    }

    private JsonObject serialize(
        MetricRecord metric)
    {
        JsonObject result = null;
        if (metric.getClass().equals(ScalarRecord.class))
        {
            result = serializeScalar((ScalarRecord) metric);
        }
        else if (metric.getClass().equals(HistogramRecord.class))
        {
            result = serializeHistogram((HistogramRecord) metric);
        }
        return result;
    }

    private JsonObject serializeScalar(
        ScalarRecord record)
    {
        JsonObject dataPoint = Json.createObjectBuilder()
            .add("asInt", record.valueReader().getAsLong())
            .add("timeUnixNano", now())
            .add("attributes", attributes(record))
            .build();
        JsonArray dataPoints = Json.createArrayBuilder()
            .add(dataPoint)
            .build();
        String kind = descriptor.kind(record.metricName());
        JsonObjectBuilder scalarData = Json.createObjectBuilder()
            .add("dataPoints", dataPoints);
        if ("sum".equals(kind))
        {
            scalarData
                .add("aggregationTemporality", CUMULATIVE)
                .add("isMonotonic", true);
        }
        return Json.createObjectBuilder()
            .add("name", descriptor.nameByBinding(record.metricName(), record.bindingId()))
            .add("unit", descriptor.unit(record.metricName()))
            .add("description", descriptor.description(record.metricName()))
            .add(kind, scalarData)
            .build();
    }

    private long now()
    {
        return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    }

    private JsonArrayBuilder attributes(
        MetricRecord record)
    {
        return attributesToJson(List.of(
            new AttributeConfig("namespace", record.namespaceName()),
            new AttributeConfig("binding", record.bindingName())
        ));
    }

    private JsonArrayBuilder attributesToJson(
        List<AttributeConfig> attributes)
    {
        JsonArrayBuilder result = Json.createArrayBuilder();
        attributes.forEach(attribute -> result.add(attributeToJson(attribute)));
        return result;
    }

    private JsonObject attributeToJson(
        AttributeConfig attributeConfig)
    {
        JsonObject value = Json.createObjectBuilder()
            .add("stringValue", attributeConfig.value)
            .build();
        return Json.createObjectBuilder()
            .add("key", attributeConfig.name)
            .add("value", value)
            .build();
    }

    private JsonObject serializeHistogram(
        HistogramRecord record)
    {
        record.update();
        // Histogram buckets are inclusive of their upper boundary, except the last bucket where the boundary is at infinity.
        JsonArrayBuilder explicitBounds = Json.createArrayBuilder();
        Arrays.stream(record.bucketLimits()).limit(record.buckets() - 1).forEach(explicitBounds::add);
        // The number of elements in bucket_counts must be by one greater than the number of elements in explicit_bounds.
        JsonArrayBuilder bucketCounts = Json.createArrayBuilder();
        Arrays.stream(record.bucketValues()).forEach(bucketCounts::add);
        JsonObject dataPoint = Json.createObjectBuilder()
            .add("timeUnixNano", now())
            .add("attributes", attributes(record))
            .add("min", record.stats()[0])
            .add("max", record.stats()[1])
            .add("sum", record.stats()[2])
            .add("count", record.stats()[3])
            .add("explicitBounds", explicitBounds)
            .add("bucketCounts", bucketCounts)
            .build();
        JsonArray dataPoints = Json.createArrayBuilder()
            .add(dataPoint)
            .build();
        JsonObjectBuilder histogramData = Json.createObjectBuilder()
            .add("aggregationTemporality", CUMULATIVE)
            .add("dataPoints", dataPoints);
        return Json.createObjectBuilder()
            .add("name", descriptor.nameByBinding(record.metricName(), record.bindingId()))
            .add("description", descriptor.description(record.metricName()))
            .add("unit", descriptor.unit(record.metricName()))
            .add("histogram", histogramData)
            .build();
    }

    private String createJson(
        JsonArrayBuilder attributes,
        JsonArrayBuilder metricsArray)
    {
        JsonObject resource = Json.createObjectBuilder()
            .add("attributes", attributes)
            .build();
        JsonObject scope = Json.createObjectBuilder()
            .add("name", SCOPE_NAME)
            .add("version", SCOPE_VERSION)
            .build();
        JsonObject scopeMetrics = Json.createObjectBuilder()
            .add("scope", scope)
            .add("metrics", metricsArray)
            .build();
        JsonArray scopeMetricsArray = Json.createArrayBuilder()
            .add(scopeMetrics)
            .build();
        JsonObject resourceMetrics = Json.createObjectBuilder()
            .add("resource", resource)
            .add("scopeMetrics", scopeMetricsArray)
            .build();
        JsonArray resourceMetricsArray = Json.createArrayBuilder()
            .add(resourceMetrics)
            .build();
        JsonObject jsonObject = Json.createObjectBuilder()
            .add("resourceMetrics", resourceMetricsArray)
            .build();
        return jsonObject.toString();
    }

    final class OtlpMetricsDescriptor
    {
        private final Map<String, String> kinds;
        private final Map<String, String> descriptions;
        private final Map<String, String> units;

        private OtlpMetricsDescriptor()
        {
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
}
