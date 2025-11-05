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
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOptionsConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.OltpConfiguration;

public class OtlpExporterConfigTest
{
    private EngineContext context;
    private OltpConfiguration config;

    @Before
    public void init()
    {
        config = new OltpConfiguration(new Configuration());
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldCreateDefaultMetricsUrl()
    {
        // GIVEN
        OtlpOptionsConfig options = OtlpOptionsConfig.builder()
            .interval(Duration.ofSeconds(30L))
            .signals(Set.of(METRICS))
            .endpoint()
                .protocol("http")
                .location(URI.create("http://example.com"))
                .build()
            .build();
        ExporterConfig exporter = ExporterConfig.builder()
                .namespace("test")
                .name("oltp0")
                .type("oltp")
                .options(options)
                .build();
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(config, context, exporter);

        // WHEN
        URI metrics = oltpExporter.metrics;
        URI logs = oltpExporter.logs;

        // THEN
        assertThat(metrics, equalTo(URI.create("http://example.com/v1/metrics")));
        assertThat(logs, equalTo(URI.create("http://example.com/v1/logs")));
    }

    @Test
    public void shouldOverrideAbsoluteMetricsUrl()
    {
        // GIVEN
        OtlpOptionsConfig options = OtlpOptionsConfig.builder()
            .interval(Duration.ofSeconds(30L))
            .signals(Set.of(METRICS))
            .endpoint()
                .protocol("http")
                .location(URI.create("http://example.com"))
                .overrides()
                    .metrics(URI.create("http://overridden.com/metrics"))
                    .logs(URI.create("http://overridden.com/logs"))
                    .build()
                .build()
            .build();
        ExporterConfig exporter = ExporterConfig.builder()
                .namespace("test")
                .name("oltp0")
                .type("oltp")
                .options(options)
                .build();
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(config, context, exporter);

        // WHEN
        URI metrics = oltpExporter.metrics;
        URI logs = oltpExporter.logs;

        // THEN
        assertThat(metrics, equalTo(URI.create("http://overridden.com/metrics")));
        assertThat(logs, equalTo(URI.create("http://overridden.com/logs")));
    }

    @Test
    public void shouldOverrideRelativeMetricsUrl()
    {
        // GIVEN
        OtlpOptionsConfig options = OtlpOptionsConfig.builder()
            .interval(Duration.ofSeconds(30L))
            .signals(Set.of(METRICS))
            .endpoint()
                .protocol("http")
                .location(URI.create("http://example.com"))
                .overrides()
                    .metrics(URI.create("/v42/metrix"))
                    .logs(URI.create("/v42/logz"))
                    .build()
                .build()
            .build();
        ExporterConfig exporter = ExporterConfig.builder()
                .namespace("test")
                .name("oltp0")
                .type("oltp")
                .options(options)
                .build();
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(config, context, exporter);

        // WHEN
        URI metrics = oltpExporter.metrics;
        URI logs = oltpExporter.logs;

        // THEN
        assertThat(metrics, equalTo(URI.create("http://example.com/v42/metrix")));
        assertThat(logs, equalTo(URI.create("http://example.com/v42/logz")));
    }

    @Test
    public void shouldAppendPathsWithNonRootBase()
    {
        // GIVEN
        OtlpOptionsConfig options = OtlpOptionsConfig.builder()
            .interval(Duration.ofSeconds(30L))
            .signals(Set.of(METRICS))
            .endpoint()
                .protocol("http")
                .location(URI.create("http://localhost:8080/telemetry"))
                .overrides()
                    .metrics("/v1/metrics")
                    .logs("/v1/logs")
                    .build()
                .build()
            .build();
        ExporterConfig exporter = ExporterConfig.builder()
                .namespace("test")
                .name("oltp0")
                .type("oltp")
                .options(options)
                .build();
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(config, context, exporter);

        // WHEN
        URI metrics = oltpExporter.metrics;
        URI logs = oltpExporter.logs;

        // THEN
        assertThat(metrics, equalTo(URI.create("http://localhost:8080/telemetry/v1/metrics")));
        assertThat(logs, equalTo(URI.create("http://localhost:8080/telemetry/v1/logs")));
    }

    @Test
    public void shouldHandlePathsWithoutLeadingSlash()
    {
        // GIVEN
        OtlpOptionsConfig options = OtlpOptionsConfig.builder()
            .interval(Duration.ofSeconds(30L))
            .signals(Set.of(METRICS))
            .endpoint()
                .protocol("http")
                .location(URI.create("http://localhost:8080/telemetry"))
                .overrides()
                    .metrics("v1/metrics")
                    .logs("v1/logs")
                    .build()
                .build()
            .build();
        ExporterConfig exporter = ExporterConfig.builder()
                .namespace("test")
                .name("oltp0")
                .type("oltp")
                .options(options)
                .build();
        OtlpExporterConfig oltpExporter = new OtlpExporterConfig(config, context, exporter);

        // WHEN
        URI metrics = oltpExporter.metrics;
        URI logs = oltpExporter.logs;

        // THEN
        assertThat(metrics, equalTo(URI.create("http://localhost:8080/telemetry/v1/metrics")));
        assertThat(logs, equalTo(URI.create("http://localhost:8080/telemetry/v1/logs")));
    }
}
