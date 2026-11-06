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
import io.aklivity.zilla.config.engine.ModelConfig;

public final class A2aHttpSkillConfigBuilder<T> extends ConfigBuilder<T, A2aHttpSkillConfigBuilder<T>>
{
    private final Function<A2aHttpSkillConfig, T> mapper;

    private String id;
    private String name;
    private String description;
    private List<String> examples;
    private List<String> tags;
    private ModelConfig input;
    private ModelConfig output;

    A2aHttpSkillConfigBuilder(
        Function<A2aHttpSkillConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aHttpSkillConfigBuilder<T>> thisType()
    {
        return (Class<A2aHttpSkillConfigBuilder<T>>) getClass();
    }

    public A2aHttpSkillConfigBuilder<T> id(
        String id)
    {
        this.id = id;
        return this;
    }

    public A2aHttpSkillConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public A2aHttpSkillConfigBuilder<T> description(
        String description)
    {
        this.description = description;
        return this;
    }

    public A2aHttpSkillConfigBuilder<T> examples(
        List<String> examples)
    {
        this.examples = examples;
        return this;
    }

    public A2aHttpSkillConfigBuilder<T> example(
        String example)
    {
        if (examples == null)
        {
            examples = new LinkedList<>();
        }
        examples.add(example);
        return this;
    }

    public A2aHttpSkillConfigBuilder<T> tags(
        List<String> tags)
    {
        this.tags = tags;
        return this;
    }

    public A2aHttpSkillConfigBuilder<T> tag(
        String tag)
    {
        if (tags == null)
        {
            tags = new LinkedList<>();
        }
        tags.add(tag);
        return this;
    }

    public A2aHttpSkillConfigBuilder<T> input(
        ModelConfig input)
    {
        this.input = input;
        return this;
    }

    public A2aHttpSkillConfigBuilder<T> output(
        ModelConfig output)
    {
        this.output = output;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aHttpSkillConfig(
            id,
            name != null ? name : id,
            description,
            examples != null ? examples : List.of(),
            tags != null ? tags : List.of(),
            input,
            output));
    }
}
