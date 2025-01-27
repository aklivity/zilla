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

import java.util.function.Function;

public class OpenapiServerConfig
{
    public final String url;

    public static OpenapiServerConfigBuilder<OpenapiServerConfig> builder()
    {
        return new OpenapiServerConfigBuilder<>(OpenapiServerConfig.class::cast);
    }

    public static <T> OpenapiServerConfigBuilder<T> builder(
        Function<OpenapiServerConfig, T> mapper)
    {
        return new OpenapiServerConfigBuilder<>(mapper);
    }

    OpenapiServerConfig(
        String url)
    {
        this.url = url;
    }
}

