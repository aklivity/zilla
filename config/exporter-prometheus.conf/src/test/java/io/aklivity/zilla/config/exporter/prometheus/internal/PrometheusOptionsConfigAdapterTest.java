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
package io.aklivity.zilla.config.exporter.prometheus.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.exporter.prometheus.PrometheusEndpointConfig;
import io.aklivity.zilla.config.exporter.prometheus.PrometheusOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class PrometheusOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new PrometheusOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptions()
    {
        // GIVEN
        String yaml =
            """
            endpoints:
              - scheme: http
                port: 9090
                path: /metrics
            """;

        // WHEN
        PrometheusOptionsConfig options = jsonb.fromJson(yaml, PrometheusOptionsConfig.class);

        // THEN
        assertThat(options, not(nullValue()));
        assertThat(options.endpoints[0].scheme, equalTo("http"));
        assertThat(options.endpoints[0].port, equalTo(9090));
        assertThat(options.endpoints[0].path, equalTo("/metrics"));
    }

    @Test
    public void shouldApplyDefaultPath()
    {
        // GIVEN
        String yaml =
            """
            endpoints:
              - scheme: http
                port: 9090
            """;

        // WHEN
        PrometheusOptionsConfig options = jsonb.fromJson(yaml, PrometheusOptionsConfig.class);

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
        String yaml = jsonb.toJson(config);

        // THEN
        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
            """
            endpoints:
              - scheme: http
                port: 9090
                path: /metrics
            """));
    }
}
