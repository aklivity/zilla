/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.config;

import static java.util.function.Function.identity;

import java.util.List;

public final class HttpCredentialsConfig
{
    public final List<HttpPatternConfig> headers;
    public final List<HttpPatternConfig> parameters;
    public final List<HttpPatternConfig> cookies;

    public static HttpCredentialsConfigBuilder<HttpCredentialsConfig> builder()
    {
        return new HttpCredentialsConfigBuilder<>(identity());
    }

    HttpCredentialsConfig(
        List<HttpPatternConfig> headers,
        List<HttpPatternConfig> parameters,
        List<HttpPatternConfig> cookies)
    {
        this.headers = headers;
        this.parameters = parameters;
        this.cookies = cookies;
    }
}
