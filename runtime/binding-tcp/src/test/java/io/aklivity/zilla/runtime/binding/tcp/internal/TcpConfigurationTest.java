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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import static io.aklivity.zilla.runtime.binding.tcp.internal.TcpConfiguration.TCP_BACKLOG;
import static io.aklivity.zilla.runtime.binding.tcp.internal.TcpConfiguration.TCP_REBALANCE_TOLERANCE;
import static io.aklivity.zilla.runtime.binding.tcp.internal.TcpConfiguration.TCP_WINDOW_THRESHOLD;
import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class TcpConfigurationTest
{
    @Test
    public void shouldVerifyConstants()
    {
        assertEquals("zilla.binding.tcp.window.threshold", TCP_WINDOW_THRESHOLD.name());
        assertEquals("zilla.binding.tcp.backlog", TCP_BACKLOG.name());
        assertEquals("zilla.binding.tcp.rebalance.tolerance", TCP_REBALANCE_TOLERANCE.name());
    }

    @Test
    public void shouldDefaultBacklogToOperatingSystemMaximum()
    {
        final TcpConfiguration config = new TcpConfiguration(new Configuration());

        assertEquals(Integer.MAX_VALUE, config.backlog());
    }

    @Test
    public void shouldConfigureBacklog()
    {
        final Properties properties = new Properties();
        properties.setProperty(TCP_BACKLOG.name(), "1024");

        final TcpConfiguration config = new TcpConfiguration(new Configuration(properties));

        assertEquals(1024, config.backlog());
    }

    @Test
    public void shouldDefaultRebalanceTolerance()
    {
        final TcpConfiguration config = new TcpConfiguration(new Configuration());

        assertEquals(2, config.rebalanceTolerance());
    }

    @Test
    public void shouldConfigureRebalanceTolerance()
    {
        final Properties properties = new Properties();
        properties.setProperty(TCP_REBALANCE_TOLERANCE.name(), "5");

        final TcpConfiguration config = new TcpConfiguration(new Configuration(properties));

        assertEquals(5, config.rebalanceTolerance());
    }
}
