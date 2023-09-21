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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;

public class HttpRequestConfigBuilder<T> extends ConfigBuilder<T, HttpRequestConfigBuilder<T>>
{
    private final Function<HttpRequestConfig, T> mapper;

    private String path;
    private HttpRequestConfig.Method method;
    private List<String> contentTypes;
    // TODO: Ati - headers, params
    private ValidatorConfig content;

    HttpRequestConfigBuilder(
        Function<HttpRequestConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpRequestConfigBuilder<T>> thisType()
    {
        return (Class<HttpRequestConfigBuilder<T>>) getClass();
    }

    public HttpRequestConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }

    public HttpRequestConfigBuilder<T> method(
        HttpRequestConfig.Method method)
    {
        this.method = method;
        return this;
    }

    public HttpRequestConfigBuilder<T> contentTypes(
        List<String> contentTypes)
    {
        this.contentTypes = contentTypes;
        return this;
    }

    public HttpRequestConfigBuilder<T> contentType(
        String contentType)
    {
        if (this.contentTypes == null)
        {
            this.contentTypes = new LinkedList<>();
        }
        this.contentTypes.add(contentType);
        return this;
    }

    public HttpRequestConfigBuilder<T> content(
        ValidatorConfig content)
    {
        this.content = content;
        return this;
    }

    public <C extends ConfigBuilder<HttpRequestConfigBuilder<T>, C>> C content(
        Function<Function<ValidatorConfig, HttpRequestConfigBuilder<T>>, C> content)
    {
        return content.apply(this::content);
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpRequestConfig(path, method, contentTypes, content));
    }
}
