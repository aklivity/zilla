/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class PrometheusOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new PrometheusOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        // GIVEN
        String text =
            "{\n" +
                "\"endpoints\":\n" +
                "[\n" +
                    "{\n" +
                        "\"scheme\": \"https\",\n" +
                        "\"port\": 9999,\n" +
                        "\"path\": \"/metrix\"\n" +
                    "}\n" +
                "]\n" +
            "}";


        // WHEN
        PrometheusOptionsConfig options = jsonb.fromJson(text, PrometheusOptionsConfig.class);

        // THEN
        assertThat(options, not(nullValue()));
        assertThat(options.endpoints[0].scheme, equalTo("https"));
        assertThat(options.endpoints[0].port, equalTo(9999));
        assertThat(options.endpoints[0].path, equalTo("/metrix"));
    }

    @Test
    public void shouldApplyDefaultValues()
    {
        // GIVEN
        String text =
            "{\n" +
                "\"endpoints\":\n" +
                "[\n" +
                    "{\n" +
                    "}\n" +
                "]\n" +
            "}";


        // WHEN
        PrometheusOptionsConfig options = jsonb.fromJson(text, PrometheusOptionsConfig.class);

        // THEN
        assertThat(options, not(nullValue()));
        assertThat(options.endpoints[0].scheme, equalTo("http"));
        assertThat(options.endpoints[0].port, equalTo(9090));
        assertThat(options.endpoints[0].path, equalTo("/metrics"));
    }

    @Test
    public void shouldApplyDefaultEndpoint()
    {
        // GIVEN
        String text = "{}";

        // WHEN
        PrometheusOptionsConfig options = jsonb.fromJson(text, PrometheusOptionsConfig.class);

        // THEN
        assertThat(options, not(nullValue()));
        assertThat(options.endpoints[0].scheme, equalTo("http"));
        assertThat(options.endpoints[0].port, equalTo(9090));
        assertThat(options.endpoints[0].path, equalTo("/metrics"));
    }

    @Test
    public void shouldWriteOptions()
    {
        // GIVEN
        PrometheusEndpointConfig endpoint = new PrometheusEndpointConfig("http", 9090, "/metrics");
        PrometheusOptionsConfig config = new PrometheusOptionsConfig(new PrometheusEndpointConfig[]{endpoint});

        // WHEN
        String text = jsonb.toJson(config);

        // THEN
        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"endpoints\":[{\"scheme\":\"http\",\"port\":9090,\"path\":\"/metrics\"}]}"));
    }
}
