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
package io.aklivity.zilla.config.binding.ws;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;

public final class WsConditionConfig extends ConditionConfig
{
    public final String protocol;
    public final String scheme;
    public final String authority;
    public final String path;

    public static WsConditionConfigBuilder<WsConditionConfig> builder()
    {
        return new WsConditionConfigBuilder<>(WsConditionConfig.class::cast);
    }

    public static <T> WsConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new WsConditionConfigBuilder<>(mapper);
    }

    WsConditionConfig(
        String protocol,
        String scheme,
        String authority,
        String path)
    {
        this.protocol = protocol;
        this.scheme = scheme;
        this.authority = authority;
        this.path = path;
    }
}
