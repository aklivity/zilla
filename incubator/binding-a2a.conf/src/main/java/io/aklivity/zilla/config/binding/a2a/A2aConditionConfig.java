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

public final class A2aConditionConfig extends ConditionConfig
{
    public final List<String> agent;
    public final List<String> skill;

    A2aConditionConfig(
        List<String> agent,
        List<String> skill)
    {
        this.agent = agent;
        this.skill = skill;
    }

    public static A2aConditionConfigBuilder<A2aConditionConfig> builder()
    {
        return new A2aConditionConfigBuilder<>(A2aConditionConfig.class::cast);
    }

    public static <T> A2aConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new A2aConditionConfigBuilder<>(mapper);
    }
}
