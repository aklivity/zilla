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
package io.aklivity.zilla.config.guard.inline;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class InlineGuardConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        InlineGuardConfig guard = InlineGuardConfig.builder()
            .namespace("test")
            .name("inline0")
            .options()
                .identity("id")
                .credentials("credentials")
                .build()
            .build();

        assertThat(guard.namespace, equalTo("test"));
        assertThat(guard.name, equalTo("inline0"));
        assertThat(guard.type, equalTo("inline"));
        assertThat(((InlineOptionsConfig) guard.options).identity, equalTo("id"));
        assertThat(((InlineOptionsConfig) guard.options).credentials, equalTo("credentials"));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        InlineGuardConfig guard = InlineGuardConfig.builder(InlineGuardConfig.class::cast)
            .namespace("test")
            .name("inline1")
            .build();

        assertThat(guard.name, equalTo("inline1"));
    }
}
