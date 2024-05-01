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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;

class AsyncapiNamespaceConfig
{
    List<String> asyncapiLabels;
    List<AsyncapiServerView> servers;
    List<Asyncapi> asyncapis;
    List<AsyncapiSchemaConfig> configs;

    AsyncapiNamespaceConfig()
    {
        asyncapiLabels = new ArrayList<>();
        servers = new ArrayList<>();
        asyncapis = new ArrayList<>();
        configs = new ArrayList<>();
    }

    public void addSpecForNamespace(
        List<AsyncapiServerView> servers,
        AsyncapiSchemaConfig config,
        Asyncapi asyncapi)
    {
        this.asyncapiLabels.add(config.apiLabel);
        this.servers.addAll(servers);
        this.configs.add(config);
        this.asyncapis.add(asyncapi);
    }
}
