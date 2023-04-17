/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryConfig;
import io.aklivity.zilla.runtime.engine.test.internal.exporter.config.TestExporterOptionsConfig;

public class TelemetryConfigsAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new TelemetryAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadTelemetry()
    {
        // GIVEN
        String text =
                "{" +
                    "\"attributes\":" +
                    "{" +
                        "\"test.attribute1\": \"example1\"," +
                        "\"test.attribute2\": \"example2\"" +
                    "}," +
                    "\"metrics\": " +
                        "[" +
                        "\"test.counter\"," +
                        "\"test.histogram\"" +
                        "]," +
                    "\"exporters\": " +
                    "{" +
                        "\"test0\": " +
                        "{" +
                            "\"type\": \"test\"," +
                        "}" +
                    "}" +
                "}";

        // WHEN
        TelemetryConfig telemetry = jsonb.fromJson(text, TelemetryConfig.class);

        // THEN
        assertThat(telemetry, not(nullValue()));
        assertThat(telemetry.attributes.get(0).name, equalTo("test.attribute1"));
        assertThat(telemetry.attributes.get(0).value, equalTo("example1"));
        assertThat(telemetry.attributes.get(1).name, equalTo("test.attribute2"));
        assertThat(telemetry.attributes.get(1).value, equalTo("example2"));
        assertThat(telemetry.metrics.get(0).name, equalTo("test.counter"));
        assertThat(telemetry.metrics.get(1).name, equalTo("test.histogram"));
        assertThat(telemetry.exporters.get(0).name, equalTo("test0"));
        assertThat(telemetry.exporters.get(0).type, equalTo("test"));
        assertThat(telemetry.exporters.get(0).options, nullValue());
    }

    @Test
    public void shouldWriteTelemetry()
    {
        // GIVEN
        TelemetryConfig telemetry = new TelemetryConfig(
                List.of(new AttributeConfig("test.attribute", "example")),
                List.of(new MetricConfig("test", "test.counter")),
                List.of(new ExporterConfig("test0", "test", null))
        );

        // WHEN
        String text = jsonb.toJson(telemetry);

        // THEN
        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                "{\"attributes\":{\"test.attribute\":\"example\"}," +
                "\"metrics\":[\"test.counter\"]," +
                "\"exporters\":{\"test0\":{\"type\":\"test\"}}}"));
    }

    @Test
    public void shouldReadTelemetryWithExporterOptions()
    {
        // GIVEN
        String text =
                "{" +
                    "\"attributes\":" +
                    "{" +
                        "\"test.attribute1\": \"example1\"," +
                        "\"test.attribute2\": \"example2\"" +
                    "}," +
                    "\"metrics\": " +
                        "[" +
                        "\"test.counter\"," +
                        "\"test.histogram\"" +
                        "]," +
                    "\"exporters\": " +
                    "{" +
                        "\"test0\": " +
                        "{" +
                            "\"type\": \"test\"," +
                            "\"options\": {" +
                                "\"mode\": \"test42\"" +
                            "}" +
                        "}" +
                    "}" +
                "}";

        // WHEN
        TelemetryConfig telemetry = jsonb.fromJson(text, TelemetryConfig.class);

        // THEN
        assertThat(telemetry, not(nullValue()));
        assertThat(telemetry.attributes.get(0).name, equalTo("test.attribute1"));
        assertThat(telemetry.attributes.get(0).value, equalTo("example1"));
        assertThat(telemetry.attributes.get(1).name, equalTo("test.attribute2"));
        assertThat(telemetry.attributes.get(1).value, equalTo("example2"));
        assertThat(telemetry.metrics.get(0).name, equalTo("test.counter"));
        assertThat(telemetry.metrics.get(1).name, equalTo("test.histogram"));
        assertThat(telemetry.exporters.get(0).name, equalTo("test0"));
        assertThat(telemetry.exporters.get(0).type, equalTo("test"));
        assertThat(telemetry.exporters.get(0).options, instanceOf(TestExporterOptionsConfig.class));
        assertThat(((TestExporterOptionsConfig)telemetry.exporters.get(0).options).mode, equalTo("test42"));
    }

    @Test
    public void shouldWriteTelemetryWithExporterOptions()
    {
        // GIVEN
        TelemetryConfig telemetry = new TelemetryConfig(
                List.of(new AttributeConfig("test.attribute", "example")),
                List.of(new MetricConfig("test", "test.counter")),
                List.of(new ExporterConfig("test0", "test", new TestExporterOptionsConfig("test42")))
        );

        // WHEN
        String text = jsonb.toJson(telemetry);

        // THEN
        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                "{\"attributes\":{\"test.attribute\":\"example\"}," +
                "\"metrics\":[\"test.counter\"]," +
                "\"exporters\":{\"test0\":{\"type\":\"test\",\"options\":{\"mode\":\"test42\"}}}}"));
    }
}
