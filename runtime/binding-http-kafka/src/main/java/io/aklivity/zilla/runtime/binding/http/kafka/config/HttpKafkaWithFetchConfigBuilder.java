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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpKafkaWithFetchConfigBuilder<T> extends ConfigBuilder<T, HttpKafkaWithFetchConfigBuilder<T>>
{
    private final Function<HttpKafkaWithFetchConfig, T> mapper;
    private String topic;
    private List<HttpKafkaWithFetchFilterConfig> filters;
    private HttpKafkaWithFetchMergeConfig merge;

    HttpKafkaWithFetchConfigBuilder(
        Function<HttpKafkaWithFetchConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaWithFetchConfigBuilder<T> topic(
        String topic)
    {
        this.topic = topic;
        return this;
    }

    public HttpKafkaWithFetchConfigBuilder<T> filters(
         List<HttpKafkaWithFetchFilterConfig> filters)
    {
        this.filters = filters;
        return this;
    }

    public HttpKafkaWithFetchConfigBuilder<T> merged(
         HttpKafkaWithFetchMergeConfig merge)
    {
        this.merge = merge;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaWithFetchConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaWithFetchConfigBuilder<T>>) getClass();
    }


    @Override
    public T build()
    {
        return mapper.apply(new HttpKafkaWithFetchConfig(topic, filters, merge));
    }
}
