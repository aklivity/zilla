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
package io.aklivity.zilla.runtime.binding.mcp.http.internal;

import static io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpConfiguration.MCP_HTTP_CLIENT_EXIT;
import static io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpConfiguration.MCP_HTTP_SESSION_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.util.Properties;
import java.util.UUID;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class McpHttpConfigurationTest
{
    public static final String MCP_HTTP_SESSION_ID_NAME = "zilla.binding.mcp.http.session.id";
    public static final String MCP_HTTP_CLIENT_EXIT_NAME = "zilla.binding.mcp.http.client.exit";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(MCP_HTTP_SESSION_ID.name(), MCP_HTTP_SESSION_ID_NAME);
        assertEquals(MCP_HTTP_CLIENT_EXIT.name(), MCP_HTTP_CLIENT_EXIT_NAME);
    }

    @Test
    public void shouldDefaultClientExitToSysHttpClient()
    {
        McpHttpConfiguration config = new McpHttpConfiguration();

        assertEquals("sys:http_client", config.clientExit());
    }

    @Test
    public void shouldOverrideClientExit()
    {
        Properties properties = new Properties();
        properties.setProperty(MCP_HTTP_CLIENT_EXIT_NAME, "test:http0");
        McpHttpConfiguration config = new McpHttpConfiguration(new Configuration(properties));

        assertEquals("test:http0", config.clientExit());
    }

    @Test
    public void shouldSupplyDefaultSessionId()
    {
        McpHttpConfiguration config = new McpHttpConfiguration();

        String sessionId = config.sessionIdSupplier().get();

        assertNotNull(sessionId);
        assertNotNull(UUID.fromString(sessionId));
    }

    @Test
    public void shouldRejectMalformedSessionIdSupplierValue()
    {
        Properties properties = new Properties();
        properties.setProperty(MCP_HTTP_SESSION_ID_NAME, "not-a-valid-reference");
        McpHttpConfiguration config = new McpHttpConfiguration(new Configuration(properties));

        assertThrows(Exception.class, config::sessionIdSupplier);
    }

    @Test
    public void shouldPropagateExceptionFromConfiguredSessionIdSupplier()
    {
        Properties properties = new Properties();
        properties.setProperty(MCP_HTTP_SESSION_ID_NAME,
            "%s::throwingSessionId".formatted(McpHttpConfigurationTest.class.getName()));
        McpHttpConfiguration config = new McpHttpConfiguration(new Configuration(properties));

        assertThrows(RuntimeException.class, () -> config.sessionIdSupplier().get());
    }

    public static String throwingSessionId()
    {
        throw new IllegalStateException("boom");
    }
}
