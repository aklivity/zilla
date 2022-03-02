/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.jwt.internal;

import java.net.URL;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.guard.jwt.internal.config.JwtOptionsConfig;

final class JwtContext implements GuardContext
{
    private final Function<String, URL> resolvePath;

    JwtContext(
        Configuration config,
        EngineContext context)
    {
        this.resolvePath = context::resolvePath;
    }

    @Override
    public JwtGuardHandler attach(
        GuardConfig guard)
    {
        JwtOptionsConfig options = (JwtOptionsConfig) guard.options;
        return new JwtGuardHandler(options, resolvePath);
    }

    @Override
    public void detach(
            GuardConfig vault)
    {
    }
}
