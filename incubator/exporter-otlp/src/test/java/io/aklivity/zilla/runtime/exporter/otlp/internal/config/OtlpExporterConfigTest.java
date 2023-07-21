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

import static io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpOptionsConfig.ALL_SIGNALS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.URI;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.ExporterConfig;

public class OtlpExporterConfigTest
{
    @Test
    public void shouldCreateDefaultMetricsUrl()
    {
        // GIVEN
        OtlpOverridesConfig overrides = new OtlpOverridesConfig(null);
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http", URI.create("http://example.com"), overrides);
        OtlpOptionsConfig options = new OtlpOptionsConfig(30L, ALL_SIGNALS, endpoint);
        ExporterConfig exporter = new ExporterConfig("oltp0", "oltp", options);
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(exporter);

        // WHEN
        URI metrics = oltpExporter.resolveMetrics();

        // THEN
        assertThat(metrics, equalTo(URI.create("http://example.com/v1/metrics")));
    }

    @Test
    public void shouldOverrideAbsoluteMetricsUrl()
    {
        // GIVEN
        OtlpOverridesConfig overrides = new OtlpOverridesConfig(URI.create("http://overridden.com/metrics"));
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http", URI.create("http://example.com"), overrides);
        OtlpOptionsConfig options = new OtlpOptionsConfig(30L, ALL_SIGNALS, endpoint);
        ExporterConfig exporter = new ExporterConfig("oltp0", "oltp", options);
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(exporter);

        // WHEN
        URI metrics = oltpExporter.resolveMetrics();

        // THEN
        assertThat(metrics, equalTo(URI.create("http://overridden.com/metrics")));
    }

    @Test
    public void shouldOverrideRelativeMetricsUrl()
    {
        // GIVEN
        OtlpOverridesConfig overrides = new OtlpOverridesConfig(URI.create("/v42/metrix"));
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http", URI.create("http://example.com"), overrides);
        OtlpOptionsConfig options = new OtlpOptionsConfig(30L, ALL_SIGNALS, endpoint);
        ExporterConfig exporter = new ExporterConfig("oltp0", "oltp", options);
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(exporter);

        // WHEN
        URI metrics = oltpExporter.resolveMetrics();

        // THEN
        assertThat(metrics, equalTo(URI.create("http://example.com/v42/metrix")));
    }
}
