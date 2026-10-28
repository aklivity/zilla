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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class A2aHttpOptionsConfig extends OptionsConfig
{
    public static final double THRESHOLD_DEFAULT = 0.8;

    public final String embedding;
    public final String store;
    public final double threshold;
    public final A2aHttpCardConfig card;
    public final A2aHttpAuthorizationConfig authorization;
    public final List<A2aHttpSkillConfig> skills;

    public static A2aHttpOptionsConfigBuilder<A2aHttpOptionsConfig> builder()
    {
        return new A2aHttpOptionsConfigBuilder<>(A2aHttpOptionsConfig.class::cast);
    }

    public static <T> A2aHttpOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new A2aHttpOptionsConfigBuilder<>(mapper);
    }

    A2aHttpOptionsConfig(
        String embedding,
        String store,
        double threshold,
        A2aHttpCardConfig card,
        A2aHttpAuthorizationConfig authorization,
        List<A2aHttpSkillConfig> skills)
    {
        super(null, null);
        this.embedding = embedding;
        this.store = store;
        this.threshold = threshold;
        this.card = card;
        this.authorization = authorization;
        this.skills = skills;
    }
}
