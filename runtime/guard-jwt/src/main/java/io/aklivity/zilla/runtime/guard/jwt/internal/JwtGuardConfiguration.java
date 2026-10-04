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

import io.aklivity.zilla.runtime.engine.Configuration;

public class JwtGuardConfiguration extends Configuration
{
    public static final LongPropertyDef KEY_CACHE_EXPIRY_MILLIS;

    private static final ConfigurationDef JWT_GUARD_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.guard.jwt");
        KEY_CACHE_EXPIRY_MILLIS = config.property("key.cache.expiry.millis", 300_000L);
        JWT_GUARD_CONFIG = config;
    }

    public JwtGuardConfiguration(
        Configuration config)
    {
        super(JWT_GUARD_CONFIG, config);
    }

    public long keyCacheExpiryMillis()
    {
        return KEY_CACHE_EXPIRY_MILLIS.getAsLong(this);
    }
}
