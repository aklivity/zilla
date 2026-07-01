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
package io.aklivity.zilla.runtime.binding.mcp.openapi.config;

import java.util.List;
import java.util.Map;

public final class McpOpenapiSpecificationConfig
{
    public final String label;
    public final List<String> servers;
    public final List<McpOpenapiCatalogConfig> catalogs;
    public final Map<String, String> security;

    public McpOpenapiSpecificationConfig(
        String label,
        List<String> servers,
        List<McpOpenapiCatalogConfig> catalogs)
    {
        this(label, servers, catalogs, null);
    }

    public McpOpenapiSpecificationConfig(
        String label,
        List<String> servers,
        List<McpOpenapiCatalogConfig> catalogs,
        Map<String, String> security)
    {
        this.label = label;
        this.servers = servers;
        this.catalogs = catalogs;
        this.security = security;
    }
}
