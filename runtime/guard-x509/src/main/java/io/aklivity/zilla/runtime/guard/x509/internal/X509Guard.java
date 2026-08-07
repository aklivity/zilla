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
package io.aklivity.zilla.runtime.guard.x509.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.function.LongToIntFunction;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.Guard;

public final class X509Guard implements Guard
{
    public static final String NAME = "x509";

    private final Configuration config;
    private final X509GuardContext[] contexts;

    X509Guard(
        Configuration config)
    {
        this.config = config;
        this.contexts = new X509GuardContext[ENGINE_WORKERS.get(config)];
    }

    @Override
    public String name()
    {
        return X509Guard.NAME;
    }

    @Override
    public X509GuardContext supply(
        EngineContext context)
    {
        X509GuardContext guard = new X509GuardContext(config, context);
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

        return (session, resolve) -> verify(guardIndex, guardId, indexOf.applyAsInt(session), session, roles);
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
        Objects.requireNonNull(indexOf);

        final long guardId = config.id;

        final int guardIndex = indexOf.applyAsInt(guardId);

        return (session, name) -> attribute(guardIndex, guardId, indexOf.applyAsInt(session), session, name);
    }

    private boolean verify(
        int guardIndex,
        long guardId,
        int sessionIndex,
        long sessionId,
        List<String> roles)
    {
        if (sessionIndex != guardIndex)
        {
            VarHandle.fullFence();
        }
        final X509GuardContext context = contexts[sessionIndex];
        final X509GuardHandler handler = context != null ? context.handler(guardId) : null;
        return handler != null && handler.verify(sessionId, roles);
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
        final X509GuardContext context = contexts[sessionIndex];
        final X509GuardHandler handler = context != null ? context.handler(guardId) : null;
        return handler != null ? handler.identity(sessionId) : null;
    }

    private String attribute(
        int guardIndex,
        long guardId,
        int sessionIndex,
        long sessionId,
        String name)
    {
        if (sessionIndex != guardIndex)
        {
            VarHandle.fullFence();
        }
        final X509GuardContext context = contexts[sessionIndex];
        final X509GuardHandler handler = context != null ? context.handler(guardId) : null;
        return handler != null ? handler.attribute(sessionId, name) : null;
    }
}
