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
package io.aklivity.zilla.runtime.binding.openapi.config;

import static java.util.Collections.emptyList;

import java.util.List;

public class OpenapiSpecificationConfig
{
    public final String apiLabel;
    public final List<OpenapiServerConfig> servers;
    public final List<OpenapiCatalogConfig> catalogs;

    public OpenapiSpecificationConfig(
        String apiLabel,
        List<OpenapiServerConfig> servers,
        List<OpenapiCatalogConfig> catalogs)
    {
        this.apiLabel = apiLabel;
        this.servers = servers;
        this.catalogs = catalogs;
    }

    public OpenapiSpecificationConfig(
        String apiLabel,
        List<OpenapiCatalogConfig> catalogs)
    {
        this(apiLabel, emptyList(), catalogs);
    }
}
