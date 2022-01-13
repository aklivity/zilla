/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.cog.echo.internal;

import static io.aklivity.zilla.runtime.engine.config.RoleConfig.SERVER;
import static java.util.Collections.singletonMap;

import java.util.Map;

import io.aklivity.zilla.runtime.cog.echo.internal.stream.EchoServerFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.cog.CogContext;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.RoleConfig;

final class EchoContext implements CogContext
{
    private final EchoRouter router;
    private final Map<RoleConfig, StreamFactory> factories;

    EchoContext(
        EchoConfiguration config,
        EngineContext context)
    {
        this.router = new EchoRouter();
        this.factories = singletonMap(SERVER, new EchoServerFactory(config, context, router));
    }

    @Override
    public StreamFactory attach(
        BindingConfig binding)
    {
        router.attach(binding);
        return factories.get(binding.kind);
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        router.detach(binding.id);
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), factories);
    }
}
