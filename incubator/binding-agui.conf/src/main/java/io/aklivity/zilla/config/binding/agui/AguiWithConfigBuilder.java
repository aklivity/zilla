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
package io.aklivity.zilla.config.binding.agui;

import static io.aklivity.zilla.config.engine.WithConfig.NO_COMPOSITE_ID;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class AguiWithConfigBuilder<T> extends ConfigBuilder<T, AguiWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private long compositeId = NO_COMPOSITE_ID;
    private String agent;
    private String path;

    AguiWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AguiWithConfigBuilder<T>> thisType()
    {
        return (Class<AguiWithConfigBuilder<T>>) getClass();
    }

    public AguiWithConfigBuilder<T> compositeId(
        long compositeId)
    {
        this.compositeId = compositeId;
        return this;
    }

    public AguiWithConfigBuilder<T> agent(
        String agent)
    {
        this.agent = agent;
        return this;
    }

    public AguiWithConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new AguiWithConfig(compositeId, agent, path));
    }
}
