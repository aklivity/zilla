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
package io.aklivity.zilla.config.exporter.prometheus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class PrometheusExporterConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        PrometheusEndpointConfig endpoint = PrometheusEndpointConfig.builder()
            .scheme("http")
            .port(9090)
            .path("/metrics")
            .build();

        PrometheusExporterConfig exporter = PrometheusExporterConfig.builder()
            .namespace("test")
            .name("prometheus0")
            .vault("vault0")
            .options()
                .endpoints(new PrometheusEndpointConfig[] { endpoint })
                .build()
            .build();

        assertThat(exporter.namespace, equalTo("test"));
        assertThat(exporter.name, equalTo("prometheus0"));
        assertThat(exporter.type, equalTo("prometheus"));
        assertThat(exporter.vault, equalTo("vault0"));
        assertThat(((PrometheusOptionsConfig) exporter.options).endpoints[0].port, equalTo(9090));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        PrometheusExporterConfig exporter = PrometheusExporterConfig.builder(PrometheusExporterConfig.class::cast)
            .namespace("test")
            .name("prometheus1")
            .build();

        assertThat(exporter.name, equalTo("prometheus1"));
    }
}
