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
package io.aklivity.zilla.config.binding.echo;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class EchoOptionsConfig extends OptionsConfig
{
    public final ModelConfig value;

    public static EchoOptionsConfigBuilder<EchoOptionsConfig> builder()
    {
        return new EchoOptionsConfigBuilder<>(EchoOptionsConfig.class::cast);
    }

    public static <T> EchoOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new EchoOptionsConfigBuilder<>(mapper);
    }

    EchoOptionsConfig(
        ModelConfig value,
        List<NamedConfig> refs)
    {
        super(List.of(), null, refs);
        this.value = value;
    }
}
