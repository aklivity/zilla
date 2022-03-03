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

import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;

import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.guard.jwt.internal.config.JwtKeyConfig;
import io.aklivity.zilla.runtime.guard.jwt.internal.config.JwtOptionsConfig;

public class JwtGuardHandler implements GuardHandler
{
    private final JsonWebSignature signature = new JsonWebSignature();

    private final String issuer;
    private final String audience;
    private final Map<String, JsonWebKey> keys;

    public JwtGuardHandler(
        JwtOptionsConfig options)
    {
        this.issuer = options.issuer;
        this.audience = options.audience;

        Map<String, JsonWebKey> keys = new HashMap<>();
        for (JwtKeyConfig key : options.keys)
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
                keys.put(key.kid, JsonWebKey.Factory.newJwk(params));
            }
            catch (JoseException ex)
            {
                rethrowUnchecked(ex);
            }
        }
        this.keys = keys;
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
        int authorized = 0;

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
                break authorize;
            }

            signature.setKey(key.getKey());
            if (!signature.verifySignature())
            {
                break authorize;
            }

            String payload = signature.getPayload();
            JwtClaims claims = JwtClaims.parse(payload);
            NumericDate notBefore = claims.getNotBefore();
            NumericDate notAfter = claims.getExpirationTime();
            String issuer = claims.getIssuer();
            List<String> audience = claims.getAudience();

            long now = System.currentTimeMillis();
            if (notBefore != null && now < notBefore.getValueInMillis() ||
                notAfter != null && now > notAfter.getValueInMillis() ||
                issuer == null || !issuer.equals(this.issuer) ||
                audience == null || !audience.contains(this.audience))
            {
                break authorize;
            }

            // create session
            // store at session id, scoped by engine context index
            // TODO: when to clean up session?
        }
        catch (JoseException | InvalidJwtException | MalformedClaimException ex)
        {
            // not authorized
        }

        return authorized;
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
