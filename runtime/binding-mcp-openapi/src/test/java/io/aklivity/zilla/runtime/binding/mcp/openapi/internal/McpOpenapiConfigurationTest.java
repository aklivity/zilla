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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal;

import static io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenapiConfiguration.MCP_OPENAPI_COMPOSITE_ROUTE_ID;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class McpOpenapiConfigurationTest
{
    public static final String MCP_OPENAPI_COMPOSITE_ROUTE_ID_NAME = "zilla.binding.mcp.openapi.composite.route.id";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(MCP_OPENAPI_COMPOSITE_ROUTE_ID.name(), MCP_OPENAPI_COMPOSITE_ROUTE_ID_NAME);
    }
}
