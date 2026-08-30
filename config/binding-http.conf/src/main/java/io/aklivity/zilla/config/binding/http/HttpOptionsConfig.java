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

import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class HttpOptionsConfig extends OptionsConfig
{
    public final SortedSet<HttpVersion>  versions;
    public final Map<String, String>  overrides;
    public final HttpAccessControlConfig access;
    public final HttpAuthorizationConfig authorization;
    public final List<HttpRequestConfig> requests;

    public static HttpOptionsConfigBuilder<HttpOptionsConfig> builder()
    {
        return new HttpOptionsConfigBuilder<>(HttpOptionsConfig.class::cast);
    }

    public static <T> HttpOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new HttpOptionsConfigBuilder<>(mapper);
    }

    HttpOptionsConfig(
        SortedSet<HttpVersion>  versions,
        Map<String, String> overrides,
        HttpAccessControlConfig access,
        HttpAuthorizationConfig authorization,
        List<HttpRequestConfig> requests,
        List<NamedConfig> refs)
    {
        super(null, refs);
        this.versions = versions;
        this.overrides = overrides;
        this.access = access;
        this.authorization = authorization;
        this.requests = requests;
    }
}
