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

import java.util.function.BiFunction;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.CounterGaugeRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.HistogramRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessor;

public class OtlpMetricsSerializer
{
    private static final String SCOPE_NAME = "OtlpMetricsSerializer";
    private static final String SCOPE_VERSION = "1.0.0";

    private final MetricsProcessor metricsProcessor;
    private final Function<String, String> supplyKind;
    private final BiFunction<String, String, String> supplyName;
    private final Function<String, String> supplyDescription;
    private final Function<String, String> supplyUnit;

    public OtlpMetricsSerializer(
        MetricsProcessor metricsProcessor,
        Function<String, String> supplyKind,
        BiFunction<String, String, String> supplyName,
        Function<String, String> supplyDescription,
        Function<String, String> supplyUnit)
    {
        this.metricsProcessor = metricsProcessor;
        this.supplyKind = supplyKind;
        this.supplyName = supplyName;
        this.supplyDescription = supplyDescription;
        this.supplyUnit = supplyUnit;
    }

    public String serializeAll()
    {
        AttributeConfig attribute = new AttributeConfig("service.namespace", "example"); // TODO: Ati
        JsonArray attributes = Json.createArrayBuilder()
            .add(attributeToJson(attribute))
            .build();
        JsonArrayBuilder metricsArray = Json.createArrayBuilder();
        for (MetricRecord metric : metricsProcessor.getRecords())
        {
            JsonObject json = serialize(metric);
            metricsArray.add(json);
        }
        return createJson(attributes, metricsArray.build());
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
            .build();
        JsonArray dataPoints = Json.createArrayBuilder()
            .add(dataPoint)
            .build();
        String kind = supplyKind.apply(record.metricName());
        JsonObject metricData = Json.createObjectBuilder()
            .add("dataPoints", dataPoints)
            .build();
        return Json.createObjectBuilder()
            .add("name", supplyName.apply(record.metricName(), record.bindingName()))
            .add("description", supplyDescription.apply(record.metricName()))
            .add("unit", supplyUnit.apply(record.metricName()))
            .add(kind, metricData)
            .build();
    }

    private JsonObject serializeHistogram(
        HistogramRecord record)
    {
        return Json.createObjectBuilder()
            .add("name", supplyName.apply(record.metricName(), record.bindingName()))
            .add("description", supplyDescription.apply(record.metricName()))
            .add("unit", supplyUnit.apply(record.metricName()))
            .build();
    }

    private String createJson(
        JsonArray attributes,
        JsonArray metricsArray)
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
}
