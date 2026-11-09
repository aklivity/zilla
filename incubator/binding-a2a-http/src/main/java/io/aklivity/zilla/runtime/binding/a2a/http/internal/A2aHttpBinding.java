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

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class A2aHttpBinding implements Binding
{
    public static final String TYPE = "a2a-http";

    private final A2aHttpConfiguration config;

    A2aHttpBinding(
        A2aHttpConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return A2aHttpBinding.TYPE;
    }

    @Override
    public A2aHttpBindingContext supply(
        EngineContext context)
    {
        return new A2aHttpBindingContext(config, context);
    }
}
