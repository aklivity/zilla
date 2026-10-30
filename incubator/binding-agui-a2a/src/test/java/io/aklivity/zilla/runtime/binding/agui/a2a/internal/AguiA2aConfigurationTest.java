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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal;

import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfiguration.AGUI_A2A_HANDLE_TTL;
import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfiguration.AGUI_A2A_THREAD_CAPACITY;
import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfiguration.AGUI_A2A_THREAD_TTL;
import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class AguiA2aConfigurationTest
{
    public static final String AGUI_A2A_THREAD_TTL_NAME = "zilla.binding.agui.a2a.thread.ttl";
    public static final String AGUI_A2A_THREAD_CAPACITY_NAME = "zilla.binding.agui.a2a.thread.capacity";
    public static final String AGUI_A2A_HANDLE_TTL_NAME = "zilla.binding.agui.a2a.handle.ttl";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(AGUI_A2A_THREAD_TTL.name(), AGUI_A2A_THREAD_TTL_NAME);
        assertEquals(AGUI_A2A_THREAD_CAPACITY.name(), AGUI_A2A_THREAD_CAPACITY_NAME);
        assertEquals(AGUI_A2A_HANDLE_TTL.name(), AGUI_A2A_HANDLE_TTL_NAME);
    }

    @Test
    public void shouldDefaultProperties() throws Exception
    {
        final AguiA2aConfiguration config = new AguiA2aConfiguration();

        assertEquals(Duration.ofHours(1L), config.threadTtl());
        assertEquals(4096, config.threadCapacity());
        assertEquals(Duration.ofMinutes(15L), config.handleTtl());
    }

    @Test
    public void shouldConfigureProperties() throws Exception
    {
        final Properties properties = new Properties();
        properties.setProperty(AGUI_A2A_THREAD_TTL_NAME, "PT30M");
        properties.setProperty(AGUI_A2A_THREAD_CAPACITY_NAME, "128");
        properties.setProperty(AGUI_A2A_HANDLE_TTL_NAME, "PT5M");
        final AguiA2aConfiguration config = new AguiA2aConfiguration(new Configuration(properties));

        assertEquals(Duration.ofMinutes(30L), config.threadTtl());
        assertEquals(128, config.threadCapacity());
        assertEquals(Duration.ofMinutes(5L), config.handleTtl());
    }
}
