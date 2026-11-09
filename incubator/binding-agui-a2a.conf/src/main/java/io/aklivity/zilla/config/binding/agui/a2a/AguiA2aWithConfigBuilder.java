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

package io.aklivity.zilla.config.binding.agui.a2a;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class AguiA2aWithConfigBuilder<T> extends ConfigBuilder<T, AguiA2aWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private String agent;
    private String identity;

    AguiA2aWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AguiA2aWithConfigBuilder<T>> thisType()
    {
        return (Class<AguiA2aWithConfigBuilder<T>>) getClass();
    }

    public AguiA2aWithConfigBuilder<T> agent(
        String agent)
    {
        this.agent = agent;
        return this;
    }

    public AguiA2aWithConfigBuilder<T> identity(
        String identity)
    {
        this.identity = identity;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new AguiA2aWithConfig(agent, identity));
    }
}
