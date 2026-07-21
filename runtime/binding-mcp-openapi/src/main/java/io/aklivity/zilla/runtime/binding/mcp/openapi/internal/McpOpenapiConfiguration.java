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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class McpOpenapiConfiguration extends Configuration
{
    public static final LongPropertyDef MCP_OPENAPI_COMPOSITE_ROUTE_ID;
    public static final PropertyDef<String> MCP_OPENAPI_HTTP_CLIENT_EXIT;
    private static final ConfigurationDef MCP_OPENAPI_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mcp.openapi");
        MCP_OPENAPI_COMPOSITE_ROUTE_ID = config.property("composite.route.id", -1L);
        MCP_OPENAPI_HTTP_CLIENT_EXIT = config.property("http.client.exit", "sys:http_client");
        MCP_OPENAPI_CONFIG = config;
    }

    public McpOpenapiConfiguration()
    {
        super(MCP_OPENAPI_CONFIG, new Configuration());
    }

    public McpOpenapiConfiguration(
        Configuration config)
    {
        super(MCP_OPENAPI_CONFIG, config);
    }

    public long compositeRouteId()
    {
        return MCP_OPENAPI_COMPOSITE_ROUTE_ID.getAsLong(this);
    }

    public String httpClientExit()
    {
        return MCP_OPENAPI_HTTP_CLIENT_EXIT.get(this);
    }
}
