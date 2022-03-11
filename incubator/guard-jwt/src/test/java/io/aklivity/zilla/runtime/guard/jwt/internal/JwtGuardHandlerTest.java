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

import static io.aklivity.zilla.runtime.guard.jwt.internal.keys.JwtKeyConfigs.RFC7515_RS256_CONFIG;
import static io.aklivity.zilla.specs.guard.jwt.keys.JwtKeys.RFC7515_RS256;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;

import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.Test;

import io.aklivity.zilla.runtime.guard.jwt.internal.config.JwtOptionsConfig;

public class JwtGuardHandlerTest
{
    @Test
    public void shouldAuthorize() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256");

        long authorizedId = guard.reauthorize(101L, token);

        assertThat(authorizedId, not(equalTo(0L)));
        assertThat(guard.identity(authorizedId), equalTo("testSubject"));
        assertThat(guard.expiresAt(authorizedId), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiringAt(authorizedId), equalTo(ofSeconds(now.getEpochSecond() + 10L).minus(challenge).toMillis()));
        assertTrue(guard.verify(authorizedId, asList("read:stream", "write:stream")));
    }

    @Test
    public void shouldAuthorizeAndWithinChallengeWindow() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertTrue(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
    }

    @Test
    public void shouldAuthorizeAndNotWithinChallengeWindow() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertFalse(guard.challenge(sessionId, now.plusSeconds(5L).toEpochMilli()));
    }

    @Test
    public void shouldNotAuthorizeWhenAlgorithmDiffers() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS512");

        long authorizedId = guard.reauthorize(101L, token);

        assertThat(authorizedId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenSignatureInvalid() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256")
                .replaceFirst("\\.[^X]", ".X")
                .replaceFirst("\\.[^Y]", ".Y");

        long authorizedId = guard.reauthorize(101L, token);

        assertThat(authorizedId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenIssuerDiffers() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "not test issuer");
        claims.setClaim("aud", "testAudience");
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256");

        long authorizedId = guard.reauthorize(101L, token);

        assertThat(authorizedId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenAudienceDiffers() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "not testAudience");
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256");

        long authorizedId = guard.reauthorize(101L, token);

        assertThat(authorizedId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenExpired() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("exp", now.getEpochSecond() - 10L);
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256");

        long authorizedId = guard.reauthorize(101L, token);

        assertThat(authorizedId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenNotYetValid() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("nbf", now.getEpochSecond() + 10L);
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256");

        long authorizedId = guard.reauthorize(101L, token);

        assertThat(authorizedId, equalTo(0L));
    }

    @Test
    public void shouldNotVerifyAuthorizedWhenRolesInsufficient() throws Exception
    {
        Duration challenge = ofSeconds(30L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("scope", "read:stream");
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256");

        long authorizedId = guard.reauthorize(101L, token);

        assertThat(authorizedId, not(equalTo(0L)));
        assertFalse(guard.verify(authorizedId, asList("read:stream", "write:stream")));
    }

    @Test
    public void shouldDeauthorize() throws Exception
    {
        Duration challenge = ofSeconds(30L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, Long.valueOf(1L)::longValue);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");
        String payload = claims.toJson();

        String token = sign(payload, "test", RFC7515_RS256, "RS256");

        long authorizedId = guard.reauthorize(101L, token);

        guard.deauthorize(authorizedId);
    }

    private static String sign(
        String payload,
        String kid,
        KeyPair pair,
        String alg) throws JoseException
    {
        final JsonWebSignature signature = new JsonWebSignature();
        signature.setPayload(payload);
        signature.setKey(pair.getPrivate());
        signature.setKeyIdHeaderValue(kid);
        signature.setAlgorithmHeaderValue(alg);
        signature.sign();
        signature.setKey(pair.getPublic());

        return String.format("%s.%s.%s",
                signature.getHeaders().getEncodedHeader(),
                signature.getEncodedPayload(),
                signature.getEncodedSignature());
    }
}
