/*
 * Copyright 2021-2022 Aklivity Inc
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

import java.util.List;

import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.guard.jwt.internal.config.JwtOptionsConfig;

public class JwtGuardHandler implements GuardHandler
{
    public JwtGuardHandler(
        JwtOptionsConfig options)
    {
    }

    @Override
    public long verifier(
        List<String> roles)
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long authorize(
        long session,
        String credentials)
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String identity(
        long session)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long expiresAt(
        long session)
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long challengeAt(
        long session)
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean allows(
        long session,
        long verifier)
    {
        // TODO Auto-generated method stub
        return false;
    }
}
