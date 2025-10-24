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
    private static final String SPLIT_VALUE_PATTERN = "\\s+";
    private static final String SPLIT_PATH_PATTERN = "\\.";

    private final JsonWebSignature signature = new JsonWebSignature();

    private final String issuer;
    private final String audience;
    private final String roles;
    private final Duration challenge;
    private final String identity;
    private final Map<String, JsonWebKey> keys;
    private final Long2ObjectHashMap<JwtSession> sessionsById;
    private final LongSupplier supplyAuthorizedId;
    private final Long2ObjectHashMap<JwtSessionStore> sessionStoresByContextId;
    private final JwtEventContext event;
    private final Map<String, String> attributes;

    public JwtGuardHandler(
        JwtOptionsConfig options,
        EngineContext context,
        LongSupplier supplyAuthorizedId)
    {
        this.issuer = options.issuer;
        this.audience = options.audience;
        this.roles = options.roles;
        this.challenge = options.challenge.orElse(null);
        this.identity = options.identity;

        List<JwtKeyConfig> keysConfig = options.keys;
        if ((keysConfig == null || keysConfig.isEmpty()) && options.keysURL.isPresent())
        {
            Jsonb jsonb = JsonbBuilder.newBuilder()
                    .withConfig(new JsonbConfig()
                        .withAdapters(new JwtKeySetConfigAdapter()))
                    .build();
            Path keysPath = context.resolvePath(options.keysURL.get());
            String keysText = readKeys(keysPath);
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
        this.attributes = options.attributes;
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
            identity = this.identity != null ? claims.getStringClaimValue(this.identity) : claims.getSubject();
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

            Object rolesValue = claimValue(claims, this.roles);
            @SuppressWarnings("unchecked")
            List<String> roles = (rolesValue instanceof List)
                    ? (List<String>) rolesValue
                    : Optional.ofNullable(rolesValue)
                        .map(Object::toString)
                        .map(s -> s.split(SPLIT_VALUE_PATTERN))
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
            event.authorizationFailed(traceId, bindingId, identity, reason);
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

    private static Object claimValue(
        Object node,
        String path)
    {
        Object current = node;
        for (String part : path.split(SPLIT_PATH_PATTERN))
        {
            if (current == null)
            {
                break;
            }
            if (current instanceof JwtClaims)
            {
                current = ((JwtClaims) current).getClaimValue(part);
            }
            else if (current instanceof Map)
            {
                current = ((Map<?, ?>) current).get(part);
            }
            else
            {
                current = null;
            }
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
