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

import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.OltpExporterHandler;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.CounterGaugeRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.HistogramRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessor;

public class OtlpMetricsSerializer
{
    private static final String CLASS_NAME = OltpExporterHandler.class.getName();
    private static final String SCOPE_VERSION = "1.0.0";

    private final MetricsProcessor metricsProcessor;
    private final Function<String, String> supplyKind;
    private final Function<String, String> supplyName;
    private final Function<String, String> supplyDescription;

    public OtlpMetricsSerializer(
        MetricsProcessor metricsProcessor,
        Function<String, String> supplyKind,
        Function<String, String> supplyName,
        Function<String, String> supplyDescription)
    {
        this.metricsProcessor = metricsProcessor;
        this.supplyKind = supplyKind;
        this.supplyName = supplyName;
        this.supplyDescription = supplyDescription;
    }

    public String serializeAll()
    {
        for (MetricRecord metric : metricsProcessor.getRecords())
        {
            String metric1 = serialize(metric);
        }
        AttributeConfig attribute = new AttributeConfig("service.namespace", "example");
        JsonArray attributes = Json.createArrayBuilder()
            .add(attributeToJson(attribute))
            .build();
        JsonArray metrics = Json.createArrayBuilder()
            .build();
        return createJson(attributes, metrics);
    }

    private String serialize(
        MetricRecord metric)
    {
        String result = null;
        if (metric.getClass().equals(CounterGaugeRecord.class))
        {
            //result = formatCounterGauge((CounterGaugeRecord) metric);
        }
        else if (metric.getClass().equals(HistogramRecord.class))
        {
            HistogramRecord record = (HistogramRecord) metric;
            //result = formatHistogram(record);
        }
        return result;
    }

    private String createJson(
        JsonArray attributes,
        JsonArray metricsArray)
    {
        JsonObject resource = Json.createObjectBuilder()
            .add("attributes", attributes)
            .build();
        JsonObject resourceMetrics = Json.createObjectBuilder()
            .add("resource", resource)
            .build();
        JsonArray resourceMetricsArray = Json.createArrayBuilder()
            .add(resourceMetrics)
            .build();
        JsonObject scope = Json.createObjectBuilder()
            .add("name", CLASS_NAME)
            .add("version", SCOPE_VERSION)
            .build();
        JsonObject scopeMetrics = Json.createObjectBuilder()
            .add("scope", scope)
            .add("metrics", metricsArray)
            .build();
        JsonArray scopeMetricsArray = Json.createArrayBuilder()
            .add(scopeMetrics)
            .build();
        JsonObject jsonObject = Json.createObjectBuilder()
            .add("resourceMetrics", resourceMetricsArray)
            .add("scopeMetrics", scopeMetricsArray)
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
