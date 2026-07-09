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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal;

import static io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.McpSchemaRegistryConfiguration.MCP_SCHEMA_REGISTRY_SESSION_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.util.Properties;
import java.util.UUID;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class McpSchemaRegistryConfigurationTest
{
    public static final String MCP_SCHEMA_REGISTRY_SESSION_ID_NAME = "zilla.binding.mcp.schema.registry.session.id";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(MCP_SCHEMA_REGISTRY_SESSION_ID.name(), MCP_SCHEMA_REGISTRY_SESSION_ID_NAME);
    }

    @Test
    public void shouldConstructWithNoArgs()
    {
        McpSchemaRegistryConfiguration config = new McpSchemaRegistryConfiguration();

        assertNotNull(config.sessionIdSupplier());
    }

    @Test
    public void shouldSupplyDefaultSessionId()
    {
        McpSchemaRegistryConfiguration config = new McpSchemaRegistryConfiguration();

        String sessionId = config.sessionIdSupplier().get();

        assertNotNull(sessionId);
        assertNotNull(UUID.fromString(sessionId));
    }

    @Test
    public void shouldRejectMalformedSessionIdSupplierValue()
    {
        Properties properties = new Properties();
        properties.setProperty(MCP_SCHEMA_REGISTRY_SESSION_ID_NAME, "not-a-valid-reference");
        McpSchemaRegistryConfiguration config = new McpSchemaRegistryConfiguration(new Configuration(properties));

        assertThrows(Exception.class, config::sessionIdSupplier);
    }

    @Test
    public void shouldPropagateExceptionFromConfiguredSessionIdSupplier()
    {
        Properties properties = new Properties();
        properties.setProperty(MCP_SCHEMA_REGISTRY_SESSION_ID_NAME,
            "%s::throwingSessionId".formatted(McpSchemaRegistryConfigurationTest.class.getName()));
        McpSchemaRegistryConfiguration config = new McpSchemaRegistryConfiguration(new Configuration(properties));

        assertThrows(RuntimeException.class, () -> config.sessionIdSupplier().get());
    }

    public static String throwingSessionId()
    {
        throw new IllegalStateException("boom");
    }
}
