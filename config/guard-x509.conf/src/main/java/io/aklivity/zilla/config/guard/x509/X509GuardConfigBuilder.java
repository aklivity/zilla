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
package io.aklivity.zilla.config.guard.x509;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.GuardConfig;
import io.aklivity.zilla.config.engine.GuardConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.guard.x509.internal.X509GuardInfo;

public final class X509GuardConfigBuilder<T> extends GuardConfigBuilder<T, X509GuardConfigBuilder<T>>
{
    X509GuardConfigBuilder(
        Function<GuardConfig, T> mapper)
    {
        super(mapper);
        type(X509GuardInfo.TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<X509GuardConfigBuilder<T>> thisType()
    {
        return (Class<X509GuardConfigBuilder<T>>) getClass();
    }

    public X509OptionsConfigBuilder<X509GuardConfigBuilder<T>> options()
    {
        return new X509OptionsConfigBuilder<>(this::options);
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
        return new X509GuardConfig(namespace, name, type, kind, store, options);
    }
}
