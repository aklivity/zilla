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
package io.aklivity.zilla.config.binding.a2a.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class A2aOpenapiOptionsConfigBuilder<T> extends ConfigBuilder<T, A2aOpenapiOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private A2aOpenapiAgentConfig agent;
    private A2aOpenapiAuthorizationConfig authorization;
    private List<A2aOpenapiSpecificationConfig> specs;
    private List<A2aOpenapiSkillConfig> skills;
    private String embedding;
    private String store;
    private Double threshold;

    A2aOpenapiOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aOpenapiOptionsConfigBuilder<T>> thisType()
    {
        return (Class<A2aOpenapiOptionsConfigBuilder<T>>) getClass();
    }

    public A2aOpenapiAgentConfigBuilder<A2aOpenapiOptionsConfigBuilder<T>> agent()
    {
        return new A2aOpenapiAgentConfigBuilder<>(this::agent);
    }

    public A2aOpenapiOptionsConfigBuilder<T> agent(
        A2aOpenapiAgentConfig agent)
    {
        this.agent = agent;
        return this;
    }

    public A2aOpenapiOptionsConfigBuilder<T> authorization(
        A2aOpenapiAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public A2aOpenapiSpecificationConfigBuilder<A2aOpenapiOptionsConfigBuilder<T>> spec()
    {
        return new A2aOpenapiSpecificationConfigBuilder<>(this::spec);
    }

    public A2aOpenapiOptionsConfigBuilder<T> spec(
        A2aOpenapiSpecificationConfig spec)
    {
        if (specs == null)
        {
            specs = new ArrayList<>();
        }
        specs.add(spec);
        return this;
    }

    public A2aOpenapiSkillConfigBuilder<A2aOpenapiOptionsConfigBuilder<T>> skill()
    {
        return new A2aOpenapiSkillConfigBuilder<>(this::skill);
    }

    public A2aOpenapiOptionsConfigBuilder<T> skill(
        A2aOpenapiSkillConfig skill)
    {
        if (skills == null)
        {
            skills = new ArrayList<>();
        }
        skills.add(skill);
        return this;
    }

    public A2aOpenapiOptionsConfigBuilder<T> embedding(
        String embedding)
    {
        this.embedding = embedding;
        return this;
    }

    public A2aOpenapiOptionsConfigBuilder<T> store(
        String store)
    {
        this.store = store;
        return this;
    }

    public A2aOpenapiOptionsConfigBuilder<T> threshold(
        Double threshold)
    {
        this.threshold = threshold;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aOpenapiOptionsConfig(
            agent,
            authorization,
            specs,
            skills != null ? skills : List.of(),
            embedding,
            store,
            threshold != null ? threshold : A2aOpenapiOptionsConfig.THRESHOLD_DEFAULT));
    }
}
