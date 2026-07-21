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
package io.aklivity.zilla.runtime.guard.inline.internal;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.guard.inline.internal.config.InlineOptionsConfig;

public class InlineGuardHandler implements GuardHandler
{
    private final Long2ObjectHashMap<InlineSession> sessionsById;
    private final LongSupplier supplyAuthorizedId;
    private final Long2ObjectHashMap<InlineSessionStore> sessionStoresByContextId;
    private final String identity;
    private final String credentials;

    public InlineGuardHandler(
        LongSupplier supplyAuthorizedId,
        InlineOptionsConfig options)
    {
        this.supplyAuthorizedId = supplyAuthorizedId;
        this.sessionsById = new Long2ObjectHashMap<>();
        this.sessionStoresByContextId = new Long2ObjectHashMap<>();
        this.identity = options != null ? options.identity : null;
        this.credentials = options != null ? options.credentials : null;
    }

    @Override
    public long reauthorize(
        long traceId,
        long bindingId,
        long contextId,
        String credentials)
    {
        InlineSessionStore sessionStore = supplySessionStore(contextId);
        InlineSession session = sessionStore.supplySession(credentials);
        session.traceId = traceId;
        session.bindingId = bindingId;

        InlineSession previous = sessionsById.put(session.authorized, session);

        assert previous != session && session.refs == 0 || previous == session && session.refs > 0;
        session.refs++;
        return session != null ? session.authorized : NOT_AUTHORIZED;
    }

    @Override
    public void deauthorize(
        long sessionId)
    {
        InlineSession session = sessionsById.get(sessionId);
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
        InlineSession session = sessionsById.get(sessionId);
        return session != null ? session.identity : identity;
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
        InlineSession session = sessionsById.get(sessionId);
        return session != null ? session.identity : credentials;
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

    private InlineSessionStore supplySessionStore(
        long contextId)
    {
        return sessionStoresByContextId.computeIfAbsent(contextId, InlineSessionStore::new);
    }

    boolean verify(
        long sessionId)
    {
        return sessionsById.get(sessionId) != null;
    }

    @Override
    public boolean verify(
        long sessionId,
        List<String> roles)
    {
        return verify(sessionId) && (roles == null || roles.isEmpty());
    }

    private final class InlineSessionStore
    {
        private final long contextId;
        private final Map<String, InlineSession> sessionsByIdentity;

        private InlineSessionStore(
            long contextId)
        {
            this.contextId = contextId;
            this.sessionsByIdentity = new IdentityHashMap<>();
        }

        private InlineSession supplySession(
            String identity)
        {
            String identityKey = identity != null ? identity.intern() : null;
            InlineSession session = sessionsByIdentity.get(identityKey);

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

        private InlineSession newSharedSession(
            String identity)
        {
            return new InlineSession(supplyAuthorizedId.getAsLong(), identity, this::onUnshared);
        }

        private InlineSession newSession(
            String identity)
        {
            return new InlineSession(supplyAuthorizedId.getAsLong(), identity);
        }

        private void onUnshared(
            InlineSession session)
        {
            sessionsByIdentity.remove(session.identity);
            if (sessionsByIdentity.isEmpty())
            {
                sessionStoresByContextId.remove(contextId);
            }
        }
    }

    private final class InlineSession
    {
        private final long authorized;
        private final String identity;
        private final Consumer<InlineSession> unshare;

        private long traceId;
        private long bindingId;

        private int refs;

        private InlineSession(
            long authorized,
            String identity)
        {
            this(authorized, identity, null);
        }

        private InlineSession(
            long authorized,
            String identity,
            Consumer<InlineSession> unshare)
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
