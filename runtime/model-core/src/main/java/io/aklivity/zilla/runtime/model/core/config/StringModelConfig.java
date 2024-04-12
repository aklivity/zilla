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
package io.aklivity.zilla.runtime.model.core.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class StringModelConfig extends ModelConfig
{
    public final String encoding;
    public final String pattern;
    public final int maxLength;
    public final int minLength;

    public StringModelConfig(
        String encoding,
        String pattern,
        int maxLength,
        int minLength)
    {
        super("string");
        this.encoding = encoding;
        this.pattern = pattern;
        this.maxLength = maxLength;
        this.minLength = minLength;

    }

    public static <T> StringModelConfigBuilder<T> builder(
        Function<ModelConfig, T> mapper)
    {
        return new StringModelConfigBuilder<>(mapper::apply);
    }

    public static StringModelConfigBuilder<StringModelConfig> builder()
    {
        return new StringModelConfigBuilder<>(StringModelConfig.class::cast);
    }
}
