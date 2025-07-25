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

import static io.aklivity.zilla.runtime.guard.jwt.internal.keys.JwtKeyConfigs.RFC7515_RS256_CONFIG;
import static io.aklivity.zilla.specs.guard.jwt.keys.JwtKeys.RFC7515_RS256;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.agrona.collections.MutableLong;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;

public class JwtGuardHandlerTest
{
    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.clock()).thenReturn(mock(Clock.class));
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    @Test
    public void shouldAuthorize() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, not(equalTo(0L)));
        assertThat(guard.identity(sessionId), equalTo("testSubject"));
        assertThat(guard.expiresAt(sessionId), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiringAt(sessionId), equalTo(ofSeconds(now.getEpochSecond() + 10L).minus(challenge).toMillis()));
        assertTrue(guard.verify(sessionId, asList("read:stream", "write:stream")));
    }

    @Test
    public void shouldAuthorizeWithCustomIdentity() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .identity("username")
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("username", "johndoe");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, not(equalTo(0L)));
        assertThat(guard.identity(sessionId), equalTo("johndoe"));
        assertThat(guard.expiresAt(sessionId), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiringAt(sessionId), equalTo(ofSeconds(now.getEpochSecond() + 10L).minus(challenge).toMillis()));
        assertTrue(guard.verify(sessionId, asList("read:stream", "write:stream")));
    }

    @Test
    public void shouldChallengeDuringChallengeWindow() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertTrue(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
    }

    @Test
    public void shouldNotChallengeDuringWindowWithoutSubject() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertFalse(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
    }

    @Test
    public void shouldNotChallengeBeforeChallengeWindow() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertFalse(guard.challenge(sessionId, now.plusSeconds(5L).toEpochMilli()));
    }

    @Test
    public void shouldNotChallengeAgainDuringChallengeWindow() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertTrue(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
        assertFalse(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
        assertFalse(guard.challenge(sessionId, now.plusSeconds(8L).toEpochMilli()));
    }

    @Test
    public void shouldNotAuthorizeWhenAlgorithmDiffers() throws Exception
    {
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS512");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenSignatureInvalid() throws Exception
    {
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256")
                .replaceFirst("\\.[^X]", ".X")
                .replaceFirst("\\.[^Y]", ".Y");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenIssuerDiffers() throws Exception
    {
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "not test issuer");
        claims.setClaim("aud", "testAudience");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenAudienceDiffers() throws Exception
    {
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "not testAudience");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenExpired() throws Exception
    {
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("exp", now.getEpochSecond() - 10L);

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenNotYetValid() throws Exception
    {
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("nbf", now.getEpochSecond() + 10L);

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotVerifyAuthorizedWhenRolesInsufficient() throws Exception
    {
        Duration challenge = ofSeconds(30L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("scope", "read:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, not(equalTo(0L)));
        assertFalse(guard.verify(sessionId, asList("read:stream", "write:stream")));
    }

    @Test
    public void shouldReauthorizeWhenExpirationLater() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(0L, 0L, 101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 60L);
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(0L, 0L, 101L, tokenPlus60);

        assertThat(sessionIdPlus60, equalTo(sessionIdPlus10));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldReauthorizeWhenScopeBroader() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(0L, 0L, 101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 60L);
        claims.setClaim("scope", "read:stream write:stream");
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(0L, 0L, 101L, tokenPlus60);

        assertThat(sessionIdPlus60, equalTo(sessionIdPlus10));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldNotReauthorizeWhenExpirationEarlier() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(0L, 0L, 101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 5L);
        String tokenPlus5 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus5 = guard.reauthorize(0L, 0L, 101L, tokenPlus5);

        assertThat(sessionIdPlus5, equalTo(sessionIdPlus10));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
    }

    @Test
    public void shouldNotReauthorizeWhenScopeNarrower() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(0L, 0L, 101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 60L);
        claims.setClaim("scope", "read:stream");
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(0L, 0L, 101L, tokenPlus60);

        assertThat(sessionIdPlus60, not(equalTo(sessionIdPlus10)));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiresAt(sessionIdPlus60), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldNotReauthorizeWhenSubjectDiffers() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(0L, 0L, 101L, tokenPlus10);

        claims.setClaim("sub", "otherSubject");
        claims.setClaim("exp", now.getEpochSecond() + 60L);
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(0L, 0L, 101L, tokenPlus60);

        assertThat(sessionIdPlus60, not(equalTo(sessionIdPlus10)));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiresAt(sessionIdPlus60), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldNotReauthorizeWhenContextDiffers() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String tokenPlus10 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus10 = guard.reauthorize(0L, 0L, 101L, tokenPlus10);

        claims.setClaim("exp", now.getEpochSecond() + 60L);
        String tokenPlus60 = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionIdPlus60 = guard.reauthorize(0L, 0L, 202L, tokenPlus60);

        assertThat(sessionIdPlus60, not(equalTo(sessionIdPlus10)));
        assertThat(guard.expiresAt(sessionIdPlus10), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiresAt(sessionIdPlus60), equalTo(ofSeconds(now.getEpochSecond() + 60L).toMillis()));
    }

    @Test
    public void shouldDeauthorize() throws Exception
    {
        Duration challenge = ofSeconds(30L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        guard.deauthorize(sessionId);
    }

    @Test
    public void shouldAuthorizeWithCustomRole() throws Exception
    {
        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .roles("realm_access.roles")
            .key(RFC7515_RS256_CONFIG)
            .challenge(challenge)
            .build();
        JwtGuardHandler guard = new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("realm_access",
            Map.of("roles", List.of("default-roles-backend", "offline_access", "uma_authorization")));
        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = guard.reauthorize(0L, 0L, 101L, token);

        assertThat(sessionId, not(equalTo(0L)));
        assertThat(guard.identity(sessionId), equalTo("testSubject"));
        assertThat(guard.expiresAt(sessionId), equalTo(ofSeconds(now.getEpochSecond() + 10L).toMillis()));
        assertThat(guard.expiringAt(sessionId), equalTo(ofSeconds(now.getEpochSecond() + 10L).minus(challenge).toMillis()));
        assertTrue(guard.verify(sessionId, asList("default-roles-backend", "offline_access", "uma_authorization")));
        assertFalse(guard.verify(sessionId, asList("admin")));
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
