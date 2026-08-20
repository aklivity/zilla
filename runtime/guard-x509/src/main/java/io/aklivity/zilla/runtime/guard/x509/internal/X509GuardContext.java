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

import java.util.function.LongSupplier;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.GuardConfig;
import io.aklivity.zilla.config.guard.x509.X509OptionsConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;

final class X509GuardContext implements GuardContext
{
    private final Long2ObjectHashMap<X509GuardHandler> handlersById;
    private final LongSupplier supplyAuthorizedId;
    private final EngineContext context;

    X509GuardContext(
        Configuration config,
        EngineContext context)
    {
        this.handlersById = new Long2ObjectHashMap<>();
        this.context = context;
        this.supplyAuthorizedId = context::supplyAuthorizedId;
    }

    @Override
    public X509GuardHandler attach(
        GuardConfig guard)
    {
        X509OptionsConfig options = (X509OptionsConfig) guard.options;
        X509GuardHandler handler = new X509GuardHandler(options, context, supplyAuthorizedId);
        handlersById.put(guard.id, handler);
        return handler;
    }

    @Override
    public void detach(
        GuardConfig guard)
    {
        handlersById.remove(guard.id);
    }

    X509GuardHandler handler(
        long guardId)
    {
        return handlersById.get(guardId);
    }
}
