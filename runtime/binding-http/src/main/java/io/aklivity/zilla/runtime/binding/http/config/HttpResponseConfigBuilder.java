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
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public class HttpResponseConfigBuilder<T> extends ConfigBuilder<T, HttpResponseConfigBuilder<T>>
{
    private final Function<HttpResponseConfig, T> mapper;

    private List<String> status;
    private List<String> contentType;
    private List<HttpParamConfig> headers;
    private ModelConfig content;

    HttpResponseConfigBuilder(
        Function<HttpResponseConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpResponseConfigBuilder<T>> thisType()
    {
        return (Class<HttpResponseConfigBuilder<T>>) getClass();
    }

    public HttpResponseConfigBuilder<T> status(
        int status)
    {
        if (this.status == null)
        {
            this.status = new LinkedList<>();
        }
        this.status.add(String.valueOf(status));
        return this;
    }

    public HttpResponseConfigBuilder<T> contentType(
        String contentType)
    {
        if (this.contentType == null)
        {
            this.contentType = new LinkedList<>();
        }
        this.contentType.add(contentType);
        return this;
    }

    public HttpResponseConfigBuilder<T> headers(
        List<HttpParamConfig> headers)
    {
        this.headers = headers;
        return this;
    }

    public HttpResponseConfigBuilder<T> header(
        HttpParamConfig header)
    {
        if (this.headers == null)
        {
            this.headers = new LinkedList<>();
        }
        this.headers.add(header);
        return this;
    }

    public HttpParamConfigBuilder<HttpResponseConfigBuilder<T>> header()
    {
        return new HttpParamConfigBuilder<>(this::header);
    }


    public HttpResponseConfigBuilder<T> content(
        ModelConfig content)
    {
        this.content = content;
        return this;
    }

    public <C extends ConfigBuilder<HttpResponseConfigBuilder<T>, C>> C content(
        Function<Function<ModelConfig, HttpResponseConfigBuilder<T>>, C> content)
    {
        return content.apply(this::content);
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpResponseConfig(status, contentType, headers, content));
    }
}
