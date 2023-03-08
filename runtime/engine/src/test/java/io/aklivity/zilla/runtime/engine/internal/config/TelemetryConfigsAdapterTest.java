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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryConfig;

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
        String text =
                "{" +
                    "\"attributes\":" +
                    "{" +
                        "\"test.attribute\": \"example\"" +
                    "}," +
                    "\"metrics\": " +
                        "[" +
                        "\"test.counter\"" +
                        "]" +
                "}";

        TelemetryConfig telemetry = jsonb.fromJson(text, TelemetryConfig.class);

        assertThat(telemetry, not(nullValue()));
        assertThat(telemetry.attributes.get(0).name, equalTo("test.attribute"));
        assertThat(telemetry.attributes.get(0).value, equalTo("example"));
        assertThat(telemetry.metrics.get(0).name, equalTo("test.counter"));
    }

    @Test
    @Ignore // TODO: Ati
    public void shouldWriteTelemetry()
    {
        TelemetryConfig telemetry = new TelemetryConfig(
                List.of(new AttributeConfig("test.attribute", "example")),
                List.of(new MetricConfig("test", "test.counter"))
        );

        String text = jsonb.toJson(telemetry);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"attributes\":{\"test.attribute\":\"example\"},\"metrics\":[\"test.counter\"]}"));
    }
}
