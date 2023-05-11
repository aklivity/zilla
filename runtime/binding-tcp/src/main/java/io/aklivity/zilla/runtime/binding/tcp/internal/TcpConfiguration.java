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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class TcpConfiguration extends Configuration
{
    public static final IntPropertyDef TCP_WINDOW_THRESHOLD;
    public static final IntPropertyDef TCP_MAX_CONNECTIONS;

    private static final ConfigurationDef TCP_CONFIG;

    static
    {
        ConfigurationDef config = new ConfigurationDef("zilla.binding.tcp");
        TCP_WINDOW_THRESHOLD = config.property("window.threshold", 0);
        TCP_MAX_CONNECTIONS = config.property("max.connections", Integer.MAX_VALUE);
        TCP_CONFIG = config;
    }

    public TcpConfiguration(
        Configuration config)
    {
        super(TCP_CONFIG, config);
    }

    public int windowThreshold()
    {
        int threshold = TCP_WINDOW_THRESHOLD.getAsInt(this);
        assert threshold >= 0 && threshold <= 100;
        return threshold;
    }

    public int maxConnections()
    {
        return TCP_MAX_CONNECTIONS.getAsInt(this);
    }
}
