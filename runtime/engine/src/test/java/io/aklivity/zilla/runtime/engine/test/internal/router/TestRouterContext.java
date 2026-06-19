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
    private static final long NODE_MARKER = 0x0000_0001_0000_0000L;

    private final BindingHandler streamFactory;
    private final RouterContext engineRouter;

    public TestRouterContext(
        RouteableContext context)
    {
        this.streamFactory = context.streamFactory();
        this.engineRouter = context.engineRouter();
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

    @Override
    public long affinity(
        long bindingId,
        long affinity)
    {
        final long base = engineRouter.affinity(bindingId, affinity);
        return NODE_MARKER | (base & 0xFFFF_FFFFL);
    }

    @Override
    public boolean isLocalAffinity(
        long bindingId,
        long affinity)
    {
        return (affinity >>> 32) == 0L;
    }
}
