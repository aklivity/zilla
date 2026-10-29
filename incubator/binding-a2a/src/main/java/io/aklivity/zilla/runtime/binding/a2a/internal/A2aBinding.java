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
package io.aklivity.zilla.runtime.binding.a2a.internal;

import io.aklivity.zilla.config.binding.a2a.A2aConditionConfig;
import io.aklivity.zilla.config.binding.a2a.A2aDiscoveryConfig;
import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.ConfigException;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class A2aBinding implements Binding
{
    public static final String TYPE = "a2a";

    private final A2aConfiguration config;

    A2aBinding(
        A2aConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return A2aBinding.TYPE;
    }

    @Override
    public void validate(
        BindingConfig binding) throws ConfigException
    {
        final A2aOptionsConfig options = (A2aOptionsConfig) binding.options;
        final A2aDiscoveryConfig discovery = options != null ? options.discovery : null;

        if (discovery != null)
        {
            if (options.cache == null)
            {
                throw new ConfigException(String.format("%s: discovery requires options.cache", binding.name));
            }

            final boolean routed = binding.routes.stream()
                .flatMap(route -> route.when.stream())
                .map(A2aConditionConfig.class::cast)
                .filter(condition -> condition.agent != null)
                .anyMatch(condition -> condition.agent.contains(discovery.agent));

            if (routed)
            {
                throw new ConfigException(String.format(
                    "%s: discovery agent %s collides with a routed agent", binding.name, discovery.agent));
            }
        }
    }

    @Override
    public A2aBindingContext supply(
        EngineContext context)
    {
        return new A2aBindingContext(config, context);
    }
}
