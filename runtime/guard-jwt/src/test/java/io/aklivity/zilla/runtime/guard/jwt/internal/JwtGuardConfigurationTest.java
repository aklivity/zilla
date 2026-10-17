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
package io.aklivity.zilla.runtime.guard.jwt.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class JwtGuardConfigurationTest
{
    @Test
    public void shouldDefaultKeyCacheExpiry()
    {
        JwtGuardConfiguration config = new JwtGuardConfiguration(new Configuration());

        assertThat(config.keyCacheExpiry(), equalTo(Duration.ofMinutes(5L)));
    }

    @Test
    public void shouldConfigureKeyCacheExpiry()
    {
        Properties properties = new Properties();
        properties.setProperty("zilla.guard.jwt.key.cache.expiry", "PT1M");

        JwtGuardConfiguration config = new JwtGuardConfiguration(new Configuration(properties));

        assertThat(config.keyCacheExpiry(), equalTo(Duration.ofMinutes(1L)));
    }
}
