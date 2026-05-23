/*
 * Copyright 2021-2024 Aklivity Inc
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

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class IdentityGuardHandler implements GuardHandler
{
    private final Long2ObjectHashMap<IdentitySession> sessionsById;
    private final LongSupplier supplyAuthorizedId;
    private final Long2ObjectHashMap<IdentitySessionStore> sessionStoresByContextId;

    public IdentityGuardHandler(
        LongSupplier supplyAuthorizedId)
    {
        this.supplyAuthorizedId = supplyAuthorizedId;
        this.sessionsById = new Long2ObjectHashMap<>();
        this.sessionStoresByContextId = new Long2ObjectHashMap<>();
    }

    @Override
    public long reauthorize(
        long traceId,
        long bindingId,
        long contextId,
        String credentials)
    {
        IdentitySessionStore sessionStore = supplySessionStore(contextId);
        IdentitySession session = sessionStore.supplySession(credentials);
        session.traceId = traceId;
        session.bindingId = bindingId;

        IdentitySession previous = sessionsById.put(session.authorized, session);

        assert previous != session && session.refs == 0 || previous == session && session.refs > 0;
        session.refs++;
        return session != null ? session.authorized : NOT_AUTHORIZED;
    }

    @Override
    public void deauthorize(
        long sessionId)
    {
        IdentitySession session = sessionsById.get(sessionId);
        if (session != null)
        {
            session.refs--;

            if (session.refs == 0)
            {
                sessionsById.remove(session.authorized);
                session.unshareIfNecessary();
            }
        }
    }

    @Override
    public String identity(
        long sessionId)
    {
        IdentitySession session = sessionsById.get(sessionId);
        return session != null ? session.identity : null;
    }

    @Override
    public String attribute(
        long sessionId,
        String name)
    {
        return null;
    }

    @Override
    public String credentials(
        long sessionId)
    {
        IdentitySession session = sessionsById.get(sessionId);
        return session != null ? session.identity : null;
    }

    @Override
    public long expiresAt(
        long sessionId)
    {
        return EXPIRES_NEVER;
    }

    @Override
    public long expiringAt(
        long sessionId)
    {
        return EXPIRES_NEVER;
    }

    @Override
    public boolean challenge(
        long sessionId,
        long now)
    {
        return false;
    }

    private IdentitySessionStore supplySessionStore(
        long contextId)
    {
        return sessionStoresByContextId.computeIfAbsent(contextId, IdentitySessionStore::new);
    }

    boolean verify(
        long sessionId)
    {
        return sessionsById.get(sessionId) != null;
    }

    private final class IdentitySessionStore
    {
        private final long contextId;
        private final Map<String, IdentitySession> sessionsByIdentity;

        private IdentitySessionStore(
            long contextId)
        {
            this.contextId = contextId;
            this.sessionsByIdentity = new IdentityHashMap<>();
        }

        private IdentitySession supplySession(
            String identity)
        {
            String identityKey = identity != null ? identity.intern() : null;
            IdentitySession session = sessionsByIdentity.get(identityKey);

            if (identityKey == null || session != null)
            {
                session = newSession(identityKey);
            }
            else
            {
                session = sessionsByIdentity.computeIfAbsent(identityKey, this::newSharedSession);
            }

            return session;
        }

        private IdentitySession newSharedSession(
            String identity)
        {
            return new IdentitySession(supplyAuthorizedId.getAsLong(), identity, this::onUnshared);
        }

        private IdentitySession newSession(
            String identity)
        {
            return new IdentitySession(supplyAuthorizedId.getAsLong(), identity);
        }

        private void onUnshared(
            IdentitySession session)
        {
            sessionsByIdentity.remove(session.identity);
            if (sessionsByIdentity.isEmpty())
            {
                sessionStoresByContextId.remove(contextId);
            }
        }
    }

    private final class IdentitySession
    {
        private final long authorized;
        private final String identity;
        private final Consumer<IdentitySession> unshare;

        private long traceId;
        private long bindingId;

        private int refs;

        private IdentitySession(
            long authorized,
            String identity)
        {
            this(authorized, identity, null);
        }

        private IdentitySession(
            long authorized,
            String identity,
            Consumer<IdentitySession> unshare)
        {
            this.authorized = authorized;
            this.identity = identity;
            this.unshare = unshare;
        }

        private void unshareIfNecessary()
        {
            if (unshare != null)
            {
                unshare.accept(this);
            }
        }
    }
}
