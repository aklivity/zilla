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
import io.aklivity.zilla.config.engine.GuardConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.guard.inline.internal.InlineGuardInfo;

public final class InlineGuardConfigBuilder<T> extends GuardConfigBuilder<T, InlineGuardConfigBuilder<T>>
{
    InlineGuardConfigBuilder(
        Function<GuardConfig, T> mapper)
    {
        super(mapper);
        type(InlineGuardInfo.TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<InlineGuardConfigBuilder<T>> thisType()
    {
        return (Class<InlineGuardConfigBuilder<T>>) getClass();
    }

    public InlineOptionsConfigBuilder<InlineGuardConfigBuilder<T>> options()
    {
        return new InlineOptionsConfigBuilder<>(this::options);
    }

    @Override
    protected GuardConfig newGuard(
        String namespace,
        String name,
        String type,
        String kind,
        String store,
        OptionsConfig options)
    {
        return new InlineGuardConfig(namespace, name, type, kind, store, options);
    }
}
