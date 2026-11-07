/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BACKOFF_MAX_PARK_NANOS_BEFORE_SELECT;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BACKOFF_MAX_SPINS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BACKOFF_MAX_YIELDS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BACKOFF_MIN_PARK_NANOS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_POOL_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CACERTS_STORE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CACERTS_STORE_PASS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CACERTS_STORE_TYPE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CLOCK;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_LOCAL_CONFIG_URI;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_NODE_INSTANCE_ID;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_ROUTER;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_SERVICE_HOSTNAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_POOL_CAPACITY_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CACERTS_STORE_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CACERTS_STORE_PASS_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CACERTS_STORE_TYPE_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CLOCK_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CONFIG_URL_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_LOCAL_CONFIG_URI_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_NODE_INSTANCE_ID_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_ROUTER_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_SERVICE_HOSTNAME_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_WORKER_CAPACITY_NAME;
import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class EngineConfigurationTest
{
    @Test
    public void shouldSumSpinsYieldsAndParksForMaxIdleCount()
    {
        Properties properties = new Properties();
        properties.setProperty(ENGINE_BACKOFF_MAX_SPINS.name(), "10");
        properties.setProperty(ENGINE_BACKOFF_MAX_YIELDS.name(), "20");
        properties.setProperty(ENGINE_BACKOFF_MIN_PARK_NANOS.name(), "1");
        properties.setProperty(ENGINE_BACKOFF_MAX_PARK_NANOS_BEFORE_SELECT.name(), "8");

        EngineConfiguration config = new EngineConfiguration(properties);

        assertEquals(33, config.maxIdleCount());
    }

    @Test
    public void shouldSaturateMaxIdleCountWhenSpinsAreUnbounded()
    {
        Properties properties = new Properties();
        properties.setProperty(ENGINE_BACKOFF_MAX_SPINS.name(), Integer.toString(Integer.MAX_VALUE));

        EngineConfiguration config = new EngineConfiguration(properties);

        assertEquals(Integer.MAX_VALUE, config.maxIdleCount());
    }

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(ENGINE_BUFFER_POOL_CAPACITY.name(), ENGINE_BUFFER_POOL_CAPACITY_NAME);
        assertEquals(ENGINE_BUFFER_SLOT_CAPACITY.name(), ENGINE_BUFFER_SLOT_CAPACITY_NAME);
        assertEquals(ENGINE_CACERTS_STORE_TYPE.name(), ENGINE_CACERTS_STORE_TYPE_NAME);
        assertEquals(ENGINE_CACERTS_STORE.name(), ENGINE_CACERTS_STORE_NAME);
        assertEquals(ENGINE_CACERTS_STORE_PASS.name(), ENGINE_CACERTS_STORE_PASS_NAME);
        assertEquals(ENGINE_CONFIG_URL.name(), ENGINE_CONFIG_URL_NAME);
        assertEquals(ENGINE_LOCAL_CONFIG_URI.name(), ENGINE_LOCAL_CONFIG_URI_NAME);
        assertEquals(ENGINE_WORKER_CAPACITY.name(), ENGINE_WORKER_CAPACITY_NAME);
        assertEquals(ENGINE_CLOCK.name(), ENGINE_CLOCK_NAME);
        assertEquals(ENGINE_ROUTER.name(), ENGINE_ROUTER_NAME);
        assertEquals(ENGINE_SERVICE_HOSTNAME.name(), ENGINE_SERVICE_HOSTNAME_NAME);
        assertEquals(ENGINE_NODE_INSTANCE_ID.name(), ENGINE_NODE_INSTANCE_ID_NAME);
    }
}
