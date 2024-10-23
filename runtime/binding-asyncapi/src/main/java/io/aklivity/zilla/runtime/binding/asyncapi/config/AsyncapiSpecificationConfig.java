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
package io.aklivity.zilla.runtime.binding.asyncapi.config;

import java.util.List;
import java.util.function.Function;

public class AsyncapiSpecificationConfig
{
    public final String label;
    public final List<AsyncapiCatalogConfig> catalogs;
    public final List<AsyncapiServerConfig> servers;

    public static AsyncapiSpecificationConfigBuilder<AsyncapiSpecificationConfig> builder()
    {
        return new AsyncapiSpecificationConfigBuilder<>(AsyncapiSpecificationConfig.class::cast);
    }

    public static <T> AsyncapiSpecificationConfigBuilder<T> builder(
        Function<AsyncapiSpecificationConfig, T> mapper)
    {
        return new AsyncapiSpecificationConfigBuilder<>(mapper);
    }

    AsyncapiSpecificationConfig(
        String label,
        List<AsyncapiServerConfig> servers,
        List<AsyncapiCatalogConfig> catalogs)
    {
        this.label = label;
        this.catalogs = catalogs;
        this.servers = servers;
    }
}
