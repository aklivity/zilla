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

import static org.agrona.LangUtil.rethrowUnchecked;

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
import java.util.function.Function;
import java.util.function.LongSupplier;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.agrona.collections.Long2ObjectHashMap;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtKeyConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtKeySetConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.guard.jwt.internal.config.JwtKeySetConfigAdapter;

public class JwtGuardHandler implements GuardHandler
{
    private final JsonWebSignature signature = new JsonWebSignature();

    private final String issuer;
    private final String audience;
    private final Duration challenge;
    private final Map<String, JsonWebKey> keys;
    private final Long2ObjectHashMap<JwtSession> sessionsById;
    private final LongSupplier supplyAuthorizedId;
    private final Long2ObjectHashMap<JwtSessionStore> sessionStoresByContextId;
    private final JwtEventContext event;

    public JwtGuardHandler(
        JwtOptionsConfig options,
        EngineContext context,
        LongSupplier supplyAuthorizedId,
        Function<String, String> readURL)
    {
        this.issuer = options.issuer;
        this.audience = options.audience;
        this.challenge = options.challenge.orElse(null);

        List<JwtKeyConfig> keysConfig = options.keys;
        if ((keysConfig == null || keysConfig.isEmpty()) && options.keysURL.isPresent())
        {
            JsonbConfig config = new JsonbConfig()
                    .withAdapters(new JwtKeySetConfigAdapter());
            Jsonb jsonb = JsonbBuilder.newBuilder()
                    .withConfig(config)
                    .build();

            String keysText = readURL.apply(options.keysURL.get());
            JwtKeySetConfig jwks = jsonb.fromJson(keysText, JwtKeySetConfig.class);
            keysConfig = jwks.keys;
        }

        Map<String, JsonWebKey> resolvedKeys = new HashMap<>();
        if (keysConfig != null)
        {
            for (JwtKeyConfig key : keysConfig)
            {
                try
                {
                    Map<String, Object> params = new HashMap<>();
                    params.put("kty", key.kty);
                    params.put("kid", key.kid);
                    params.put("e", key.e);
                    params.put("n", key.n);
                    params.put("alg", key.alg);
                    params.put("crv", key.crv);
                    params.put("x", key.x);
                    params.put("y", key.y);
                    params.put("use", key.use);
                    resolvedKeys.put(key.kid, JsonWebKey.Factory.newJwk(params));
                }
                catch (JoseException ex)
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
    }

    @Override
    public long reauthorize(
        long traceId,
        long bindingId,
        long contextId,
        String credentials)
    {
        JwtSession session = null;
        String subject = null;
        String reason = "";

        authorize:
        try
        {
            signature.setCompactSerialization(credentials);

            String kid = signature.getKeyIdHeaderValue();
            String alg = signature.getAlgorithmHeaderValue();
            JsonWebKey key = keys.get(kid);

            if (alg == null ||
                key == null ||
                !Objects.equals(alg, key.getAlgorithm()))
            {
                reason = "Invalid alg or key.";
                break authorize;
            }

            signature.setKey(null);
            signature.setKey(key.getKey());
            if (!signature.verifySignature())
            {
                reason = "Unable to verify key signature.";
                break authorize;
            }

            String payload = signature.getPayload();
            JwtClaims claims = JwtClaims.parse(payload);
            subject = claims.getSubject();
            NumericDate notBefore = claims.getNotBefore();
            NumericDate notAfter = claims.getExpirationTime();
            String issuer = claims.getIssuer();
            List<String> audience = claims.getAudience();

            long now = Instant.now().toEpochMilli();
            if (notBefore != null && now < notBefore.getValueInMillis() ||
                notAfter != null && now > notAfter.getValueInMillis())
            {
                reason = "Token is expired.";
                break authorize;
            }
            if (issuer == null || !issuer.equals(this.issuer) ||
                audience == null || !audience.contains(this.audience))
            {
                reason = "Invalid issuer or audience.";
                break authorize;
            }

            List<String> roles = Optional.ofNullable(claims.getClaimValue("scope"))
                .map(s -> s.toString().intern())
                .map(s -> s.split("\\s+"))
                .map(Arrays::asList)
                .orElse(null);

            JwtSessionStore sessionStore = supplySessionStore(contextId);
            session = sessionStore.supplySession(subject, roles);

            session.credentials = credentials;
            session.roles = roles;
            session.expiresAt = notAfter != null
                ? Math.max(session.expiresAt, notAfter.getValueInMillis())
                : EXPIRES_NEVER;
            session.challengeAt = challenge != null ? session.expiresAt - challenge.toMillis() : session.expiresAt;

            JwtSession previous = sessionsById.put(session.authorized, session);
            assert previous != session && session.refs == 0 || previous == session && session.refs > 0;
            session.refs++;
        }
        catch (JoseException | InvalidJwtException | MalformedClaimException ex)
        {
            reason = ex.getMessage();
        }
        if (session == null)
        {
            event.authorizationFailed(traceId, bindingId, subject, reason);
        }
        return session != null ? session.authorized : NOT_AUTHORIZED;
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
        return session != null ? session.subject : null;
    }

    @Override
    public String credentials(
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

    boolean verify(
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
        private final Map<String, JwtSession> sessionsBySubject;

        private JwtSessionStore(
            long contextId)
        {
            this.contextId = contextId;
            this.sessionsBySubject = new IdentityHashMap<>();
        }

        private JwtSession supplySession(
            String subject,
            List<String> roles)
        {
            String subjectKey = subject != null ? subject.intern() : null;
            JwtSession session = sessionsBySubject.get(subjectKey);

            if (subjectKey == null || session != null && roles != null && !supersetOf(session, roles))
            {
                session = newSession(subjectKey);
            }
            else
            {
                session = sessionsBySubject.computeIfAbsent(subjectKey, this::newSharedSession);
            }

            return session;
        }

        private JwtSession newSharedSession(
            String subject)
        {
            return new JwtSession(supplyAuthorizedId.getAsLong(), subject, this::onUnshared);
        }

        private JwtSession newSession(
            String subject)
        {
            return new JwtSession(supplyAuthorizedId.getAsLong(), subject);
        }

        private void onUnshared(
            JwtSession session)
        {
            sessionsBySubject.remove(session.subject);
            if (sessionsBySubject.isEmpty())
            {
                sessionStoresByContextId.remove(contextId);
            }
        }
    }

    private final class JwtSession
    {
        private final long authorized;
        private final String subject;
        private final Consumer<JwtSession> unshare;

        private String credentials;
        private long expiresAt;
        private long challengeAt;
        private long challengedAt;

        private volatile List<String> roles;

        private int refs;

        private JwtSession(
            long authorized,
            String subject)
        {
            this(authorized, subject, null);
        }

        private JwtSession(
            long authorized,
            String subject,
            Consumer<JwtSession> unshare)
        {
            this.authorized = authorized;
            this.subject = subject;
            this.unshare = unshare;
        }

        boolean challenge(
            long now)
        {
            final boolean challenge =
                subject != null &&
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
}
