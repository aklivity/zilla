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
package io.aklivity.zilla.config.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class VaultedConfigTest
{
    @Test
    public void shouldWork()
    {
        VaultedConfig vaulted = VaultedConfig.builder()
            .name("vault0")
            .build();

        assertThat(vaulted.name, equalTo("vault0"));
        assertThat(vaulted.ext("encryption", Config.class), nullValue());
    }

    @Test
    public void shouldResolveId()
    {
        VaultedConfig vaulted = VaultedConfig.builder()
            .name("vault0")
            .build();

        vaulted.id = 42L;

        assertThat(vaulted.id, equalTo(42L));
    }
}
