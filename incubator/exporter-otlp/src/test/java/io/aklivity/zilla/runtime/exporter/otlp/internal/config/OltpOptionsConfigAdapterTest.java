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
package io.aklivity.zilla.runtime.exporter.otlp.internal.config;

import static io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpSignalsConfig.Signals.METRICS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;
import java.util.Set;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class OltpOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new OtlpOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        // GIVEN
        String text =
            "{\n" +
                "\"interval\": 30,\n" +
                "\"signals\":\n" +
                    "[\n" +
                        "metrics\n" +
                    "],\n" +
                "\"endpoint\":\n" +
                    "{\n" +
                        "\"location\": \"http://localhost:4317\",\n" +
                        "\"overrides\": \n" +
                            "{\n" +
                                "\"metrics\": \"/v1/metricsOverride\"\n" +
                            "}\n" +
                    "}\n" +
            "}";

        // WHEN
        OtlpOptionsConfig options = jsonb.fromJson(text, OtlpOptionsConfig.class);

        // THEN
        assertThat(options, not(nullValue()));
        assertThat(options.interval, equalTo(30L));
        assertThat(options.signals.signals, containsInAnyOrder(METRICS));
        assertThat(options.endpoint.location, equalTo(URI.create("http://localhost:4317")));
        assertThat(options.endpoint.overrides.metrics, equalTo(URI.create("/v1/metricsOverride")));
    }

    @Test
    public void shouldWriteOptions()
    {
        // GIVEN
        String expected =
            "{" +
                "\"interval\":30," +
                "\"signals\":" +
                    "[" +
                        "\"metrics\"" +
                    "]," +
                "\"endpoint\":" +
                    "{" +
                        "\"protocol\":\"http\"," +
                        "\"location\":\"http://localhost:4317\"," +
                        "\"overrides\":" +
                            "{" +
                                "\"metrics\":\"/v1/metrics\"" +
                            "}" +
                    "}" +
            "}";
        OtlpOverridesConfig overrides = new OtlpOverridesConfig(URI.create("/v1/metrics"));
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http", URI.create("http://localhost:4317"), overrides);
        OtlpSignalsConfig signals = new OtlpSignalsConfig(Set.of(METRICS));
        OtlpOptionsConfig config = new OtlpOptionsConfig(30, signals, endpoint);

        // WHEN
        String json = jsonb.toJson(config);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expected));
    }
}
