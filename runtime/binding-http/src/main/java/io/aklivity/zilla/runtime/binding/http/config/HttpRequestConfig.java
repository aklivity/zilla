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

import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;

public class HttpRequestConfig
{
    public enum Method
    {
        GET,
        PUT,
        POST,
        DELETE,
        OPTIONS,
        HEAD,
        PATCH,
        TRACE
    }

    public String path;
    public Method method;
    public List<String> contentType;
    public List<HttpParamConfig> headers;
    public List<HttpParamConfig> pathParams;
    public List<HttpParamConfig> queryParams;
    public ValidatorConfig content;

    public HttpRequestConfig(
        String path,
        Method method,
        List<String> contentType,
        List<HttpParamConfig> headers,
        List<HttpParamConfig> pathParams,
        List<HttpParamConfig> queryParams,
        ValidatorConfig content)
    {
        this.path = path;
        this.method = method;
        this.contentType = contentType;
        this.headers = headers;
        this.pathParams = pathParams;
        this.queryParams = queryParams;
        this.content = content;
    }

    public static HttpRequestConfigBuilder<HttpRequestConfig> builder()
    {
        return new HttpRequestConfigBuilder<>(identity());
    }
}
