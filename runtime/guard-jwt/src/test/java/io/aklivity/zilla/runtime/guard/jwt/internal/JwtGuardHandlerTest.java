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

import java.net.URL;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

import org.agrona.collections.MutableLong;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.Test;

import io.aklivity.zilla.runtime.guard.jwt.internal.config.JwtOptionsConfig;

public class JwtGuardHandlerTest
{
    private static final Function<URL, String> READ_KEYS_URL = url -> "{}";

    @Test
    public void shouldAuthorize() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertThat(sessionId, not(equalTo(0L)));
        assertThat(guard.identity(sessionId), equalTo("testSubject"));
        assertThat(guard.expiresAt(sessionId), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiringAt(sessionId), equalTo(ofSeconds(now.getEpochSecond() + 10L).minus(challenge).toMillis()));
        assertTrue(guard.verify(sessionId, asList("read:stream", "write:stream")));
    }

    @Test
    public void shouldChallengeDuringChallengeWindow() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertTrue(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
    }

    @Test
    public void shouldNotChallengeDuringWindowWithoutSubject() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertFalse(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
    }

    @Test
    public void shouldNotChallengeBeforeChallengeWindow() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertFalse(guard.challenge(sessionId, now.plusSeconds(5L).toEpochMilli()));
    }

    @Test
    public void shouldNotChallengeAgainDuringChallengeWindow() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertTrue(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
        assertFalse(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
        assertFalse(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
    }

    @Test
    public void shouldNotAuthorizeWhenAlgorithmDiffers() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS512");

        long sessionId = guard.reauthorize(101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenSignatureInvalid() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256")
                .replaceFirst("\\.[^X]", ".X")
                .replaceFirst("\\.[^Y]", ".Y");

        long sessionId = guard.reauthorize(101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenIssuerDiffers() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "not test issuer");
        claims.setClaim("aud", "testAudience");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenAudienceDiffers() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "not testAudience");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenExpired() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("exp", now.getEpochSecond() - 10L);

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenNotYetValid() throws Exception
    {
        JwtOptionsConfig options = new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), null);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("nbf", now.getEpochSecond() + 10L);

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotVerifyAuthorizedWhenRolesInsufficient() throws Exception
    {
        Duration challenge = ofSeconds(30L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("scope", "read:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        assertThat(sessionId, not(equalTo(0L)));
        assertFalse(guard.verify(sessionId, asList("read:stream", "write:stream")));
    }

    @Test
    public void shouldReauthorizeWhenExpirationLater() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 60L);
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(101L, tokenPlus60);

        assertThat(sessionIdPlus60, equalTo(sessionIdPlus10));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldReauthorizeWhenScopeBroader() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 60L);
        claims.setClaim("scope", "read:stream write:stream");
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(101L, tokenPlus60);

        assertThat(sessionIdPlus60, equalTo(sessionIdPlus10));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldNotReauthorizeWhenExpirationEarlier() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 5L);
        String tokenPlus5 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus5 = guard.reauthorize(101L, tokenPlus5);

        assertThat(sessionIdPlus5, equalTo(sessionIdPlus10));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
    }

    @Test
    public void shouldNotReauthorizeWhenScopeNarrower() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 60L);
        claims.setClaim("scope", "read:stream");
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(101L, tokenPlus60);

        assertThat(sessionIdPlus60, not(equalTo(sessionIdPlus10)));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiresAt(sessionIdPlus60), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldNotReauthorizeWhenSubjectDiffers() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(101L, tokenPlus10);

        claims.setClaim("sub", "otherSubject");
        claims.setClaim("exp", now.getEpochSecond() + 60L);
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(101L, tokenPlus60);

        assertThat(sessionIdPlus60, not(equalTo(sessionIdPlus10)));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiresAt(sessionIdPlus60), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldNotReauthorizeWhenContextDiffers() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 60L);
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(202L, tokenPlus60);

        assertThat(sessionIdPlus60, not(equalTo(sessionIdPlus10)));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiresAt(sessionIdPlus60), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldDeauthorize() throws Exception
    {
        Duration challenge = ofSeconds(30L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        JwtGuardHandler guard = new JwtGuardHandler(options, new MutableLong(1L)::getAndIncrement, READ_KEYS_URL);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(101L, token);

        guard.deauthorize(sessionId);
    }

    static String sign(
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
