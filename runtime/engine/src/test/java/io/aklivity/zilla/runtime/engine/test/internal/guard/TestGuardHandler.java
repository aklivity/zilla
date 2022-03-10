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

import java.util.List;

import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class TestGuardHandler implements GuardHandler
{
    private final String credentials;
    private final List<String> roles;

    public TestGuardHandler(
        TestGuardConfig config)
    {
        this.credentials = config.options != null ? config.options.credentials : null;
        this.roles = config.options != null ? config.options.roles : null;
    }

    @Override
    public long reauthorize(
        long contextId,
        String credentials)
    {
        return this.credentials != null && this.credentials.equals(credentials) ? 1L : 0L;
    }

    @Override
    public void deauthorize(
        long sessionId)
    {
    }

    @Override
    public String identity(
        long sessionId)
    {
        return "test";
    }

    @Override
    public long expiresAt(
        long sessionId)
    {
        return 0;
    }

    @Override
    public long challengeAt(
        long sessionId)
    {
        return 0;
    }

    boolean verify(
        long sessionId,
        List<String> roles)
    {
        return sessionId != 0L && (this.roles == null || this.roles.containsAll(roles));
    }
}
