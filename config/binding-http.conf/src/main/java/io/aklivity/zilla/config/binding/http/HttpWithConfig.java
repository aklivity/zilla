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
package io.aklivity.zilla.config.binding.http;

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.WithConfig;

public final class HttpWithConfig extends WithConfig
{
    public final Map<String, String> overrides;

    public static HttpWithConfigBuilder<HttpWithConfig> builder()
    {
        return new HttpWithConfigBuilder<>(HttpWithConfig.class::cast);
    }

    public static <T> HttpWithConfigBuilder<T> builder(
        Function<WithConfig, T> mapper)
    {
        return new HttpWithConfigBuilder<>(mapper);
    }

    HttpWithConfig(
        long compositeId,
        Map<String, String> overrides)
    {
        super(compositeId);
        this.overrides = overrides;
    }
}
