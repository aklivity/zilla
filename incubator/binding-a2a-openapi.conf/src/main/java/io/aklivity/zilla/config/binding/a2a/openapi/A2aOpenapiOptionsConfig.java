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

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class A2aOpenapiOptionsConfig extends OptionsConfig
{
    public static final double THRESHOLD_DEFAULT = 0.8;

    public final A2aOpenapiAgentConfig agent;
    public final A2aOpenapiAuthorizationConfig authorization;
    public final List<A2aOpenapiSpecificationConfig> specs;
    public final List<A2aOpenapiSkillConfig> skills;
    public final String embedding;
    public final String store;
    public final double threshold;

    public static A2aOpenapiOptionsConfigBuilder<A2aOpenapiOptionsConfig> builder()
    {
        return new A2aOpenapiOptionsConfigBuilder<>(A2aOpenapiOptionsConfig.class::cast);
    }

    public static <T> A2aOpenapiOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new A2aOpenapiOptionsConfigBuilder<>(mapper);
    }

    A2aOpenapiOptionsConfig(
        A2aOpenapiAgentConfig agent,
        A2aOpenapiAuthorizationConfig authorization,
        List<A2aOpenapiSpecificationConfig> specs,
        List<A2aOpenapiSkillConfig> skills,
        String embedding,
        String store,
        double threshold)
    {
        super(null, null);
        this.agent = agent;
        this.authorization = authorization;
        this.specs = specs;
        this.skills = skills;
        this.embedding = embedding;
        this.store = store;
        this.threshold = threshold;
    }
}
