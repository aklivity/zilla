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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ModelConfig;

public final class A2aOpenapiSkillConfig extends Config
{
    public final String id;
    public final String name;
    public final String description;
    public final List<String> examples;
    public final List<String> tags;
    public final ModelConfig input;
    public final ModelConfig output;

    public static A2aOpenapiSkillConfigBuilder<A2aOpenapiSkillConfig> builder()
    {
        return new A2aOpenapiSkillConfigBuilder<>(A2aOpenapiSkillConfig.class::cast);
    }

    public static <T> A2aOpenapiSkillConfigBuilder<T> builder(
        Function<A2aOpenapiSkillConfig, T> mapper)
    {
        return new A2aOpenapiSkillConfigBuilder<>(mapper);
    }

    A2aOpenapiSkillConfig(
        String id,
        String name,
        String description,
        List<String> examples,
        List<String> tags,
        ModelConfig input,
        ModelConfig output)
    {
        this.id = id;
        this.name = name;
        this.description = description;
        this.examples = examples;
        this.tags = tags;
        this.input = input;
        this.output = output;
    }
}
