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
package io.aklivity.zilla.config.catalog.apicurio;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class ApicurioCatalogConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        ApicurioCatalogConfig catalog = ApicurioCatalogConfig.builder()
            .namespace("test")
            .name("apicurio0")
            .vault("vault0")
            .options()
                .url("http://localhost:8080")
                .build()
            .build();

        assertThat(catalog.namespace, equalTo("test"));
        assertThat(catalog.name, equalTo("apicurio0"));
        assertThat(catalog.type, equalTo("apicurio"));
        assertThat(catalog.vault, equalTo("vault0"));
        assertThat(((ApicurioOptionsConfig) catalog.options).url, equalTo("http://localhost:8080"));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        ApicurioCatalogConfig catalog = ApicurioCatalogConfig.builder(ApicurioCatalogConfig.class::cast)
            .namespace("test")
            .name("apicurio1")
            .build();

        assertThat(catalog.name, equalTo("apicurio1"));
    }
}
