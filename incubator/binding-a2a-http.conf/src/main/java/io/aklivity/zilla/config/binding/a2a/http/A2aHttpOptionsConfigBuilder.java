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
package io.aklivity.zilla.config.binding.a2a.http;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class A2aHttpOptionsConfigBuilder<T> extends ConfigBuilder<T, A2aHttpOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String embedding;
    private String store;
    private double threshold = A2aHttpOptionsConfig.THRESHOLD_DEFAULT;
    private A2aHttpCardConfig card;
    private A2aHttpAuthorizationConfig authorization;
    private List<A2aHttpSkillConfig> skills;

    A2aHttpOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aHttpOptionsConfigBuilder<T>> thisType()
    {
        return (Class<A2aHttpOptionsConfigBuilder<T>>) getClass();
    }

    public A2aHttpOptionsConfigBuilder<T> embedding(
        String embedding)
    {
        this.embedding = embedding;
        return this;
    }

    public A2aHttpOptionsConfigBuilder<T> store(
        String store)
    {
        this.store = store;
        return this;
    }

    public A2aHttpOptionsConfigBuilder<T> threshold(
        double threshold)
    {
        this.threshold = threshold;
        return this;
    }

    public A2aHttpOptionsConfigBuilder<T> card(
        A2aHttpCardConfig card)
    {
        this.card = card;
        return this;
    }

    public A2aHttpCardConfigBuilder<A2aHttpOptionsConfigBuilder<T>> card()
    {
        return new A2aHttpCardConfigBuilder<>(this::card);
    }

    public A2aHttpOptionsConfigBuilder<T> authorization(
        A2aHttpAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public A2aHttpAuthorizationConfigBuilder<A2aHttpOptionsConfigBuilder<T>> authorization()
    {
        return new A2aHttpAuthorizationConfigBuilder<>(this::authorization);
    }

    public A2aHttpOptionsConfigBuilder<T> skills(
        List<A2aHttpSkillConfig> skills)
    {
        this.skills = skills;
        return this;
    }

    public A2aHttpOptionsConfigBuilder<T> skill(
        A2aHttpSkillConfig skill)
    {
        if (skills == null)
        {
            skills = new LinkedList<>();
        }
        skills.add(skill);
        return this;
    }

    public A2aHttpSkillConfigBuilder<A2aHttpOptionsConfigBuilder<T>> skill()
    {
        return new A2aHttpSkillConfigBuilder<>(this::skill);
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aHttpOptionsConfig(
            embedding, store, threshold, card, authorization, skills != null ? skills : List.of()));
    }
}
