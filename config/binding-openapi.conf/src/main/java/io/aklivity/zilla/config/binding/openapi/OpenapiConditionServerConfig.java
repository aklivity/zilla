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

import java.util.function.Function;

import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;

public class OpenapiConditionServerConfig
{
    public final String url;

    public static OpenapiConditionServerConfigBuilder<OpenapiConditionServerConfig> builder()
    {
        return new OpenapiConditionServerConfigBuilder<>(OpenapiConditionServerConfig.class::cast);
    }

    public static <T> OpenapiConditionServerConfigBuilder<T> builder(
        Function<OpenapiConditionServerConfig, T> mapper)
    {
        return new OpenapiConditionServerConfigBuilder<>(mapper);
    }

    OpenapiConditionServerConfig(
        String url)
    {
        this.url = url;
    }

    boolean matches(
        OpenapiServerView server)
    {
        return url == null || url.equals(server.url.toString());
    }
}
