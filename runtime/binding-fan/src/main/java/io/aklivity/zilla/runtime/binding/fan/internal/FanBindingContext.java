/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.fan.internal;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.Collections.singletonMap;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.fan.internal.stream.FanServerFactory;
import io.aklivity.zilla.runtime.binding.fan.internal.stream.FanStreamFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

final class FanBindingContext implements BindingContext
{
    private final Map<KindConfig, FanStreamFactory> factories;

    FanBindingContext(
        FanConfiguration config,
        EngineContext context)
    {
        this.factories = singletonMap(SERVER, new FanServerFactory(config, context));
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        FanStreamFactory factory = factories.get(binding.kind);
        if (factory != null)
        {
            factory.attach(binding);
        }
        return factory;
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        FanStreamFactory factory = factories.get(binding.kind);
        if (factory != null)
        {
            factory.detach(binding.id);
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), factories);
    }
}
