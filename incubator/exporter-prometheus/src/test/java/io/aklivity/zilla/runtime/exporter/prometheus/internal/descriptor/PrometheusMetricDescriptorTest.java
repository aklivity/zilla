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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.descriptor;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class PrometheusMetricDescriptorTest
{
    @Test
    public void shouldDescribeCounter()
    {
        // GIVEN
        PrometheusMetricDescriptor descriptor =
            new PrometheusMetricDescriptor(mock(EngineConfiguration.class));

        // WHEN
        String kind = descriptor.kind("test.counter");
        String name = descriptor.name("test.counter");
        String description = descriptor.description("test.counter");

        // THEN
        assertThat(kind, equalTo("counter"));
        assertThat(name, equalTo("test_counter_total"));
        assertThat(description, equalTo("TODO description for test.counter")); // TODO: Ati
    }

    @Test
    public void shouldDescribeGauge()
    {
        // GIVEN
        PrometheusMetricDescriptor descriptor =
            new PrometheusMetricDescriptor(mock(EngineConfiguration.class));

        // WHEN
        String kind = descriptor.kind("test.gauge");
        String name = descriptor.name("test.gauge");
        String description = descriptor.description("test.gauge");

        // THEN
        assertThat(kind, equalTo("gauge"));
        assertThat(name, equalTo("test_gauge"));
        assertThat(description, equalTo("TODO description for test.gauge")); // TODO: Ati
    }

    @Test
    public void shouldDescribeHistogram()
    {
        // GIVEN
        PrometheusMetricDescriptor descriptor =
            new PrometheusMetricDescriptor(mock(EngineConfiguration.class));

        // WHEN
        String kind = descriptor.kind("test.histogram");
        String name = descriptor.name("test.histogram");
        String description = descriptor.description("test.histogram");

        // THEN
        assertThat(kind, equalTo("histogram"));
        assertThat(name, equalTo("test_histogram_bytes"));
        assertThat(description, equalTo("TODO description for test.histogram")); // TODO: Ati
    }
}
