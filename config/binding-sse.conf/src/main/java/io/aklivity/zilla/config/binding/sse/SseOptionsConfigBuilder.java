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
package io.aklivity.zilla.config.binding.sse;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class SseOptionsConfigBuilder<T> extends ConfigBuilder<T, SseOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private int retry;
    private List<SseRequestConfig> requests;

    SseOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SseOptionsConfigBuilder<T>> thisType()
    {
        return (Class<SseOptionsConfigBuilder<T>>) getClass();
    }

    public SseOptionsConfigBuilder<T> retry(
        int retry)
    {

        this.retry = retry;
        return this;
    }

    public SseOptionsConfigBuilder<T> requests(
        List<SseRequestConfig> requests)
    {
        if (requests == null)
        {
            requests = new LinkedList<>();
        }
        this.requests = requests;
        return this;
    }

    public SseOptionsConfigBuilder<T> request(
        SseRequestConfig request)
    {
        if (this.requests == null)
        {
            this.requests = new LinkedList<>();
        }
        this.requests.add(request);
        return this;
    }

    public SsePathConfigBuilder<SseOptionsConfigBuilder<T>> request()
    {
        return new SsePathConfigBuilder<>(this::request);
    }

    @Override
    public T build()
    {
        List<ModelConfig> models = resolveModels(requests);
        return mapper.apply(new SseOptionsConfig(retry, requests, models, refs(models)));
    }

    private static List<ModelConfig> resolveModels(
        List<SseRequestConfig> requests)
    {
        return requests != null && !requests.isEmpty()
            ? requests.stream()
            .flatMap(path ->
                Stream.of(path.content)
                    .filter(Objects::nonNull))
            .collect(Collectors.toList())
            : emptyList();
    }

    private static List<NamedConfig> refs(
        List<ModelConfig> models)
    {
        List<NamedConfig> refs = new ArrayList<>();
        for (ModelConfig model : models)
        {
            refs.addAll(model.refs());
        }
        return refs;
    }
}
