/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.inline.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class InlineOptionsConfig extends OptionsConfig
{
    public final List<InlineSchemaConfig> subjects;

    public static InlineOptionsConfigBuilder<InlineOptionsConfig> builder()
    {
        return new InlineOptionsConfigBuilder<>(InlineOptionsConfig.class::cast);
    }

    public static <T> InlineOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new InlineOptionsConfigBuilder<>(mapper);
    }

    InlineOptionsConfig(
        List<InlineSchemaConfig> subjects)
    {
        this.subjects = subjects;
    }
}
