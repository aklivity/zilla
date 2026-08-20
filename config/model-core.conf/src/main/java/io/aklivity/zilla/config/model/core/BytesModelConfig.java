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
package io.aklivity.zilla.config.model.core;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.ValidateConfig;

public final class BytesModelConfig extends ModelConfig
{
    BytesModelConfig(
        ValidateConfig validate,
        Map<String, Config> extensions,
        List<NamedConfig> refs)
    {
        super("bytes", null, validate, extensions, refs);
    }

    public static <T> BytesModelConfigBuilder<T> builder(
        Function<ModelConfig, T> mapper)
    {
        return new BytesModelConfigBuilder<>(mapper::apply);
    }

    public static BytesModelConfigBuilder<BytesModelConfig> builder()
    {
        return new BytesModelConfigBuilder<>(BytesModelConfig.class::cast);
    }
}
