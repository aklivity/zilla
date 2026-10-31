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
package io.aklivity.zilla.runtime.binding.a2a.internal;

import java.time.Duration;

import io.aklivity.zilla.runtime.engine.Configuration;

public class A2aConfiguration extends Configuration
{
    private static final ConfigurationDef A2A_CONFIG;

    public static final PropertyDef<Duration> A2A_CACHE_LEASE_TTL;
    public static final IntPropertyDef A2A_PUSH_DELIVERY_RETRIES;
    public static final PropertyDef<Duration> A2A_PUSH_DELIVERY_BACKOFF;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.a2a");
        A2A_CACHE_LEASE_TTL = config.property(Duration.class, "cache.lease.ttl",
            (c, v) -> Duration.parse(v), "PT30S");
        A2A_PUSH_DELIVERY_RETRIES = config.property("push.delivery.retries", 2);
        A2A_PUSH_DELIVERY_BACKOFF = config.property(Duration.class, "push.delivery.backoff",
            (c, v) -> Duration.parse(v), "PT1S");
        A2A_CONFIG = config;
    }

    public A2aConfiguration(
        Configuration config)
    {
        super(A2A_CONFIG, config);
    }

    public Duration cacheLeaseTtl()
    {
        return A2A_CACHE_LEASE_TTL.get(this);
    }

    public int pushDeliveryRetries()
    {
        return A2A_PUSH_DELIVERY_RETRIES.getAsInt(this);
    }

    public Duration pushDeliveryBackoff()
    {
        return A2A_PUSH_DELIVERY_BACKOFF.get(this);
    }
}
