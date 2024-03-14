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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpKafkaWithFetchFilterConfigBuilder<T> extends
    ConfigBuilder<T, HttpKafkaWithFetchFilterConfigBuilder<T>>
{
    private final Function<HttpKafkaWithFetchFilterConfig, T> mapper;
    private String key;
    private List<HttpKafkaWithFetchFilterHeaderConfig> headers;


    HttpKafkaWithFetchFilterConfigBuilder(
        Function<HttpKafkaWithFetchFilterConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaWithFetchFilterConfigBuilder<T> key(
        String key)
    {
        this.key = key;
        return this;
    }

    public HttpKafkaWithFetchFilterConfigBuilder<T> headers(
        List<HttpKafkaWithFetchFilterHeaderConfig> headers)
    {
        this.headers = headers;
        return this;
    }

    public HttpKafkaWithFetchFilterConfigBuilder<T> header(
        String name,
        String value)
    {
        if (headers == null)
        {
            headers = new ArrayList<>();
        }

        headers.add(HttpKafkaWithFetchFilterHeaderConfig.builder()
                        .name(name)
                        .value(value)
                        .build());

        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaWithFetchFilterConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaWithFetchFilterConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpKafkaWithFetchFilterConfig(key, headers));
    }
}
