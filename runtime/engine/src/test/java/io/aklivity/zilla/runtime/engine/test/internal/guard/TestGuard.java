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
package io.aklivity.zilla.runtime.engine.test.internal.guard;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;

import java.net.URL;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.function.LongToIntFunction;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;

public final class TestGuard implements Guard
{
    public static final String NAME = "test";

    private final TestGuardContext[] contexts;

    public TestGuard(
        Configuration config)
    {
        this.contexts = new TestGuardContext[ENGINE_WORKERS.get(config)];
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("test.schema.patch.json");
    }

    @Override
    public GuardContext supply(
        EngineContext context)
    {
        TestGuardContext guard = new TestGuardContext(context);
        contexts[context.index()] = guard;
        return guard;
    }

    @Override
    public LongPredicate verifier(
        LongToIntFunction indexOf,
        GuardedConfig config)
    {
        long guardId = config.id;
        List<String> roles = config.roles;
        return sessionId -> verify(guardId, indexOf.applyAsInt(sessionId), sessionId, roles);
    }

    private boolean verify(
        long guardId,
        int index,
        long sessionId,
        List<String> roles)
    {
        TestGuardContext context = contexts[index];
        TestGuardHandler handler = context.handler(guardId);
        return handler.verify(sessionId, roles);
    }
}
