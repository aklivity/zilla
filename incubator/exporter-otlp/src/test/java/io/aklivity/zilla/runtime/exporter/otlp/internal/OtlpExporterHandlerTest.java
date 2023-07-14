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
package io.aklivity.zilla.runtime.exporter.otlp.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.function.LongFunction;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpOptionsConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpOverridesConfig;

public class OtlpExporterHandlerTest
{
    @Test
    public void shouldCreateDefaultMetricsUrl()
    {
        // GIVEN
        EngineConfiguration config = mock(EngineConfiguration.class);
        EngineContext context = mock(EngineContext.class);
        OtlpOverridesConfig overrides = new OtlpOverridesConfig(null, null, null);
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http://example.com", overrides);
        OptionsConfig options = new OtlpOptionsConfig(30L, endpoint);
        ExporterConfig exporterConfig = new ExporterConfig("otlp0", "otlp", options);
        OtlpExporterConfig exporter = new OtlpExporterConfig(exporterConfig);
        Collector collector = mock(Collector.class);
        LongFunction<KindConfig> resolveKind = mock(LongFunction.class);
        List<AttributeConfig> attributes = List.of();

        // WHEN
        OltpExporterHandler handler = new OltpExporterHandler(config, context, exporter, collector, resolveKind, attributes);

        // THEN
        assertThat(handler.metricsUrl().toString(), equalTo("http://example.com/v1/metrics"));
    }

    @Test
    public void shouldOverrideAbsoluteMetricsUrl()
    {
        // GIVEN
        EngineConfiguration config = mock(EngineConfiguration.class);
        EngineContext context = mock(EngineContext.class);
        OtlpOverridesConfig overrides = new OtlpOverridesConfig("http://overridden.com/metrics", null, null);
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http://example.com", overrides);
        OptionsConfig options = new OtlpOptionsConfig(30L, endpoint);
        ExporterConfig exporterConfig = new ExporterConfig("otlp0", "otlp", options);
        OtlpExporterConfig exporter = new OtlpExporterConfig(exporterConfig);
        Collector collector = mock(Collector.class);
        LongFunction<KindConfig> resolveKind = mock(LongFunction.class);
        List<AttributeConfig> attributes = List.of();

        // WHEN
        OltpExporterHandler handler = new OltpExporterHandler(config, context, exporter, collector, resolveKind, attributes);

        // THEN
        assertThat(handler.metricsUrl().toString(), equalTo("http://overridden.com/metrics"));
    }

    @Test
    public void shouldOverrideRelativeMetricsUrl()
    {
        // GIVEN
        EngineConfiguration config = mock(EngineConfiguration.class);
        EngineContext context = mock(EngineContext.class);
        OtlpOverridesConfig overrides = new OtlpOverridesConfig("/v42/metrix", null, null);
        OtlpEndpointConfig endpoint = new OtlpEndpointConfig("http://example.com", overrides);
        OptionsConfig options = new OtlpOptionsConfig(30L, endpoint);
        ExporterConfig exporterConfig = new ExporterConfig("otlp0", "otlp", options);
        OtlpExporterConfig exporter = new OtlpExporterConfig(exporterConfig);
        Collector collector = mock(Collector.class);
        LongFunction<KindConfig> resolveKind = mock(LongFunction.class);
        List<AttributeConfig> attributes = List.of();

        // WHEN
        OltpExporterHandler handler = new OltpExporterHandler(config, context, exporter, collector, resolveKind, attributes);

        // THEN
        assertThat(handler.metricsUrl().toString(), equalTo("http://example.com/v42/metrix"));
    }
}
