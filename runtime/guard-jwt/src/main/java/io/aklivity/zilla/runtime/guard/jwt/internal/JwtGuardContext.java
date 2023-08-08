/*
 * Copyright 2021-2023 Aklivity Inc
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

import java.util.function.LongSupplier;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;

final class JwtGuardContext implements GuardContext
{
    private final Long2ObjectHashMap<JwtGuardHandler> handlersById;
    private final LongSupplier supplyAuthorizedId;

    JwtGuardContext(
        Configuration config,
        EngineContext context)
    {
        this.handlersById = new Long2ObjectHashMap<>();
        this.supplyAuthorizedId = context::supplyAuthorizedId;
    }

    @Override
    public JwtGuardHandler attach(
        GuardConfig guard)
    {
        JwtOptionsConfig options = (JwtOptionsConfig) guard.options;
        JwtGuardHandler handler = new JwtGuardHandler(options, supplyAuthorizedId, guard.readURL);
        handlersById.put(guard.id, handler);
        return handler;
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
