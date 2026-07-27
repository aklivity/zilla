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
package io.aklivity.zilla.config.exporter.stdout;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.Test;

public class StdoutExporterConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        StdoutExporterConfig exporter = StdoutExporterConfig.builder()
            .namespace("test")
            .name("stdout0")
            .vault("vault0")
            .options()
                .build()
            .build();

        assertThat(exporter.namespace, equalTo("test"));
        assertThat(exporter.name, equalTo("stdout0"));
        assertThat(exporter.type, equalTo("stdout"));
        assertThat(exporter.vault, equalTo("vault0"));
        assertThat((StdoutOptionsConfig) exporter.options, notNullValue());
    }

    @Test
    public void shouldBuildViaMapper()
    {
        StdoutExporterConfig exporter = StdoutExporterConfig.builder(StdoutExporterConfig.class::cast)
            .namespace("test")
            .name("stdout1")
            .build();

        assertThat(exporter.name, equalTo("stdout1"));
    }
}
