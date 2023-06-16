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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.metrics.processor.MetricsProcessor;
import io.aklivity.zilla.runtime.engine.metrics.record.CounterGaugeRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.MetricRecord;

public class OtlpMetricsSerializer
{
    private static final String SCOPE_NAME = "OtlpMetricsSerializer";
    private static final String SCOPE_VERSION = "1.0.0";
    // CUMULATIVE is an AggregationTemporality for a metric aggregator which reports changes since a fixed start time.
    private static final int CUMULATIVE = 2;

    private final MetricsProcessor metricsProcessor;
    private final List<AttributeConfig> attributes;
    private final Function<String, String> supplyKind;
    private final BiFunction<String, String, String> supplyName;
    private final Function<String, String> supplyDescription;
    private final Function<String, String> supplyUnit;

    // required for testing
    private long timeStamp;

    public OtlpMetricsSerializer(
        MetricsProcessor metricsProcessor,
        List<AttributeConfig> attributes,
        Function<String, String> supplyKind,
        BiFunction<String, String, String> supplyName,
        Function<String, String> supplyDescription,
        Function<String, String> supplyUnit)
    {
        this.metricsProcessor = metricsProcessor;
        this.attributes = attributes;
        this.supplyKind = supplyKind;
        this.supplyName = supplyName;
        this.supplyDescription = supplyDescription;
        this.supplyUnit = supplyUnit;
        this.timeStamp = 0;
    }

    public String serializeAll()
    {
        JsonArrayBuilder attributesArray = Json.createArrayBuilder();
        attributes.forEach(attr -> attributesArray.add(attributeToJson(attr)));
        JsonArrayBuilder metricsArray = Json.createArrayBuilder();
        metricsProcessor.getRecords().forEach(metric -> metricsArray.add(serialize(metric)));
        return createJson(attributesArray, metricsArray);
    }

    // required for testing
    void timeStamp(
        long timeStamp)
    {
        this.timeStamp = timeStamp;
    }

    private JsonObject serialize(
        MetricRecord metric)
    {
        JsonObject result = null;
        if (metric.getClass().equals(CounterGaugeRecord.class))
        {
            result = serializeCounterGauge((CounterGaugeRecord) metric);
        }
        else if (metric.getClass().equals(HistogramRecord.class))
        {
            result = serializeHistogram((HistogramRecord) metric);
        }
        return result;
    }

    private JsonObject serializeCounterGauge(
        CounterGaugeRecord record)
    {
        JsonObject dataPoint = Json.createObjectBuilder()
            .add("asInt", record.value())
            .add("timeUnixNano", now())
            .add("attributes", attributes(record))
            .build();
        JsonArray dataPoints = Json.createArrayBuilder()
            .add(dataPoint)
            .build();
        String kind = supplyKind.apply(record.metricName());
        JsonObjectBuilder counterGaugeData = Json.createObjectBuilder()
            .add("dataPoints", dataPoints);
        if ("sum".equals(kind))
        {
            counterGaugeData
                .add("aggregationTemporality", CUMULATIVE)
                .add("isMonotonic", true);
        }
        return Json.createObjectBuilder()
            .add("name", supplyName.apply(record.metricName(), record.bindingName()))
            .add("unit", supplyUnit.apply(record.metricName()))
            .add("description", supplyDescription.apply(record.metricName()))
            .add(kind, counterGaugeData)
            .build();
    }

    private long now()
    {
        return timeStamp != 0 ? timeStamp : TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
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
            .add("name", supplyName.apply(record.metricName(), record.bindingName()))
            .add("description", supplyDescription.apply(record.metricName()))
            .add("unit", supplyUnit.apply(record.metricName()))
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
}
