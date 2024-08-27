/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class RisingwaveConditionConfigBuilder<T> extends ConfigBuilder<T, RisingwaveConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private List<String> commands;

    RisingwaveConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<RisingwaveConditionConfigBuilder<T>> thisType()
    {
        return (Class<RisingwaveConditionConfigBuilder<T>>) getClass();
    }

    public RisingwaveConditionConfigBuilder<T> command(
        String command)
    {
        if (commands == null)
        {
            commands = new ArrayList<>();
        }
        commands.add(command);
        return this;
    }

    public T build()
    {
        return mapper.apply(new RisingwaveConditionConfig(commands));
    }
}
