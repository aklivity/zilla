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

import static io.aklivity.zilla.config.guard.jwt.internal.keys.JwtKeyConfigs.RFC7515_RS256_CONFIG;
import static io.aklivity.zilla.runtime.engine.guard.GuardHandler.EXPIRES_NEVER;
import static io.aklivity.zilla.specs.guard.jwt.keys.JwtKeys.RFC7515_RS256;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import org.agrona.collections.MutableLong;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.guard.jwt.JwtKeyConfig;
import io.aklivity.zilla.config.guard.jwt.JwtOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class JwtGuardAlgorithmsTest
{
    private static final String EC_X = "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU";
    private static final String EC_Y = "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0";

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    @Test
    public void shouldTruncateFractionalExpiration() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        long exp = Instant.now().getEpochSecond() + 10L;
        String claims = "{\"iss\":\"test issuer\",\"aud\":\"testAudience\",\"sub\":\"testSubject\",\"exp\":" + exp + ".9}";

        long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", "RS256", RFC7515_RS256, claims));

        assertThat(guard.expiresAt(sessionId), equalTo(exp * 1000L));
    }

    @Test
    public void shouldAuthorizeWithoutExpirationAsNeverExpiring() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        String claims = "{\"iss\":\"test issuer\",\"aud\":\"testAudience\",\"sub\":\"testSubject\"}";

        long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", "RS256", RFC7515_RS256, claims));

        assertThat(sessionId, not(equalTo(0L)));
        assertThat(guard.expiresAt(sessionId), equalTo(EXPIRES_NEVER));
    }

    private JwtGuardHandler newGuard(
        JwtKeyConfig key)
    {
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .inject(identity())
            .issuer("test issuer")
            .audience("testAudience")
            .key(key)
            .build();

        return new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement, sessionId -> null);
    }

    private static JwtKeyConfig rsaKey(
        String kid,
        String alg)
    {
        return JwtKeyConfig.builder()
            .kty("RSA")
            .kid(kid)
            .alg(alg)
            .n(RFC7515_RS256_CONFIG.n)
            .e(RFC7515_RS256_CONFIG.e)
            .build();
    }

    private static JwtKeyConfig ecKey(
        String kid,
        String alg)
    {
        return JwtKeyConfig.builder()
            .kty("EC")
            .kid(kid)
            .alg(alg)
            .crv("P-256")
            .x(EC_X)
            .y(EC_Y)
            .build();
    }

    private static String claims(
        Instant now)
    {
        return "{\"iss\":\"test issuer\",\"aud\":\"testAudience\",\"sub\":\"testSubject\",\"exp\":" +
            (now.getEpochSecond() + 10L) + "}";
    }

    private static String token(
        String kid,
        String alg,
        KeyPair pair,
        String claims) throws Exception
    {
        return JwtGuardHandlerTest.sign(claims, kid, pair, alg);
    }

    private static String unsigned(
        String header,
        String claims)
    {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

        return encoder.encodeToString(header.getBytes(UTF_8)) + "." + encoder.encodeToString(claims.getBytes(UTF_8)) + ".";
    }

    private static String signedWithHeader(
        String header,
        String claims) throws Exception
    {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String signingInput = encoder.encodeToString(header.getBytes(UTF_8)) +
            "." + encoder.encodeToString(claims.getBytes(UTF_8));

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(RFC7515_RS256.getPrivate());
        signature.update(signingInput.getBytes(US_ASCII));

        return signingInput + "." + encoder.encodeToString(signature.sign());
    }
}
