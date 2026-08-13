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
package io.aklivity.zilla.config.binding.openapi;

import java.util.List;
import java.util.Map;

public class OpenapiSpecificationConfig
{
    public final String label;
    public final List<String> servers;
    public final List<OpenapiCatalogConfig> catalogs;
    public final Map<String, String> security;

    public OpenapiSpecificationConfig(
        String label,
        List<String> servers,
        List<OpenapiCatalogConfig> catalogs,
        Map<String, String> security)
    {
        this.label = label;
        this.servers = servers;
        this.catalogs = catalogs;
        this.security = security;
    }

    public OpenapiSpecificationConfig(
        String label,
        List<String> servers,
        List<OpenapiCatalogConfig> catalogs)
    {
        this(label, servers, catalogs, null);
    }

    public OpenapiSpecificationConfig(
        String label,
        List<OpenapiCatalogConfig> catalogs)
    {
        this(label, null, catalogs, null);
    }
}
