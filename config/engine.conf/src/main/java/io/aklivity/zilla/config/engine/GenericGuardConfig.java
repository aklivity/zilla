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
package io.aklivity.zilla.config.engine;

import java.util.function.Function;

public final class GenericGuardConfig extends GuardConfig
{
    public static GenericGuardConfigBuilder<GenericGuardConfig> builder()
    {
        return new GenericGuardConfigBuilder<>(GenericGuardConfig.class::cast);
    }

    public static <T> GenericGuardConfigBuilder<T> builder(
        Function<GuardConfig, T> mapper)
    {
        return new GenericGuardConfigBuilder<>(mapper);
    }

    GenericGuardConfig(
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
