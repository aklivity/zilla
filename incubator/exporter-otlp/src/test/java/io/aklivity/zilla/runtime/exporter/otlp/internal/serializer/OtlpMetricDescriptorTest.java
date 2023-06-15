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
package io.aklivity.zilla.runtime.exporter.otlp.internal.serializer;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Function;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.metrics.Metric;

public class OtlpMetricDescriptorTest
{
    @Test
    public void shouldDescribeServerMetric()
    {
        // GIVEN
        Metric metric = mock(Metric.class);
        when(metric.name()).thenReturn("http.request.size");
        when(metric.kind()).thenReturn(Metric.Kind.HISTOGRAM);
        when(metric.unit()).thenReturn(Metric.Unit.BYTES);
        when(metric.description()).thenReturn("description");
        Function<String, Metric> metricResolver = mock(Function.class);
        when(metricResolver.apply("http.request.size")).thenReturn(metric);
        Function<String, KindConfig> findBindingKind = mock(Function.class);
        when(findBindingKind.apply("binding_server")).thenReturn(KindConfig.SERVER);
        OtlpMetricsDescriptor descriptor = new OtlpMetricsDescriptor(metricResolver, findBindingKind);

        // WHEN
        String kind = descriptor.kind("http.request.size");
        String name = descriptor.nameByBinding("http.request.size", "binding_server");
        String description = descriptor.description("http.request.size");

        // THEN
        assertThat(kind, equalTo("histogram"));
        assertThat(name, equalTo("http.server.request.size"));
        assertThat(description, equalTo("description"));
    }

    @Test
    public void shouldDescribeClientMetric()
    {
        // GIVEN
        Metric metric = mock(Metric.class);
        when(metric.name()).thenReturn("http.request.size");
        when(metric.kind()).thenReturn(Metric.Kind.HISTOGRAM);
        when(metric.unit()).thenReturn(Metric.Unit.BYTES);
        when(metric.description()).thenReturn("description");
        Function<String, Metric> metricResolver = mock(Function.class);
        when(metricResolver.apply("http.request.size")).thenReturn(metric);
        Function<String, KindConfig> findBindingKind = mock(Function.class);
        when(findBindingKind.apply("binding_client")).thenReturn(KindConfig.CLIENT);
        OtlpMetricsDescriptor descriptor = new OtlpMetricsDescriptor(metricResolver, findBindingKind);

        // WHEN
        String kind = descriptor.kind("http.request.size");
        String name = descriptor.nameByBinding("http.request.size", "binding_client");
        String description = descriptor.description("http.request.size");

        // THEN
        assertThat(kind, equalTo("histogram"));
        assertThat(name, equalTo("http.client.request.size"));
        assertThat(description, equalTo("description"));
    }
}
