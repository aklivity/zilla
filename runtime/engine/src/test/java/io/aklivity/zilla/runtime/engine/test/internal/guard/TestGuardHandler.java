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

import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class TestGuardHandler implements GuardHandler
{
    public TestGuardHandler(
        GuardConfig vault)
    {
    }

    @Override
    public long verifier(
        List<String> roles)
    {
        return 0;
    }

    @Override
    public long authorize(
        long session, String credentials)
    {
        return 0;
    }

    @Override
    public String identity(
        long session)
    {
        return "test";
    }

    @Override
    public long expiresAt(
        long session)
    {
        return 0;
    }

    @Override
    public long challengeAt(
        long session)
    {
        return 0;
    }

    @Override
    public boolean allows(
        long session,
        long verifier)
    {
        return false;
    }
}
