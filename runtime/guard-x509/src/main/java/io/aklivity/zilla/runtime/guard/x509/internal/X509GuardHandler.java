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

import static io.aklivity.zilla.config.guard.x509.X509OptionsConfigBuilder.IDENTITY_DEFAULT;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.emptyMap;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.guard.x509.X509OptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class X509GuardHandler implements GuardHandler
{
    private static final String CERTIFICATE_TYPE = "X.509";

    private static final String REASON_MISSING = "Missing credentials.";
    private static final String REASON_EMPTY = "Empty certificate chain.";
    private static final String REASON_UNPARSEABLE = "Unable to parse certificate chain.";

    private final CertificateFactory factory;
    private final String identity;
    private final Map<String, String> attributes;
    private final X509Roles roles;
    private final Long2ObjectHashMap<X509Session> sessionsById;
    private final Long2ObjectHashMap<X509SessionStore> sessionStoresByContextId;
    private final LongSupplier supplyAuthorizedId;
    private final X509EventContext event;
    private final Consumer<Runnable> dispatcher;

    public X509GuardHandler(
        X509OptionsConfig options,
        EngineContext context,
        LongSupplier supplyAuthorizedId)
    {
        this.identity = options != null && options.identity != null ? options.identity : IDENTITY_DEFAULT;
        this.attributes = options != null && options.attributes != null ? options.attributes : emptyMap();
        this.roles = new X509Roles(options != null ? options.roles : null);
        this.factory = newCertificateFactory();
        this.supplyAuthorizedId = supplyAuthorizedId;
        this.sessionsById = new Long2ObjectHashMap<>();
        this.sessionStoresByContextId = new Long2ObjectHashMap<>();
        this.event = new X509EventContext(context);
        this.dispatcher = context::dispatch;
    }

    @Override
    public long reauthorize(
        long traceId,
        long bindingId,
        long contextId,
        String credentials)
    {
        X509Session session = null;
        String identity = null;
        String reason = "";
        Map<String, String> attributes = new LinkedHashMap<>();

        authorize:
        try
        {
            if (credentials == null)
            {
                reason = REASON_MISSING;
                break authorize;
            }

            List<X509Certificate> chain = decode(credentials);

            if (chain.isEmpty())
            {
                reason = REASON_EMPTY;
                break authorize;
            }

            X509Certificate leaf = chain.get(0);
            Map<String, List<String>> fields = X509Fields.resolve(leaf);

            identity = value(fields, this.identity);

            this.attributes.forEach((name, field) -> attributes.put(name, value(fields, field)));

            List<String> roles = this.roles.resolve(fields);

            X509SessionStore sessionStore = supplySessionStore(contextId);
            session = sessionStore.supplySession(identity, roles, attributes);

            session.credentials = credentials;
            session.roles = roles;
            session.expiresAt = leaf.getNotAfter().getTime();

            X509Session previous = sessionsById.put(session.authorized, session);
            assert previous != session && session.refs == 0 || previous == session && session.refs > 0;
            session.refs++;
        }
        catch (CertificateException ex)
        {
            reason = REASON_UNPARSEABLE;
        }

        if (session == null)
        {
            event.authorizationFailed(traceId, bindingId, identity, reason);
        }

        return session != null ? session.authorized : NOT_AUTHORIZED;
    }

    // a verified chain is self-contained, so the decision above is always available locally;
    // delivery is still deferred a tick because the async contract promises the caller a later callback
    @Override
    public void reauthorize(
        long traceId,
        long bindingId,
        long contextId,
        String credentials,
        LongCompletionCallback completion)
    {
        dispatcher.accept(() -> complete(traceId, bindingId, contextId, credentials, completion));
    }

    private void complete(
        long traceId,
        long bindingId,
        long contextId,
        String credentials,
        LongCompletionCallback completion)
    {
        try
        {
            completion.completed(contextId, reauthorize(traceId, bindingId, contextId, credentials));
        }
        catch (Throwable ex)
        {
            completion.failed(contextId, ex);
        }
    }

    @Override
    public void deauthorize(
        long sessionId)
    {
        X509Session session = sessionsById.get(sessionId);
        if (session != null)
        {
            session.refs--;

            if (session.refs == 0)
            {
                sessionsById.remove(session.authorized);
                session.unshare();
            }
        }
    }

    @Override
    public String identity(
        long sessionId)
    {
        X509Session session = sessionsById.get(sessionId);
        return session != null ? session.identity : null;
    }

    @Override
    public String attribute(
        long sessionId,
        String name)
    {
        X509Session session = sessionsById.get(sessionId);
        return session != null ? session.attributes.get(name) : null;
    }

    @Override
    public String credentials(
        long sessionId)
    {
        X509Session session = sessionsById.get(sessionId);
        return session != null ? session.credentials : null;
    }

    @Override
    public long expiresAt(
        long sessionId)
    {
        X509Session session = sessionsById.get(sessionId);
        return session != null ? session.expiresAt : EXPIRES_NEVER;
    }

    @Override
    public long expiringAt(
        long sessionId)
    {
        return expiresAt(sessionId);
    }

    @Override
    public boolean challenge(
        long sessionId,
        long now)
    {
        return false;
    }

    @Override
    public boolean verify(
        long sessionId,
        List<String> roles)
    {
        X509Session session = sessionsById.get(sessionId);
        return session != null && subsetOf(session, roles);
    }

    private List<X509Certificate> decode(
        String credentials) throws CertificateException
    {
        String pem = unescape(credentials);

        Collection<? extends Certificate> certificates =
            factory.generateCertificates(new ByteArrayInputStream(pem.getBytes(US_ASCII)));

        return certificates.stream()
            .filter(X509Certificate.class::isInstance)
            .map(X509Certificate.class::cast)
            .toList();
    }

    private boolean subsetOf(
        X509Session session,
        List<String> roles)
    {
        return roles != null && session.roles != null && session.roles.containsAll(roles);
    }

    private boolean supersetOf(
        X509Session session,
        List<String> roles)
    {
        return session.roles == null || roles.containsAll(session.roles);
    }

    private X509SessionStore supplySessionStore(
        long contextId)
    {
        return sessionStoresByContextId.computeIfAbsent(contextId, X509SessionStore::new);
    }

    private static String value(
        Map<String, List<String>> fields,
        String field)
    {
        List<String> values = fields.get(field);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    // leniency for credentials that survived a transport rendering escaped newlines,
    // not a second supported credential format
    private static String unescape(
        String credentials)
    {
        return credentials
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\n")
            .strip();
    }

    private static CertificateFactory newCertificateFactory()
    {
        CertificateFactory factory = null;

        try
        {
            factory = CertificateFactory.getInstance(CERTIFICATE_TYPE);
        }
        catch (CertificateException ex)
        {
            rethrowUnchecked(ex);
        }

        return factory;
    }

    private final class X509SessionStore
    {
        private final long contextId;
        private final Map<String, X509Session> sessionsByIdentity;

        private X509SessionStore(
            long contextId)
        {
            this.contextId = contextId;
            this.sessionsByIdentity = new IdentityHashMap<>();
        }

        private X509Session supplySession(
            String identity,
            List<String> roles,
            Map<String, String> attributes)
        {
            String identityKey = identity != null ? identity.intern() : null;
            X509Session session = sessionsByIdentity.get(identityKey);

            if (identityKey == null || session != null && roles != null && !supersetOf(session, roles))
            {
                session = newSession(identityKey, attributes);
            }
            else
            {
                session = sessionsByIdentity.computeIfAbsent(identityKey, key -> newSharedSession(key, attributes));
            }

            return session;
        }

        private X509Session newSharedSession(
            String identity,
            Map<String, String> attributes)
        {
            return new X509Session(supplyAuthorizedId.getAsLong(), identity, attributes, this::onUnshared);
        }

        private X509Session newSession(
            String identity,
            Map<String, String> attributes)
        {
            return new X509Session(supplyAuthorizedId.getAsLong(), identity, attributes);
        }

        private void onUnshared(
            X509Session session)
        {
            sessionsByIdentity.remove(session.identity);
            if (sessionsByIdentity.isEmpty())
            {
                sessionStoresByContextId.remove(contextId);
            }
        }
    }

    private static final class X509Session
    {
        private final long authorized;
        private final String identity;
        private final Map<String, String> attributes;
        private final Consumer<X509Session> unshare;

        private String credentials;
        private long expiresAt;
        private List<String> roles;

        private int refs;

        private X509Session(
            long authorized,
            String identity,
            Map<String, String> attributes)
        {
            this(authorized, identity, attributes, null);
        }

        private X509Session(
            long authorized,
            String identity,
            Map<String, String> attributes,
            Consumer<X509Session> unshare)
        {
            this.authorized = authorized;
            this.identity = identity;
            this.attributes = attributes;
            this.unshare = unshare;
        }

        private void unshare()
        {
            if (unshare != null)
            {
                unshare.accept(this);
            }
        }
    }
}
