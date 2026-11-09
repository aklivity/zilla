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
package io.aklivity.zilla.config.binding.a2a;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class A2aConditionConfigBuilder<T> extends ConfigBuilder<T, A2aConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private List<String> agent;
    private List<String> skill;

    public A2aConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public A2aConditionConfigBuilder<T> agent(
        List<String> agent)
    {
        this.agent = agent;
        return this;
    }

    public A2aConditionConfigBuilder<T> skill(
        List<String> skill)
    {
        this.skill = skill;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aConditionConfigBuilder<T>> thisType()
    {
        return (Class<A2aConditionConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aConditionConfig(agent, skill));
    }
}
