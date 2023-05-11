/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_POOL_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_POOL_CAPACITY_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CONFIG_URL_NAME;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EngineConfigurationTest
{
    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(ENGINE_BUFFER_POOL_CAPACITY.name(), ENGINE_BUFFER_POOL_CAPACITY_NAME);
        assertEquals(ENGINE_BUFFER_SLOT_CAPACITY.name(), ENGINE_BUFFER_SLOT_CAPACITY_NAME);
        assertEquals(ENGINE_CONFIG_URL.name(), ENGINE_CONFIG_URL_NAME);
    }
}
