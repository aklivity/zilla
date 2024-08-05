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
package io.aklivity.zilla.runtime.binding.asyncapi.config;

import java.util.Optional;
import java.util.function.Function;

public class AsyncapiServerConfig
{
    public final String host;
    public final String url;
    public final String pathname;

    public static AsyncapiServerConfigBuilder<AsyncapiServerConfig> builder()
    {
        return new AsyncapiServerConfigBuilder<>(AsyncapiServerConfig.class::cast);
    }

    public static <T> AsyncapiServerConfigBuilder<T> builder(
        Function<AsyncapiServerConfig, T> mapper)
    {
        return new AsyncapiServerConfigBuilder<>(mapper);
    }

    AsyncapiServerConfig(
        String host,
        String url,
        String pathname)
    {
        this.host = Optional.ofNullable(host).orElse("");
        this.url = Optional.ofNullable(url).orElse("");
        this.pathname = Optional.ofNullable(pathname).orElse("");
    }
}

