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

import static io.aklivity.zilla.runtime.engine.test.internal.guard.TestGuardConfig.DEFAULT_CHALLENGE_NEVER;
import static io.aklivity.zilla.runtime.engine.test.internal.guard.TestGuardConfig.DEFAULT_LIFETIME_FOREVER;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.MutableLong;

import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class TestGuardHandler implements GuardHandler
{
    private final String credentials;
    private final Duration challenge;
    private final Duration lifetime;
    private final List<String> roles;

    private final Long2LongHashMap sessions;
    private final MutableLong nextSessionId;

    public TestGuardHandler(
        TestGuardConfig config)
    {
        this.credentials = config.options != null ? config.options.credentials : null;
        this.lifetime = config.options != null ? config.options.lifetime : DEFAULT_LIFETIME_FOREVER;
        this.challenge = config.options != null ? config.options.challenge : DEFAULT_CHALLENGE_NEVER;
        this.roles = config.options != null ? config.options.roles : null;
        this.sessions = new Long2LongHashMap(-1L);
        this.nextSessionId = new MutableLong(1L);
    }

    @Override
    public long reauthorize(
        long contextId,
        String credentials)
    {
        long sessionId = 0L;

        if (this.credentials != null && this.credentials.equals(credentials))
        {
            long expiresAt = DEFAULT_LIFETIME_FOREVER.equals(lifetime)
                    ? EXPIRES_NEVER
                    : Instant.now().toEpochMilli() + lifetime.toMillis();

            sessionId = nextSessionId.value++;
            sessions.put(sessionId, expiresAt);
        }

        return sessionId;
    }

    @Override
    public void deauthorize(
        long sessionId)
    {
        sessions.remove(sessionId);
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
        return sessions.containsKey(sessionId) ? sessions.get(sessionId) : 0L;
    }

    @Override
    public long expiringAt(
        long sessionId)
    {
        final long expiresAt = expiresAt(sessionId);
        return expiresAt != 0L ? expiresAt - challenge.toMillis() : 0L;
    }

    @Override
    public boolean challenge(
        long sessionId,
        long now)
    {
        final long expiresAt = expiresAt(sessionId);
        final long challengeAt = expiresAt - challenge.toMillis();
        return expiresAt != 0L && challengeAt <= now && now < expiresAt;
    }

    boolean verify(
        long sessionId,
        List<String> roles)
    {
        return (sessionId == 0L && credentials == null || sessions.containsKey(sessionId)) &&
                (this.roles == null || this.roles.containsAll(roles));
    }
}
