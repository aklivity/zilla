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
import static io.aklivity.zilla.specs.guard.jwt.keys.JwtKeys.RFC7515_ES256;
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
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
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
        when(context.clock()).thenReturn(mock(Clock.class));
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    @Test
    public void shouldAuthorizeEachSupportedAlgorithm() throws Exception
    {
        for (String alg : new String[] {"RS256", "RS384", "RS512", "PS256", "PS384", "PS512"})
        {
            JwtGuardHandler guard = newGuard(rsaKey("test", alg));

            long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", alg, RFC7515_RS256, claims(Instant.now())));

            assertThat(alg, sessionId, not(equalTo(0L)));
            assertThat(alg, guard.identity(sessionId), equalTo("testSubject"));
        }
    }

    @Test
    public void shouldAuthorizeWithEs256() throws Exception
    {
        JwtGuardHandler guard = newGuard(ecKey("test", "ES256"));

        long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", "ES256", RFC7515_ES256, claims(Instant.now())));

        assertThat(sessionId, not(equalTo(0L)));
        assertThat(guard.identity(sessionId), equalTo("testSubject"));
    }

    @Test
    public void shouldAuthorizeWithEdDsa() throws Exception
    {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] encoded = pair.getPublic().getEncoded();
        byte[] raw = Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
        String x = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        JwtKeyConfig key = JwtKeyConfig.builder().kty("OKP").kid("test").alg("EdDSA").crv("Ed25519").x(x).build();
        JwtGuardHandler guard = newGuard(key);

        long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", "EdDSA", pair, claims(Instant.now())));

        assertThat(sessionId, not(equalTo(0L)));
    }

    @Test
    public void shouldAuthorizeWithStandardBase64KeyMembers() throws Exception
    {
        String n = Base64.getEncoder().encodeToString(Base64.getUrlDecoder().decode(RFC7515_RS256_CONFIG.n));
        JwtKeyConfig key = JwtKeyConfig.builder().kty("RSA").kid("test").alg("RS256").n(n).e("AQAB").build();
        JwtGuardHandler guard = newGuard(key);

        long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", "RS256", RFC7515_RS256, claims(Instant.now())));

        assertThat(sessionId, not(equalTo(0L)));
    }

    @Test
    public void shouldNotAuthorizeWhenSignatureTampered() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        String token = token("test", "RS256", RFC7515_RS256, claims(Instant.now()));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(guard.reauthorize(0L, 0L, 101L, tampered), equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenPayloadTampered() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        String[] parts = token("test", "RS256", RFC7515_RS256, claims(Instant.now())).split("\\.");
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            claims(Instant.now()).replace("testSubject", "mallory").getBytes(UTF_8));

        assertThat(guard.reauthorize(0L, 0L, 101L, parts[0] + "." + payload + "." + parts[2]), equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenAlgorithmNone() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        String token = unsigned("{\"alg\":\"none\",\"kid\":\"test\"}", claims(Instant.now()));

        assertThat(guard.reauthorize(0L, 0L, 101L, token), equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenKeyConfiguredWithAlgorithmNone() throws Exception
    {
        JwtKeyConfig key = JwtKeyConfig.builder().kty("RSA").kid("test").alg("none")
            .n(RFC7515_RS256_CONFIG.n).e(RFC7515_RS256_CONFIG.e).build();
        JwtGuardHandler guard = newGuard(key);
        String token = unsigned("{\"alg\":\"none\",\"kid\":\"test\"}", claims(Instant.now()));

        assertThat(guard.reauthorize(0L, 0L, 101L, token), equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenRsaTokenPresentedWithEcKey() throws Exception
    {
        JwtGuardHandler guard = newGuard(ecKey("test", "RS256"));

        long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", "RS256", RFC7515_RS256, claims(Instant.now())));

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenEcTokenPresentedWithRsaKey() throws Exception
    {
        JwtGuardHandler guard = newGuard(rsaKey("test", "ES256"));

        long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", "ES256", RFC7515_ES256, claims(Instant.now())));

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenKeyIdMissing() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);

        long sessionId = guard.reauthorize(0L, 0L, 101L, token(null, "RS256", RFC7515_RS256, claims(Instant.now())));

        assertThat(sessionId, equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenCriticalHeaderPresent() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        String header = "{\"alg\":\"RS256\",\"kid\":\"test\",\"crit\":[\"exp\"],\"exp\":1}";
        String token = signedWithHeader(header, claims(Instant.now()));

        assertThat(guard.reauthorize(0L, 0L, 101L, token), equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenHeaderHasDuplicateNames() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        String token = signedWithHeader("{\"alg\":\"RS256\",\"kid\":\"test\",\"kid\":\"other\"}", claims(Instant.now()));

        assertThat(guard.reauthorize(0L, 0L, 101L, token), equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenClaimsHaveDuplicateNames() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        String duplicate = "{\"iss\":\"test issuer\",\"iss\":\"other issuer\",\"aud\":\"testAudience\"}";

        assertThat(guard.reauthorize(0L, 0L, 101L, token("test", "RS256", RFC7515_RS256, duplicate)), equalTo(0L));
    }

    @Test
    public void shouldNotAuthorizeWhenClaimsAreMalformed() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        long exp = Instant.now().getEpochSecond() + 10L;

        String[] malformed =
        {
            "{\"iss\":\"test issuer\",\"aud\":\"testAudience\",\"sub\":42,\"exp\":" + exp + "}",
            "{\"iss\":42,\"aud\":\"testAudience\",\"exp\":" + exp + "}",
            "{\"iss\":\"test issuer\",\"aud\":[\"testAudience\",1],\"exp\":" + exp + "}",
            "{\"iss\":\"test issuer\",\"aud\":\"testAudience\",\"exp\":\"soon\"}",
            "{\"iss\":\"test issuer\",\"aud\":\"testAudience\",\"nbf\":true}",
            "[\"test issuer\"]",
            "not json"
        };

        for (String claims : malformed)
        {
            assertThat(claims, guard.reauthorize(0L, 0L, 101L, token("test", "RS256", RFC7515_RS256, claims)), equalTo(0L));
        }
    }

    @Test
    public void shouldNotAuthorizeWhenAudienceMissing() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        String claims = "{\"iss\":\"test issuer\",\"sub\":\"testSubject\"}";

        assertThat(guard.reauthorize(0L, 0L, 101L, token("test", "RS256", RFC7515_RS256, claims)), equalTo(0L));
    }

    @Test
    public void shouldAuthorizeWhenAudienceIsArrayContainingConfiguredAudience() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        long exp = Instant.now().getEpochSecond() + 10L;
        String claims = "{\"iss\":\"test issuer\",\"aud\":[\"other\",\"testAudience\"]," +
            "\"sub\":\"testSubject\",\"exp\":" + exp + "}";

        long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", "RS256", RFC7515_RS256, claims));

        assertThat(sessionId, not(equalTo(0L)));
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

    @Test
    public void shouldAuthorizeWhenNotBeforeAlreadyReached() throws Exception
    {
        JwtGuardHandler guard = newGuard(RFC7515_RS256_CONFIG);
        long now = Instant.now().getEpochSecond();
        String claims = "{\"iss\":\"test issuer\",\"aud\":\"testAudience\",\"sub\":\"testSubject\"," +
            "\"nbf\":" + (now - 5L) + ",\"exp\":" + (now + 10L) + "}";

        long sessionId = guard.reauthorize(0L, 0L, 101L, token("test", "RS256", RFC7515_RS256, claims));

        assertThat(sessionId, not(equalTo(0L)));
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
