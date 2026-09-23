/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.engine.test.internal.exporter.config;

import static java.util.function.Function.identity;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class TestExporterOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String MODE_NAME = "mode";
    private static final String EVENTS_NAME = "events";
    private static final String QNAME_NAME = "qname";
    private static final String ID_NAME = "id";
    private static final String NAME_NAME = "name";
    private static final String MESSAGE_NAME = "message";
    private static final String METRICS_NAME = "metrics";
    private static final String BINDING_NAME = "binding";
    private static final String KIND_NAME = "kind";
    private static final String ATTRIBUTES_NAME = "attributes";
    private static final String VALUE_NAME = "value";
    private static final String COUNT_NAME = "count";
    private static final String BUCKETS_NAME = "buckets";
    private static final String LIMIT_NAME = "limit";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        TestExporterOptionsConfig testOptions = (TestExporterOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (testOptions.mode != null)
        {
            object.add(MODE_NAME, testOptions.mode);
        }

        if (testOptions.events != null)
        {
            JsonArrayBuilder events = Json.createArrayBuilder();
            for (TestExporterOptionsConfig.Event e : testOptions.events)
            {
                JsonObjectBuilder event = Json.createObjectBuilder();
                event.add(QNAME_NAME, e.qName);
                event.add(ID_NAME, e.id);
                event.add(NAME_NAME, e.name);
                event.add(MESSAGE_NAME, e.message);
                events.add(event);
            }
            object.add(EVENTS_NAME, events);
        }

        if (testOptions.metrics != null)
        {
            JsonArrayBuilder metrics = Json.createArrayBuilder();
            testOptions.metrics.forEach(m -> metrics.add(adaptMetricToJson(m)));
            object.add(METRICS_NAME, metrics);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        TestExporterOptionsConfigBuilder<TestExporterOptionsConfig> testOptions = TestExporterOptionsConfig.builder()
                .inject(identity());

        if (object != null)
        {
            if (object.containsKey(MODE_NAME))
            {
                testOptions.mode(object.getString(MODE_NAME));
            }
            if (object.containsKey(EVENTS_NAME))
            {
                JsonArray events = object.getJsonArray(EVENTS_NAME);
                for (JsonValue e : events)
                {
                    JsonObject e0 = e.asJsonObject();
                    testOptions.event(
                        e0.getString(QNAME_NAME),
                        e0.getString(ID_NAME),
                        e0.getString(NAME_NAME),
                        e0.getString(MESSAGE_NAME));
                }
            }
            if (object.containsKey(METRICS_NAME))
            {
                object.getJsonArray(METRICS_NAME).stream()
                    .map(JsonValue::asJsonObject)
                    .map(this::adaptMetricFromJson)
                    .forEach(testOptions::metric);
            }
        }

        return testOptions.build();
    }

    private JsonObject adaptMetricToJson(
        TestExporterOptionsConfig.Metric metric)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        object.add(NAME_NAME, metric.name);
        object.add(BINDING_NAME, metric.binding);
        object.add(KIND_NAME, metric.kind);

        if (metric.attributes != null)
        {
            JsonObjectBuilder attributes = Json.createObjectBuilder();
            metric.attributes.forEach(attributes::add);
            object.add(ATTRIBUTES_NAME, attributes);
        }

        if (metric.value != null)
        {
            object.add(VALUE_NAME, metric.value);
        }

        if (metric.count != null)
        {
            object.add(COUNT_NAME, metric.count);
        }

        if (metric.buckets != null)
        {
            JsonArrayBuilder buckets = Json.createArrayBuilder();
            metric.buckets.forEach(b -> buckets.add(Json.createObjectBuilder()
                .add(LIMIT_NAME, b.limit)
                .add(COUNT_NAME, b.count)));
            object.add(BUCKETS_NAME, buckets);
        }

        return object.build();
    }

    private TestExporterOptionsConfig.Metric adaptMetricFromJson(
        JsonObject object)
    {
        Map<String, String> attributes = null;
        if (object.containsKey(ATTRIBUTES_NAME))
        {
            attributes = new LinkedHashMap<>();
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(ATTRIBUTES_NAME).entrySet())
            {
                attributes.put(entry.getKey(), ((JsonString) entry.getValue()).getString());
            }
        }

        List<TestExporterOptionsConfig.Bucket> buckets = null;
        if (object.containsKey(BUCKETS_NAME))
        {
            buckets = new LinkedList<>();
            for (JsonValue value : object.getJsonArray(BUCKETS_NAME))
            {
                JsonObject bucket = value.asJsonObject();
                buckets.add(new TestExporterOptionsConfig.Bucket(
                    bucket.getJsonNumber(LIMIT_NAME).longValue(),
                    bucket.getJsonNumber(COUNT_NAME).longValue()));
            }
        }

        return new TestExporterOptionsConfig.Metric(
            object.getString(NAME_NAME),
            object.getString(BINDING_NAME),
            object.getString(KIND_NAME),
            attributes,
            object.containsKey(VALUE_NAME) ? object.getJsonNumber(VALUE_NAME).longValue() : null,
            object.containsKey(COUNT_NAME) ? object.getJsonNumber(COUNT_NAME).longValue() : null,
            buckets);
    }
}
