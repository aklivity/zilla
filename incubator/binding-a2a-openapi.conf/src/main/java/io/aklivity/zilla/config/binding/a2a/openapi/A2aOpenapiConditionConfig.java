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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;

public final class A2aOpenapiConditionConfig extends ConditionConfig
{
    public final String skill;

    public static A2aOpenapiConditionConfigBuilder<A2aOpenapiConditionConfig> builder()
    {
        return new A2aOpenapiConditionConfigBuilder<>(A2aOpenapiConditionConfig.class::cast);
    }

    public static <T> A2aOpenapiConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new A2aOpenapiConditionConfigBuilder<>(mapper);
    }

    A2aOpenapiConditionConfig(
        String skill)
    {
        this.skill = skill;
    }
}
