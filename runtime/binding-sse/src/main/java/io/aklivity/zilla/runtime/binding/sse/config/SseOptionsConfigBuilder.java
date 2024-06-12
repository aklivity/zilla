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
package io.aklivity.zilla.runtime.binding.sse.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class SseOptionsConfigBuilder<T> extends ConfigBuilder<T, SseOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private int retry;
    private List<SsePathConfig> paths;

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

    public SseOptionsConfigBuilder<T> paths(
        List<SsePathConfig> paths)
    {
        if (paths == null)
        {
            paths = new LinkedList<>();
        }
        this.paths = paths;
        return this;
    }

    public SseOptionsConfigBuilder<T> path(
        SsePathConfig path)
    {
        if (this.paths == null)
        {
            this.paths = new LinkedList<>();
        }
        this.paths.add(path);
        return this;
    }

    public SsePathConfigBuilder<SseOptionsConfigBuilder<T>> path()
    {
        return new SsePathConfigBuilder<>(this::path);
    }

    @Override
    public T build()
    {
        return mapper.apply(new SseOptionsConfig(retry, paths));
    }

}
