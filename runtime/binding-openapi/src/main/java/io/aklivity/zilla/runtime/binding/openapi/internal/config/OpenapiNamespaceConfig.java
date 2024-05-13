/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiServerView;

public class OpenapiNamespaceConfig
{
    List<String> openapiLabels;
    List<OpenapiServerView> servers;
    List<Openapi> openapis;
    List<OpenapiSchemaConfig> configs;

    public OpenapiNamespaceConfig()
    {
        openapiLabels = new ArrayList<>();
        servers = new ArrayList<>();
        openapis = new ArrayList<>();
        configs = new ArrayList<>();
    }

    public void addSpecForNamespace(
        List<OpenapiServerView> servers,
        OpenapiSchemaConfig config,
        Openapi openapi)
    {
        this.openapiLabels.add(config.apiLabel);
        this.servers.addAll(servers);
        this.configs.add(config);
        this.openapis.add(openapi);
    }
}
