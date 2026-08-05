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
package io.aklivity.zilla.config.exporter.otlp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;

import org.junit.Test;

public class OtlpExporterConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        OtlpExporterConfig exporter = OtlpExporterConfig.builder()
            .namespace("test")
            .name("otlp0")
            .vault("vault0")
            .options()
                .interval(Duration.ofSeconds(15))
                .authorization("Bearer token")
                .build()
            .build();

        assertThat(exporter.namespace, equalTo("test"));
        assertThat(exporter.name, equalTo("otlp0"));
        assertThat(exporter.type, equalTo("otlp"));
        assertThat(exporter.vault, equalTo("vault0"));
        assertThat(((OtlpOptionsConfig) exporter.options).interval, equalTo(Duration.ofSeconds(15)));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        OtlpExporterConfig exporter = OtlpExporterConfig.builder(OtlpExporterConfig.class::cast)
            .namespace("test")
            .name("otlp1")
            .build();

        assertThat(exporter.name, equalTo("otlp1"));
    }
}
