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

import java.time.Duration;

import io.aklivity.zilla.runtime.engine.Configuration;

public class JwtGuardConfiguration extends Configuration
{
    public static final PropertyDef<Duration> KEY_CACHE_EXPIRY;

    private static final ConfigurationDef JWT_GUARD_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.guard.jwt");
        KEY_CACHE_EXPIRY = config.property(Duration.class, "key.cache.expiry", (c, v) -> Duration.parse(v), "PT5M");
        JWT_GUARD_CONFIG = config;
    }

    public JwtGuardConfiguration(
        Configuration config)
    {
        super(JWT_GUARD_CONFIG, config);
    }

    public Duration keyCacheExpiry()
    {
        return KEY_CACHE_EXPIRY.get(this);
    }
}
