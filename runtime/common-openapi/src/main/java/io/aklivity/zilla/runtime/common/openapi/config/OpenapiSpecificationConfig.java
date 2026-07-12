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
package io.aklivity.zilla.runtime.common.openapi.config;

import java.util.List;
import java.util.Map;

public class OpenapiSpecificationConfig
{
    public final String label;
    public final String server;
    public final List<OpenapiCatalogConfig> catalogs;
    public final Map<String, String> security;
    public final OpenapiCatalogConfig overlay;

    public OpenapiSpecificationConfig(
        String label,
        String server,
        List<OpenapiCatalogConfig> catalogs,
        Map<String, String> security,
        OpenapiCatalogConfig overlay)
    {
        this.label = label;
        this.server = server;
        this.catalogs = catalogs;
        this.security = security;
        this.overlay = overlay;
    }

    public OpenapiSpecificationConfig(
        String label,
        String server,
        List<OpenapiCatalogConfig> catalogs,
        Map<String, String> security)
    {
        this(label, server, catalogs, security, null);
    }

    public OpenapiSpecificationConfig(
        String label,
        String server,
        List<OpenapiCatalogConfig> catalogs)
    {
        this(label, server, catalogs, null, null);
    }

    public OpenapiSpecificationConfig(
        String apiLabel,
        List<OpenapiCatalogConfig> catalogs)
    {
        this(apiLabel, null, catalogs, null, null);
    }
}
