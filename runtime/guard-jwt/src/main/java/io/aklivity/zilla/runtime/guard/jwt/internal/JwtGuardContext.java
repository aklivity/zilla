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
package io.aklivity.zilla.runtime.guard.jwt.internal;

import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.GuardConfig;
import io.aklivity.zilla.config.guard.jwt.JwtOptionsConfig;
import io.aklivity.zilla.runtime.common.jwt.JweKeyManager;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManagerFactory;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

final class JwtGuardContext implements GuardContext
{
    private final Long2ObjectHashMap<JwtGuardHandler> handlersById;
    private final LongSupplier supplyAuthorizedId;
    private final EngineContext context;
    private final JwtGuard parent;

    JwtGuardContext(
        Configuration config,
        EngineContext context,
        JwtGuard parent)
    {
        this.handlersById = new Long2ObjectHashMap<>();
        this.context = context;
        this.supplyAuthorizedId = context::supplyAuthorizedId;
        this.parent = parent;
    }

    @Override
    public JwtGuardHandler attach(
        GuardConfig guard)
    {
        JwtOptionsConfig options = (JwtOptionsConfig) guard.options;
        long guardId = guard.id;
        LongFunction<String> credentialer = sessionId -> parent.credentials(context.indexOf(sessionId), guardId, sessionId);
        JweKeyManager encryption = options.vault != null ? newEncryption(options) : null;
        JwtGuardHandler handler = new JwtGuardHandler(options, context, supplyAuthorizedId, credentialer, encryption);
        handlersById.put(guard.id, handler);
        return handler;
    }

    private JweKeyManager newEncryption(
        JwtOptionsConfig options)
    {
        VaultHandler vault = context.supplyVault(options.vault.id);
        SecretKeyManagerFactory factory = vault != null ? vault.initSecretKeys(options.secrets) : null;

        if (factory == null)
        {
            throw new IllegalStateException("Unable to resolve secrets from vault: " + options.vault.name);
        }

        return new VaultJweKeyManager(factory.getSecretKeyManager(), System::currentTimeMillis);
    }

    @Override
    public void detach(
        GuardConfig guard)
    {
        handlersById.remove(guard.id);
    }

    JwtGuardHandler handler(
        long guardId)
    {
        return handlersById.get(guardId);
    }
}
