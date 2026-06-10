/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.router;

import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.RouterConfig;
import io.aklivity.zilla.runtime.engine.router.RouteableContext;
import io.aklivity.zilla.runtime.engine.router.RouterContext;

public final class TestRouterContext implements RouterContext
{
    private final BindingHandler streamFactory;

    public TestRouterContext(
        RouteableContext context)
    {
        this.streamFactory = context.streamFactory();
    }

    @Override
    public BindingHandler attach(
        RouterConfig config)
    {
        return streamFactory;
    }

    @Override
    public void detach(
        long routerId)
    {
    }
}
