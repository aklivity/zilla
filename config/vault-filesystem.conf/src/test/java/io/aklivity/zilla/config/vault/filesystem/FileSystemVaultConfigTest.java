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
package io.aklivity.zilla.config.vault.filesystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class FileSystemVaultConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        FileSystemVaultConfig vault = FileSystemVaultConfig.builder()
            .namespace("test")
            .name("filesystem0")
            .options()
                .revocation("crl")
                .build()
            .build();

        assertThat(vault.namespace, equalTo("test"));
        assertThat(vault.name, equalTo("filesystem0"));
        assertThat(vault.type, equalTo("filesystem"));
        assertThat(((FileSystemOptionsConfig) vault.options).revocation, equalTo("crl"));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        FileSystemVaultConfig vault = FileSystemVaultConfig.builder(FileSystemVaultConfig.class::cast)
            .namespace("test")
            .name("filesystem1")
            .build();

        assertThat(vault.name, equalTo("filesystem1"));
    }
}
