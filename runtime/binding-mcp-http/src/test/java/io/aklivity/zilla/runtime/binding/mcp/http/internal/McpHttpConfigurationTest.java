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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.util.Properties;
import java.util.UUID;
import java.util.function.LongFunction;

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

        String sessionId = config.sessionIdSupplier().apply(0L);

        assertNotNull(sessionId);
        assertNotNull(UUID.fromString(sessionId));
    }

    @Test
    public void shouldEmbedGivenAffinityInDefaultSessionId()
    {
        McpHttpConfiguration config = new McpHttpConfiguration();

        String sessionId = config.sessionIdSupplier().apply(0x07000001L);

        assertEquals("07000001", sessionId.substring(28, 36));
    }

    @Test
    public void shouldMaskAffinityToThirtyTwoBitsInDefaultSessionId()
    {
        McpHttpConfiguration config = new McpHttpConfiguration();

        String sessionId = config.sessionIdSupplier().apply(0xffff_ffff_0000_0001L);

        assertEquals("00000001", sessionId.substring(28, 36));
    }

    @Test
    public void shouldMintDistinctSessionIdsForTheSameAffinity()
    {
        McpHttpConfiguration config = new McpHttpConfiguration();
        LongFunction<String> supplySessionId = config.sessionIdSupplier();

        String first = supplySessionId.apply(7L);
        String second = supplySessionId.apply(7L);

        assertNotEquals(first, second);
        assertEquals("00000007", first.substring(28, 36));
        assertEquals("00000007", second.substring(28, 36));
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

        assertThrows(RuntimeException.class, () -> config.sessionIdSupplier().apply(0L));
    }

    public static String throwingSessionId(
        long affinity)
    {
        throw new IllegalStateException("boom");
    }
}
