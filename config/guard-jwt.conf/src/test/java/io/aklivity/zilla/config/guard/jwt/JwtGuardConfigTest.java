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
package io.aklivity.zilla.config.guard.jwt;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class JwtGuardConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        JwtGuardConfig guard = JwtGuardConfig.builder()
            .namespace("test")
            .name("jwt0")
            .options()
                .issuer("https://issuer")
                .audience("https://audience")
                .build()
            .build();

        assertThat(guard.namespace, equalTo("test"));
        assertThat(guard.name, equalTo("jwt0"));
        assertThat(guard.type, equalTo("jwt"));
        assertThat(((JwtOptionsConfig) guard.options).issuer, equalTo("https://issuer"));
        assertThat(((JwtOptionsConfig) guard.options).audience, equalTo("https://audience"));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        JwtGuardConfig guard = JwtGuardConfig.builder(JwtGuardConfig.class::cast)
            .namespace("test")
            .name("jwt1")
            .build();

        assertThat(guard.name, equalTo("jwt1"));
    }
}
