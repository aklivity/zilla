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
package io.aklivity.zilla.config.catalog.filesystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class FilesystemCatalogConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        FilesystemCatalogConfig catalog = FilesystemCatalogConfig.builder()
            .namespace("test")
            .name("filesystem0")
            .vault("vault0")
            .options()
                .subjects()
                    .subject("subject0")
                    .path("subject0.avsc")
                    .build()
                .build()
            .build();

        assertThat(catalog.namespace, equalTo("test"));
        assertThat(catalog.name, equalTo("filesystem0"));
        assertThat(catalog.type, equalTo("filesystem"));
        assertThat(catalog.vault, equalTo("vault0"));
        assertThat(((FilesystemOptionsConfig) catalog.options).subjects.get(0).path, equalTo("subject0.avsc"));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        FilesystemCatalogConfig catalog = FilesystemCatalogConfig.builder(FilesystemCatalogConfig.class::cast)
            .namespace("test")
            .name("filesystem1")
            .build();

        assertThat(catalog.name, equalTo("filesystem1"));
    }
}
