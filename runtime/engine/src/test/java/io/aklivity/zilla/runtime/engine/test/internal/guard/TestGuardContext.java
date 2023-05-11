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
package io.aklivity.zilla.runtime.engine.test.internal.guard;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;

public final class TestGuardContext implements GuardContext
{
    private Long2ObjectHashMap<TestGuardHandler> handlersById;

    public TestGuardContext(
        EngineContext context)
    {
        this.handlersById = new Long2ObjectHashMap<>();
    }

    @Override
    public TestGuardHandler attach(
        GuardConfig guard)
    {
        TestGuardConfig config = new TestGuardConfig(guard);
        TestGuardHandler handler = new TestGuardHandler(config);
        handlersById.put(guard.id, handler);
        return handler;
    }

    @Override
    public void detach(
        GuardConfig guard)
    {
        handlersById.remove(guard.id);
    }

    TestGuardHandler handler(
        long guardId)
    {
        return handlersById.get(guardId);
    }
}
