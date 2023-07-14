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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

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
                "\"endpoint\":\n" +
                    "{\n" +
                        "\"location\": \"http://localhost:4317\",\n" +
                        "\"overrides\": \n" +
                            "{\n" +
                                "\"metrics\": \"/v1/metricsOverride\",\n" +
                                "\"logs\": \"/v1/logsOverride\"\n" +
                            "}\n" +
                    "}\n" +
            "}";

        // WHEN
        OtlpOptionsConfig options = jsonb.fromJson(text, OtlpOptionsConfig.class);

        // THEN
        assertThat(options, not(nullValue()));
        assertThat(options.interval, equalTo(30L));
        assertThat(options.endpoint.location, equalTo("http://localhost:4317"));
        assertThat(options.endpoint.overrides.metrics, equalTo("/v1/metricsOverride"));
        assertThat(options.endpoint.overrides.logs, equalTo("/v1/logsOverride"));
        assertThat(options.endpoint.overrides.traces, nullValue());
    }

    @Test
    public void shouldWriteOptions()
    {
        // GIVEN
        String expected =
            "{" +
                "\"interval\":30," +
                "\"endpoint\":" +
                    "{" +
                        "\"location\":\"http://localhost:4317\"" +
                    "}" +
            "}";
        OtlpOverridesConfig overrides = new OtlpOverridesConfig("/v1/metrics", "/v1/logs", null);
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http://localhost:4317", overrides);
        OtlpOptionsConfig config = new OtlpOptionsConfig(30, endpoint);

        // WHEN
        String json = jsonb.toJson(config);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expected));
    }
}
