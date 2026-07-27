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
package io.aklivity.zilla.config.engine.internal;

import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.TelemetryConfig;
import io.aklivity.zilla.config.engine.test.internal.exporter.config.TestExporterOptionsConfig;

public class TelemetryConfigAdapterTest
{
    private TelemetryConfigAdapter adapter;

    @Before
    public void initJson()
    {
        EngineInfo info = new EngineInfo();
        adapter = new TelemetryConfigAdapter(info);
    }

    @Test
    public void shouldReadTelemetry() throws Exception
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
                            "\"type\": \"test\"" +
                        "}" +
                    "}" +
                "}";
        JsonObject object = Json.createReader(new StringReader(text)).readObject();

        // WHEN
        TelemetryConfig telemetry = adapter.adaptFromJson("test", object);

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
        assertThat(((TestExporterOptionsConfig)telemetry.exporters.get(0).options).mode, nullValue());
    }

    @Test
    public void shouldWriteTelemetry() throws Exception
    {
        // GIVEN
        TelemetryConfig telemetry = TelemetryConfig.builder()
                .inject(identity())
                .attribute()
                    .inject(identity())
                    .name("test.attribute")
                    .value("example")
                    .build()
                .metric()
                    .inject(identity())
                    .group("test")
                    .name("test.counter")
                    .build()
                .exporter()
                    .inject(identity())
                    .name("test0")
                    .namespace("test")
                    .type("test")
                    .build()
                .build();

        // WHEN
        JsonObject object = adapter.adaptToJson(telemetry);

        // THEN
        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo(
                "{\"attributes\":{\"test.attribute\":\"example\"}," +
                "\"metrics\":[\"test.counter\"]," +
                "\"exporters\":{\"test0\":{\"type\":\"test\"}}}"));
    }

    @Test
    public void shouldReadTelemetryWithExporterOptions() throws Exception
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
                            "\"vault\": \"vault0\"," +
                            "\"options\": {" +
                                "\"mode\": \"test42\"" +
                            "}" +
                        "}" +
                    "}" +
                "}";
        JsonObject object = Json.createReader(new StringReader(text)).readObject();

        // WHEN
        TelemetryConfig telemetry = adapter.adaptFromJson("test", object);

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
        assertThat(telemetry.exporters.get(0).vault, equalTo("vault0"));
        assertThat(telemetry.exporters.get(0).options, instanceOf(TestExporterOptionsConfig.class));
        assertThat(((TestExporterOptionsConfig)telemetry.exporters.get(0).options).mode, equalTo("test42"));
    }

    @Test
    public void shouldWriteTelemetryWithExporterOptions() throws Exception
    {
        // GIVEN
        TelemetryConfig telemetry = TelemetryConfig.builder()
                .inject(identity())
                .attribute()
                    .inject(identity())
                    .name("test.attribute")
                    .value("example")
                    .build()
                .metric()
                    .inject(identity())
                    .group("test")
                    .name("test.counter")
                    .build()
                .exporter()
                    .inject(identity())
                    .namespace("test")
                    .name("test0")
                    .type("test")
                    .vault("vault0")
                    .options(TestExporterOptionsConfig::builder)
                        .inject(identity())
                        .mode("test42")
                        .build()
                    .build()
                .build();

        // WHEN
        JsonObject object = adapter.adaptToJson(telemetry);

        // THEN
        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo(
                "{\"attributes\":{\"test.attribute\":\"example\"}," +
                "\"metrics\":[\"test.counter\"]," +
                "\"exporters\":{\"test0\":{\"type\":\"test\",\"vault\":\"vault0\",\"options\":{\"mode\":\"test42\"}}}}"));
    }
}
