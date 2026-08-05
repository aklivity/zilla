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
package io.aklivity.zilla.config.catalog.karapace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class KarapaceCatalogConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        KarapaceCatalogConfig catalog = KarapaceCatalogConfig.builder()
            .namespace("test")
            .name("karapace0")
            .vault("vault0")
            .options()
                .url("http://localhost:8081")
                .context("ctx0")
                .build()
            .build();

        assertThat(catalog.namespace, equalTo("test"));
        assertThat(catalog.name, equalTo("karapace0"));
        assertThat(catalog.type, equalTo("karapace-schema-registry"));
        assertThat(catalog.vault, equalTo("vault0"));
        assertThat(((KarapaceOptionsConfig) catalog.options).url, equalTo("http://localhost:8081"));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        KarapaceCatalogConfig catalog = KarapaceCatalogConfig.builder(KarapaceCatalogConfig.class::cast)
            .namespace("test")
            .name("karapace1")
            .build();

        assertThat(catalog.name, equalTo("karapace1"));
    }
}
