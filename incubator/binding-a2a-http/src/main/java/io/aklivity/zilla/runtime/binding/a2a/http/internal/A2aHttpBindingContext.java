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
package io.aklivity.zilla.runtime.binding.a2a.http.internal;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.stream.A2aHttpProxyFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

final class A2aHttpBindingContext implements BindingContext
{
    private final A2aHttpProxyFactory factory;

    A2aHttpBindingContext(
        A2aHttpConfiguration config,
        EngineContext context)
    {
        this.factory = new A2aHttpProxyFactory(config, context);
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        BindingHandler handler = null;

        if (binding.kind == KindConfig.PROXY)
        {
            factory.attach(binding);
            handler = factory;
        }

        return handler;
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        if (binding.kind == KindConfig.PROXY)
        {
            factory.detach(binding.id);
        }
    }
}
