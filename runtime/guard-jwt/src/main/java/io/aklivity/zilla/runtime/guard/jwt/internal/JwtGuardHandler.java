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

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.guard.jwt.JwtKeyConfig;
import io.aklivity.zilla.config.guard.jwt.JwtKeySetConfig;
import io.aklivity.zilla.config.guard.jwt.JwtKeySetConfigReader;
import io.aklivity.zilla.config.guard.jwt.JwtOptionsConfig;
import io.aklivity.zilla.runtime.common.jwt.Jwk;
import io.aklivity.zilla.runtime.common.jwt.Jws;
import io.aklivity.zilla.runtime.common.jwt.JwtClaims;
import io.aklivity.zilla.runtime.common.jwt.JwtException;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class JwtGuardHandler implements GuardHandler
{
    private static final Pattern SPLIT_VALUE_PATTERN = Pattern.compile("\\s+");

    private final String issuer;
    private final String audience;
    private final String roles;
    private final Duration challenge;
    private final String identity;
    private final Map<String, Jwk> keys;
    private final Long2ObjectHashMap<JwtSession> sessionsById;
    private final LongSupplier supplyAuthorizedId;
    private final Long2ObjectHashMap<JwtSessionStore> sessionStoresByContextId;
    private final JwtEventContext event;
    private final Map<String, String> attributes;
    private final Consumer<Runnable> dispatcher;
    private final LongFunction<String> credentialer;

    public JwtGuardHandler(
        JwtOptionsConfig options,
        EngineContext context,
        LongSupplier supplyAuthorizedId,
        LongFunction<String> credentialer)
    {
        this.issuer = options.issuer;
        this.audience = options.audience;
        this.roles = options.roles;
        this.challenge = options.challenge.orElse(null);
        this.identity = options.identity;

        List<JwtKeyConfig> keysConfig = options.keys;
        if ((keysConfig == null || keysConfig.isEmpty()) && options.keysURL.isPresent())
        {
            JwtKeySetConfigReader reader = new JwtKeySetConfigReader();
            Path keysPath = context.resolvePath(options.keysURL.get());
            String keysText = readKeys(keysPath);
            JwtKeySetConfig jwks = reader.read(keysText);
            keysConfig = jwks.keys;
        }

        Map<String, Jwk> resolvedKeys = new HashMap<>();
        if (keysConfig != null)
        {
            for (JwtKeyConfig key : keysConfig)
            {
                try
                {
                    resolvedKeys.put(key.kid, Jwk.parse(asJwk(key)));
                }
                catch (JwtException ex)
                {
                    rethrowUnchecked(ex);
                }
            }
        }

        this.keys = resolvedKeys;
        this.supplyAuthorizedId = supplyAuthorizedId;
        this.sessionsById = new Long2ObjectHashMap<>();
        this.sessionStoresByContextId = new Long2ObjectHashMap<>();
        this.event = new JwtEventContext(context);
        this.attributes = options.attributes;
        this.dispatcher = context::dispatch;
        this.credentialer = credentialer;
    }

    @Override
    public long reauthorize(
        long traceId,
        long bindingId,
        long contextId,
        String credentials)
    {
        JwtSession session = null;
        String identity = null;
        String reason = "";
        Map<String, String> attributes = new HashMap<>();

        authorize:
        try
        {
            if (credentials == null)
            {
                reason = "Missing credentials.";
                break authorize;
            }

            Jws jws = Jws.parse(credentials);

            String kid = jws.keyId();
            String alg = jws.algorithm();
            Jwk key = keys.get(kid);

            if (alg == null ||
                key == null ||
                !Objects.equals(alg, key.algorithm()))
            {
                reason = "Invalid alg or key.";
                break authorize;
            }

            String payload = jws.verifiedPayload(key);
            if (payload == null)
            {
                reason = "Unable to verify key signature.";
                break authorize;
            }

            JwtClaims claims = JwtClaims.parse(payload);
            identity = this.identity != null ? claims.getStringClaim(this.identity) : claims.getSubject();
            Instant notBefore = claims.getNotBefore();
            Instant notAfter = claims.getExpirationTime();
            String issuer = claims.getIssuer();
            List<String> audience = claims.getAudience();

            long now = Instant.now().toEpochMilli();
            if (notBefore != null && now < notBefore.toEpochMilli() ||
                notAfter != null && now > notAfter.toEpochMilli())
            {
                reason = "Token is expired.";
                break authorize;
            }
            if (issuer == null || !issuer.equals(this.issuer) || !audience.contains(this.audience))
            {
                reason = "Invalid issuer or audience.";
                break authorize;
            }

            Object rolesValue = claimValue(claims, this.roles);
            @SuppressWarnings("unchecked")
            List<String> roles = (rolesValue instanceof List)
                    ? (List<String>) rolesValue
                    : Optional.ofNullable(rolesValue)
                        .map(Object::toString)
                        .map(SPLIT_VALUE_PATTERN::split)
                        .map(Arrays::asList)
                        .orElse(null);

            if (this.attributes != null && !this.attributes.isEmpty())
            {
                this.attributes
                    .forEach((name, attribute) ->
                    {
                        Object value = claimValue(claims, attribute);
                        attributes.put(name, value != null ? value.toString() : null);
                    });
            }

            JwtSessionStore sessionStore = supplySessionStore(contextId);
            session = sessionStore.supplySession(identity, roles, attributes);

            session.credentials = credentials;
            session.roles = roles;
            session.expiresAt = notAfter != null
                ? Math.max(session.expiresAt, notAfter.toEpochMilli())
                : EXPIRES_NEVER;
            session.challengeAt = challenge != null ? session.expiresAt - challenge.toMillis() : session.expiresAt;

            JwtSession previous = sessionsById.put(session.authorized, session);
            assert previous != session && session.refs == 0 || previous == session && session.refs > 0;
            session.refs++;
        }
        catch (JwtException ex)
        {
            reason = ex.getMessage();
        }
        if (session == null)
        {
            event.authorizationFailed(traceId, bindingId, identity, reason);
        }
        return session != null ? session.authorized : NOT_AUTHORIZED;
    }

    // a JWT is self-contained, so the decision above is always available locally; delivery is
    // still deferred a tick because the async contract promises the caller a later callback
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
        JwtSession session = sessionsById.get(sessionId);
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
        JwtSession session = sessionsById.get(sessionId);
        return session != null ? session.identity : null;
    }

    @Override
    public String attribute(
        long sessionId,
        String name)
    {
        JwtSession session = sessionsById.get(sessionId);
        return session != null ? session.attributes.get(name) : null;
    }

    @Override
    public String credentials(
        long sessionId)
    {
        String local = credentialsLocal(sessionId);
        return local != null ? local : credentialer.apply(sessionId);
    }

    // local-only lookup, reused by credentials() above as its fast path and by JwtGuard's
    // cross-worker dispatch (reached via the credentialer callback) once it has already
    // routed to the specific worker that owns sessionId -- never itself falls back, so a
    // session that turns out not to be local anywhere simply resolves to null rather than
    // looping back through the callback that got us here
    String credentialsLocal(
        long sessionId)
    {
        JwtSession session = sessionsById.get(sessionId);
        return session != null ? session.credentials : null;
    }

    @Override
    public long expiresAt(
        long sessionId)
    {
        JwtSession session = sessionsById.get(sessionId);
        return session != null ? session.expiresAt : EXPIRES_NEVER;
    }

    @Override
    public long expiringAt(
        long sessionId)
    {
        JwtSession session = sessionsById.get(sessionId);
        return session != null ? session.challengeAt : EXPIRES_NEVER;
    }

    @Override
    public boolean challenge(
        long sessionId,
        long now)
    {
        JwtSession session = sessionsById.get(sessionId);
        return session != null && session.challenge(now);
    }

    @Override
    public boolean verify(
        long sessionId,
        List<String> roles)
    {
        JwtSession session = sessionsById.get(sessionId);
        return session != null && subsetOf(session, roles);
    }

    private boolean subsetOf(
        JwtSession session,
        List<String> roles)
    {
        return roles != null && session.roles != null && session.roles.containsAll(roles);
    }

    private boolean supersetOf(
        JwtSession session,
        List<String> roles)
    {
        return roles != null && session.roles == null || roles.containsAll(session.roles);
    }

    private JwtSessionStore supplySessionStore(
        long contextId)
    {
        return sessionStoresByContextId.computeIfAbsent(contextId, JwtSessionStore::new);
    }

    private final class JwtSessionStore
    {
        private final long contextId;
        private final Map<String, JwtSession> sessionsByIdentity;

        private JwtSessionStore(
            long contextId)
        {
            this.contextId = contextId;
            this.sessionsByIdentity = new IdentityHashMap<>();
        }

        private JwtSession supplySession(
            String identity,
            List<String> roles,
            Map<String, String> attributes)
        {
            String identityKey = identity != null ? identity.intern() : null;
            JwtSession session = sessionsByIdentity.get(identityKey);

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

        private JwtSession newSharedSession(
            String identity,
            Map<String, String> attributes)
        {
            return new JwtSession(supplyAuthorizedId.getAsLong(), identity, attributes, this::onUnshared);
        }

        private JwtSession newSession(
            String identity,
            Map<String, String> attributes)
        {
            return new JwtSession(supplyAuthorizedId.getAsLong(), identity, attributes);
        }

        private void onUnshared(
            JwtSession session)
        {
            sessionsByIdentity.remove(session.identity);
            if (sessionsByIdentity.isEmpty())
            {
                sessionStoresByContextId.remove(contextId);
            }
        }
    }

    private final class JwtSession
    {
        private final long authorized;
        private final String identity;
        private final Consumer<JwtSession> unshare;
        private final Map<String, String> attributes;

        private String credentials;
        private long expiresAt;
        private long challengeAt;
        private long challengedAt;

        private volatile List<String> roles;

        private int refs;

        private JwtSession(
            long authorized,
            String identity,
            Map<String, String> attributes)
        {
            this(authorized, identity, attributes, null);
        }

        private JwtSession(
            long authorized,
            String identity,
            Map<String, String> attributes,
            Consumer<JwtSession> unshare)
        {
            this.authorized = authorized;
            this.identity = identity;
            this.attributes = attributes;
            this.unshare = unshare;
        }

        boolean challenge(
            long now)
        {
            final boolean challenge =
                identity != null &&
                challengeAt <= now && now < expiresAt &&
                challengedAt < challengeAt;

            if (challenge)
            {
                challengedAt = challengeAt;
            }

            return challenge;
        }

        private void unshareIfNecessary()
        {
            if (unshare != null)
            {
                unshare.accept(this);
            }
        }
    }

    private static JsonObject asJwk(
        JwtKeyConfig key)
    {
        JsonObjectBuilder jwk = Json.createObjectBuilder();
        addMember(jwk, "kty", key.kty);
        addMember(jwk, "kid", key.kid);
        addMember(jwk, "e", key.e);
        addMember(jwk, "n", key.n);
        addMember(jwk, "alg", key.alg);
        addMember(jwk, "crv", key.crv);
        addMember(jwk, "x", key.x);
        addMember(jwk, "y", key.y);
        addMember(jwk, "use", key.use);
        return jwk.build();
    }

    private static void addMember(
        JsonObjectBuilder jwk,
        String name,
        String value)
    {
        if (value != null)
        {
            jwk.add(name, value);
        }
    }

    private static Object claimValue(
        JwtClaims claims,
        String path)
    {
        int end = path.indexOf('.');

        Object current = claims.getClaimValue(end == -1 ? path : path.substring(0, end));
        while (end != -1 && current != null)
        {
            int start = end + 1;
            end = path.indexOf('.', start);

            String name = end == -1 ? path.substring(start) : path.substring(start, end);
            current = current instanceof Map<?, ?> map ? map.get(name) : null;
        }

        return current;
    }

    private static String readKeys(
        Path keysPath)
    {
        String content = null;

        try
        {
            content = Files.readString(keysPath);
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }

        return content;
    }
}
