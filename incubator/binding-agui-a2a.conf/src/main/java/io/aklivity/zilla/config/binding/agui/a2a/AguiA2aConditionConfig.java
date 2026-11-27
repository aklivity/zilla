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

import io.aklivity.zilla.config.engine.ConditionConfig;

public final class AguiA2aConditionConfig extends ConditionConfig
{
    public final String agent;

    public static AguiA2aConditionConfigBuilder<AguiA2aConditionConfig> builder()
    {
        return new AguiA2aConditionConfigBuilder<>(AguiA2aConditionConfig.class::cast);
    }

    public static <T> AguiA2aConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new AguiA2aConditionConfigBuilder<>(mapper);
    }

    AguiA2aConditionConfig(
        String agent)
    {
        this.agent = agent;
    }
}
