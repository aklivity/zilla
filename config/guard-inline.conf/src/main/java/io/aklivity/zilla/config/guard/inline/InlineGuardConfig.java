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
package io.aklivity.zilla.config.guard.inline;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.GuardConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class InlineGuardConfig extends GuardConfig
{
    public static InlineGuardConfigBuilder<InlineGuardConfig> builder()
    {
        return new InlineGuardConfigBuilder<>(InlineGuardConfig.class::cast);
    }

    public static <T> InlineGuardConfigBuilder<T> builder(
        Function<GuardConfig, T> mapper)
    {
        return new InlineGuardConfigBuilder<>(mapper);
    }

    InlineGuardConfig(
        String namespace,
        String name,
        String type,
        String kind,
        String store,
        OptionsConfig options)
    {
        super(namespace, name, type, kind, store, options);
    }
}
