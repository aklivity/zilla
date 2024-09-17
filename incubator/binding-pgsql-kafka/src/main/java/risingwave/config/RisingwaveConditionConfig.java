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
package risingwave.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.risingwave.internal.config.RisingwaveCommandType;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public final class RisingwaveConditionConfig extends ConditionConfig
{
    public final List<RisingwaveCommandType> commands;

    public static RisingwaveConditionConfigBuilder<RisingwaveConditionConfig> builder()
    {
        return new RisingwaveConditionConfigBuilder<>(RisingwaveConditionConfig.class::cast);
    }

    public static <T> RisingwaveConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new RisingwaveConditionConfigBuilder<>(mapper);
    }

    RisingwaveConditionConfig(
        List<RisingwaveCommandType> commands)
    {
        this.commands = commands;
    }
}
