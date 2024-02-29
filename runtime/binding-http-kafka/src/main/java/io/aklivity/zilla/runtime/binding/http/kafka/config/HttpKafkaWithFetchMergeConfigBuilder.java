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
package io.aklivity.zilla.runtime.binding.http.kafka.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpKafkaWithFetchMergeConfigBuilder<T> extends
    ConfigBuilder<T, HttpKafkaWithFetchMergeConfigBuilder<T>>
{
    private final Function<HttpKafkaWithFetchMergeConfig, T> mapper;
    private String contentType;
    private String initial;
    private String path;


    HttpKafkaWithFetchMergeConfigBuilder(
        Function<HttpKafkaWithFetchMergeConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaWithFetchMergeConfigBuilder<T> contentType(
        String contentType)
    {
        this.contentType = contentType;
        return this;
    }

    public HttpKafkaWithFetchMergeConfigBuilder<T> initial(
        String initial)
    {
        this.initial = initial;
        return this;
    }

    public HttpKafkaWithFetchMergeConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaWithFetchMergeConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaWithFetchMergeConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpKafkaWithFetchMergeConfig(contentType, initial, path));
    }
}
