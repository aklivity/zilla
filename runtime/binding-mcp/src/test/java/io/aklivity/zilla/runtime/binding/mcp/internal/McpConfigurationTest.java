/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal;

import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_CLIENT_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_CLIENT_VERSION;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_INACTIVITY_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_KEEPALIVE_TOLERANCE;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_SERVER_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_SERVER_VERSION;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_SESSION_ID;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_SSE_KEEPALIVE_INTERVAL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DETACH_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_SYNTHETIC_ABORT;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class McpConfigurationTest
{
    public static final String ENGINE_DETACH_ON_CLOSE_NAME = "zilla.engine.detach.on.close";
    public static final String ENGINE_SYNTHETIC_ABORT_NAME = "zilla.engine.synthetic.abort";
    public static final String MCP_SESSION_ID_NAME = "zilla.binding.mcp.session.id";
    public static final String MCP_SERVER_NAME_NAME = "zilla.binding.mcp.server.name";
    public static final String MCP_SERVER_VERSION_NAME = "zilla.binding.mcp.server.version";
    public static final String MCP_CLIENT_NAME_NAME = "zilla.binding.mcp.client.name";
    public static final String MCP_CLIENT_VERSION_NAME = "zilla.binding.mcp.client.version";
    public static final String MCP_INACTIVITY_TIMEOUT_NAME = "zilla.binding.mcp.inactivity.timeout";
    public static final String MCP_KEEPALIVE_TOLERANCE_NAME = "zilla.binding.mcp.keepalive.tolerance";
    public static final String MCP_SSE_KEEPALIVE_INTERVAL_NAME = "zilla.binding.mcp.sse.keepalive.interval";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(ENGINE_DETACH_ON_CLOSE.name(), ENGINE_DETACH_ON_CLOSE_NAME);
        assertEquals(ENGINE_SYNTHETIC_ABORT.name(), ENGINE_SYNTHETIC_ABORT_NAME);
        assertEquals(MCP_SESSION_ID.name(), MCP_SESSION_ID_NAME);
        assertEquals(MCP_SERVER_NAME.name(), MCP_SERVER_NAME_NAME);
        assertEquals(MCP_SERVER_VERSION.name(), MCP_SERVER_VERSION_NAME);
        assertEquals(MCP_CLIENT_NAME.name(), MCP_CLIENT_NAME_NAME);
        assertEquals(MCP_CLIENT_VERSION.name(), MCP_CLIENT_VERSION_NAME);
        assertEquals(MCP_INACTIVITY_TIMEOUT.name(), MCP_INACTIVITY_TIMEOUT_NAME);
        assertEquals(MCP_KEEPALIVE_TOLERANCE.name(), MCP_KEEPALIVE_TOLERANCE_NAME);
        assertEquals(MCP_SSE_KEEPALIVE_INTERVAL.name(), MCP_SSE_KEEPALIVE_INTERVAL_NAME);
    }
}
