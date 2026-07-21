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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal;

import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.McpKafkaConfiguration.MCP_KAFKA_SESSION_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class McpKafkaConfigurationTest
{
    public static final String MCP_KAFKA_SESSION_ID_NAME = "zilla.binding.mcp.kafka.session.id";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(MCP_KAFKA_SESSION_ID.name(), MCP_KAFKA_SESSION_ID_NAME);
    }

    @Test
    public void shouldCreateWithNoArgConstructor() throws Exception
    {
        final McpKafkaConfiguration config = new McpKafkaConfiguration();

        assertNotNull(config.sessionIdSupplier());
        assertNotNull(config.sessionIdSupplier().get());
    }

    @Test
    public void shouldDecodeSessionIdSupplierFromMethodReference() throws Exception
    {
        final Properties properties = new Properties();
        properties.setProperty(MCP_KAFKA_SESSION_ID_NAME, "%s::fixedSessionId".formatted(getClass().getName()));
        final McpKafkaConfiguration config = new McpKafkaConfiguration(new Configuration(properties));

        assertEquals("session-fixed", config.sessionIdSupplier().get());
    }

    public static String fixedSessionId()
    {
        return "session-fixed";
    }
}
