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
package io.aklivity.zilla.config.model.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.VaultedConfig;

public class StringModelConfigTest
{
    @Test
    public void shouldForwardRefContributedByExtension()
    {
        VaultedConfig vaulted = VaultedConfig.builder().name("vault0").build();

        StringModelConfig config = StringModelConfig.builder()
            .ref(vaulted)
            .build();

        List<NamedConfig> refs = config.refs();
        assertThat(refs, hasItem(vaulted));
    }

    @Test
    public void shouldDefaultRefsToEmpty()
    {
        StringModelConfig config = StringModelConfig.builder().build();

        assertThat(config.refs(), empty());
    }
}
