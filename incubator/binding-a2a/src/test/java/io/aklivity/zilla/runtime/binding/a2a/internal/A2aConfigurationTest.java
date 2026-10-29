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

import static io.aklivity.zilla.runtime.binding.a2a.internal.A2aConfiguration.A2A_CACHE_LEASE_TTL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class A2aConfigurationTest
{
    @Test
    public void shouldVerifyConstants()
    {
        assertThat(A2A_CACHE_LEASE_TTL.name(), equalTo("zilla.binding.a2a.cache.lease.ttl"));
    }

    @Test
    public void shouldDefaultCacheLeaseTtl()
    {
        assertThat(new A2aConfiguration(new Configuration()).cacheLeaseTtl(), equalTo(Duration.ofSeconds(30)));
    }
}
