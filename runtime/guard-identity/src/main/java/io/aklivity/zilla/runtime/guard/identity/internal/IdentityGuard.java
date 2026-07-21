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
package io.aklivity.zilla.runtime.guard.identity.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;

import java.lang.invoke.VarHandle;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.function.LongToIntFunction;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public class IdentityGuard implements Guard
{
    public static final String NAME = "identity";

    private final IdentityGuardContext[] contexts;

    IdentityGuard(
        Configuration config)
    {

        this.contexts = new IdentityGuardContext[ENGINE_WORKERS.get(config)];
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/identity.schema.patch.json");
    }

    @Override
    public IdentityGuardContext supply(
        EngineContext context)
    {
        IdentityGuardContext guard = new IdentityGuardContext(context);
        contexts[context.index()] = guard;
        return guard;
    }

    @Override
    public LongObjectPredicate<UnaryOperator<String>> verifier(
        LongToIntFunction indexOf,
        GuardedConfig config)
    {
        Objects.requireNonNull(indexOf);

        final long guardId = config.id;
        final List<String> roles = config.roles;

        final int guardIndex = indexOf.applyAsInt(guardId);

        return (session, resolve) -> verify(guardIndex, guardId, indexOf.applyAsInt(session), session);
    }

    @Override
    public LongFunction<String> identifier(
        LongToIntFunction indexOf,
        GuardedConfig config)
    {
        Objects.requireNonNull(indexOf);

        final long guardId = config.id;

        final int guardIndex = indexOf.applyAsInt(guardId);

        return session -> identity(guardIndex, guardId, indexOf.applyAsInt(session), session);
    }

    @Override
    public LongObjectBiFunction<String, String> attributor(
        LongToIntFunction indexOf,
        GuardedConfig config)
    {
        return (session, name) -> "";
    }

    private String identity(
        int guardIndex,
        long guardId,
        int sessionIndex,
        long sessionId)
    {
        if (sessionIndex != guardIndex)
        {
            VarHandle.fullFence();
        }
        final IdentityGuardContext context = contexts[sessionIndex];
        final IdentityGuardHandler handler = context != null ? context.handler(guardId) : null;
        return handler != null ? handler.identity(sessionId) : null;
    }

    private boolean verify(
        int guardIndex,
        long guardId,
        int sessionIndex,
        long sessionId)
    {
        if (sessionIndex != guardIndex)
        {
            VarHandle.fullFence();
        }
        final IdentityGuardContext context = contexts[sessionIndex];
        final IdentityGuardHandler handler = context != null ? context.handler(guardId) : null;
        return handler != null && handler.verify(sessionId);
    }
}
