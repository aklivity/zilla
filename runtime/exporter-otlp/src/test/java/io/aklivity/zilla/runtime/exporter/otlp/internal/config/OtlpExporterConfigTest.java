/*
 * Copyright 2021-2024 Aklivity Inc
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

import static io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOptionsConfig.OtlpSignalsConfig.METRICS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.URI;
import java.util.Set;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOptionsConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOverridesConfig;

public class OtlpExporterConfigTest
{
    @Test
    public void shouldCreateDefaultMetricsUrl()
    {
        // GIVEN
        OtlpOverridesConfig overrides = new OtlpOverridesConfig(null, null);
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http", URI.create("http://example.com"), overrides);
        OtlpOptionsConfig options = new OtlpOptionsConfig(30L, Set.of(METRICS), endpoint);
        ExporterConfig exporter = ExporterConfig.builder()
                .namespace("test")
                .name("oltp0")
                .type("oltp")
                .options(options)
                .build();
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(exporter);

        // WHEN
        URI metrics = oltpExporter.resolveMetrics();
        URI logs = oltpExporter.resolveLogs();

        // THEN
        assertThat(metrics, equalTo(URI.create("http://example.com/v1/metrics")));
        assertThat(logs, equalTo(URI.create("http://example.com/v1/logs")));
    }

    @Test
    public void shouldOverrideAbsoluteMetricsUrl()
    {
        // GIVEN
        OtlpOverridesConfig overrides = new OtlpOverridesConfig(URI.create("http://overridden.com/metrics"),
            URI.create("http://overridden.com/logs"));
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http", URI.create("http://example.com"), overrides);
        OtlpOptionsConfig options = new OtlpOptionsConfig(30L, Set.of(METRICS), endpoint);
        ExporterConfig exporter = ExporterConfig.builder()
                .namespace("test")
                .name("oltp0")
                .type("oltp")
                .options(options)
                .build();
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(exporter);

        // WHEN
        URI metrics = oltpExporter.resolveMetrics();
        URI logs = oltpExporter.resolveLogs();

        // THEN
        assertThat(metrics, equalTo(URI.create("http://overridden.com/metrics")));
        assertThat(logs, equalTo(URI.create("http://overridden.com/logs")));
    }

    @Test
    public void shouldOverrideRelativeMetricsUrl()
    {
        // GIVEN
        OtlpOverridesConfig overrides = new OtlpOverridesConfig(URI.create("/v42/metrix"), URI.create("/v42/logz"));
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http", URI.create("http://example.com"), overrides);
        OtlpOptionsConfig options = new OtlpOptionsConfig(30L, Set.of(METRICS), endpoint);
        ExporterConfig exporter = ExporterConfig.builder()
                .namespace("test")
                .name("oltp0")
                .type("oltp")
                .options(options)
                .build();
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(exporter);

        // WHEN
        URI metrics = oltpExporter.resolveMetrics();
        URI logs = oltpExporter.resolveLogs();

        // THEN
        assertThat(metrics, equalTo(URI.create("http://example.com/v42/metrix")));
        assertThat(logs, equalTo(URI.create("http://example.com/v42/logz")));
    }
}
