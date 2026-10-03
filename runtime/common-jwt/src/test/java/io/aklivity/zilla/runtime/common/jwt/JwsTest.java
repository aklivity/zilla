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
package io.aklivity.zilla.runtime.common.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;

import jakarta.json.Json;

import org.junit.jupiter.api.Test;

class JwsTest
{
    private static final String PAYLOAD = "{\"sub\":\"alice\"}";

    private static final Object[][] ALGORITHMS =
    {
        {JwsAlgorithm.RS256, JwtTestKeys.RSA_2048},
        {JwsAlgorithm.RS384, JwtTestKeys.RSA_2048},
        {JwsAlgorithm.RS512, JwtTestKeys.RSA_2048},
        {JwsAlgorithm.PS256, JwtTestKeys.RSA_2048},
        {JwsAlgorithm.PS384, JwtTestKeys.RSA_2048},
        {JwsAlgorithm.PS512, JwtTestKeys.RSA_2048},
        {JwsAlgorithm.ES256, JwtTestKeys.EC_P256},
        {JwsAlgorithm.ES384, JwtTestKeys.EC_P384},
        {JwsAlgorithm.ES512, JwtTestKeys.EC_P521},
        {JwsAlgorithm.EDDSA, JwtTestKeys.ED25519},
        {JwsAlgorithm.EDDSA, JwtTestKeys.ED448}
    };

    @Test
    void shouldSignAndVerifyEachAlgorithm() throws Exception
    {
        for (Object[] row : ALGORITHMS)
        {
            JwsAlgorithm algorithm = (JwsAlgorithm) row[0];
            KeyPair pair = (KeyPair) row[1];

            String token = Jws.sign(algorithm, pair.getPrivate(), "test", PAYLOAD);
            Jws jws = Jws.parse(token);
            Jwk jwk = Jwk.parse(JwtTestKeys.jwk(pair.getPublic(), "test", algorithm.joseName()));

            assertEquals(algorithm.joseName(), jws.algorithm(), algorithm.joseName());
            assertEquals("test", jws.keyId(), algorithm.joseName());
            assertEquals(PAYLOAD, jws.verifiedPayload(jwk), algorithm.joseName());
        }
    }

    @Test
    void shouldVerifyRfc7515RsaVector() throws Exception
    {
        Jws jws = Jws.parse(JwtTestKeys.RFC7515_RS256_TOKEN);
        Jwk jwk = jwk(JwtTestKeys.RFC7515_RS256);

        assertEquals(JwtTestKeys.RFC7515_PAYLOAD, jws.verifiedPayload(jwk));
    }

    @Test
    void shouldVerifyRfc7515EcVector() throws Exception
    {
        Jws jws = Jws.parse(JwtTestKeys.RFC7515_ES256_TOKEN);
        Jwk jwk = jwk(JwtTestKeys.RFC7515_ES256);

        assertEquals(JwtTestKeys.RFC7515_PAYLOAD, jws.verifiedPayload(jwk));
    }

    @Test
    void shouldVerifyRfc8037EdDsaVector() throws Exception
    {
        Jws jws = Jws.parse(JwtTestKeys.RFC8037_ED25519_TOKEN);
        Jwk jwk = Jwk.parse(Json.createObjectBuilder()
            .add("kty", "OKP")
            .add("crv", "Ed25519")
            .add("x", JwtTestKeys.RFC8037_ED25519_X)
            .build());

        assertEquals("Example of Ed25519 signing", jws.verifiedPayload(jwk));
    }

    @Test
    void shouldVerifyWithKeyThatHasNoAlgorithm() throws Exception
    {
        String token = Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_2048.getPrivate(), "test", PAYLOAD);

        assertNotNull(Jws.parse(token).verifiedPayload(jwk(JwtTestKeys.RSA_2048)));
    }

    @Test
    void shouldVerifyRfc7515Keys() throws Exception
    {
        String rsa = Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RFC7515_RS256.getPrivate(), null, PAYLOAD);
        String ec = Jws.sign(JwsAlgorithm.ES256, JwtTestKeys.RFC7515_ES256.getPrivate(), null, PAYLOAD);

        assertNotNull(Jws.parse(rsa).verifiedPayload(jwk(JwtTestKeys.RFC7515_RS256)));
        assertNotNull(Jws.parse(ec).verifiedPayload(jwk(JwtTestKeys.RFC7515_ES256)));
    }

    @Test
    void shouldOmitKeyIdWhenAbsent() throws Exception
    {
        Jws jws = Jws.parse(Jws.sign(JwsAlgorithm.ES256, JwtTestKeys.EC_P256.getPrivate(), null, PAYLOAD));

        assertNull(jws.keyId());
        assertNull(jws.header().get("kid"));
        assertEquals("ES256", jws.header().getString("alg"));
    }

    @Test
    void shouldExposeUnverifiedPayload() throws Exception
    {
        Jws jws = Jws.parse(Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_2048.getPrivate(), "test", PAYLOAD));

        assertEquals(PAYLOAD, jws.unverifiedPayload());
    }

    @Test
    void shouldFailVerificationWithOtherKey() throws Exception
    {
        String token = Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_2048.getPrivate(), "test", PAYLOAD);

        assertNull(Jws.parse(token).verifiedPayload(jwk(JwtTestKeys.RSA_2048_OTHER)));
    }

    @Test
    void shouldFailVerificationWhenPayloadTampered() throws Exception
    {
        String[] parts = Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_2048.getPrivate(), "test", PAYLOAD).split("\\.");
        String mallory = Base64Url.encode("{\"sub\":\"mallory\"}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + mallory + "." + parts[2];

        assertNull(Jws.parse(tampered).verifiedPayload(jwk(JwtTestKeys.RSA_2048)));
    }

    @Test
    void shouldFailVerificationWhenHeaderTampered() throws Exception
    {
        String[] parts = Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_2048.getPrivate(), "test", PAYLOAD).split("\\.");
        String header = Base64Url.encode("{\"alg\":\"RS256\",\"kid\":\"other\"}".getBytes(StandardCharsets.UTF_8));

        assertNull(Jws.parse(header + "." + parts[1] + "." + parts[2])
            .verifiedPayload(jwk(JwtTestKeys.RSA_2048)));
    }

    @Test
    void shouldFailVerificationWhenSignatureMissingOrTruncated() throws Exception
    {
        for (Object[] row : ALGORITHMS)
        {
            JwsAlgorithm algorithm = (JwsAlgorithm) row[0];
            KeyPair pair = (KeyPair) row[1];
            Jwk jwk = Jwk.parse(JwtTestKeys.jwk(pair.getPublic(), null, null));
            String[] parts = Jws.sign(algorithm, pair.getPrivate(), null, PAYLOAD).split("\\.");

            String empty = parts[0] + "." + parts[1] + ".";
            String truncated = parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() / 2);

            assertNull(Jws.parse(empty).verifiedPayload(jwk), algorithm.joseName());
            assertNull(Jws.parse(truncated).verifiedPayload(jwk), algorithm.joseName());
        }
    }

    @Test
    void shouldVerifyTokensWithPaddedSegments() throws Exception
    {
        String[] parts = Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_2048.getPrivate(), "test", PAYLOAD).split("\\.");
        String signature = JwtTestKeys.standardBase64(parts[2]);

        assertNotNull(Jws.parse(parts[0] + "." + parts[1] + "." + signature)
            .verifiedPayload(jwk(JwtTestKeys.RSA_2048)));
    }

    @Test
    void shouldRejectAlgorithmNone() throws Exception
    {
        String header = Base64Url.encode("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64Url.encode(PAYLOAD.getBytes(StandardCharsets.UTF_8));
        Jwk jwk = jwk(JwtTestKeys.RSA_2048);

        assertThrows(JwtException.class, () -> Jws.parse(header + "." + payload + ".").verifiedPayload(jwk));
    }

    @Test
    void shouldRejectUnsupportedOrMissingAlgorithm() throws Exception
    {
        Jwk jwk = jwk(JwtTestKeys.RSA_2048);
        String payload = Base64Url.encode(PAYLOAD.getBytes(StandardCharsets.UTF_8));

        String hmac = Base64Url.encode("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String missing = Base64Url.encode("{\"kid\":\"test\"}".getBytes(StandardCharsets.UTF_8));

        assertThrows(JwtException.class, () -> Jws.parse(hmac + "." + payload + ".AAAA").verifiedPayload(jwk));
        assertThrows(JwtException.class, () -> Jws.parse(missing + "." + payload + ".AAAA").verifiedPayload(jwk));
        assertNull(Jws.parse(missing + "." + payload + ".AAAA").algorithm());
    }

    @Test
    void shouldRejectKeyTypeMismatch() throws Exception
    {
        String rsa = Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_2048.getPrivate(), null, PAYLOAD);
        String pss = Jws.sign(JwsAlgorithm.PS256, JwtTestKeys.RSA_2048.getPrivate(), null, PAYLOAD);
        String ec = Jws.sign(JwsAlgorithm.ES256, JwtTestKeys.EC_P256.getPrivate(), null, PAYLOAD);
        Jwk rsaKey = jwk(JwtTestKeys.RSA_2048);
        Jwk ecKey = jwk(JwtTestKeys.EC_P256);

        assertThrows(JwtException.class, () -> Jws.parse(rsa).verifiedPayload(ecKey));
        assertThrows(JwtException.class, () -> Jws.parse(pss).verifiedPayload(ecKey));
        assertThrows(JwtException.class, () -> Jws.parse(ec).verifiedPayload(rsaKey));
    }

    @Test
    void shouldRejectEdDsaKeyTypeMismatch() throws Exception
    {
        String eddsa = Jws.sign(JwsAlgorithm.EDDSA, JwtTestKeys.ED25519.getPrivate(), null, PAYLOAD);
        String rsa = Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_2048.getPrivate(), null, PAYLOAD);
        Jwk rsaKey = jwk(JwtTestKeys.RSA_2048);
        Jwk edKey = jwk(JwtTestKeys.ED25519);

        assertThrows(JwtException.class, () -> Jws.parse(eddsa).verifiedPayload(rsaKey));
        assertThrows(JwtException.class, () -> Jws.parse(rsa).verifiedPayload(edKey));
    }

    @Test
    void shouldRejectCurveMismatch() throws Exception
    {
        String es256 = Jws.sign(JwsAlgorithm.ES256, JwtTestKeys.EC_P256.getPrivate(), null, PAYLOAD);
        String es384 = Jws.sign(JwsAlgorithm.ES384, JwtTestKeys.EC_P384.getPrivate(), null, PAYLOAD);
        Jwk p256 = jwk(JwtTestKeys.EC_P256);
        Jwk p384 = jwk(JwtTestKeys.EC_P384);

        assertThrows(JwtException.class, () -> Jws.parse(es256).verifiedPayload(p384));
        assertThrows(JwtException.class, () -> Jws.parse(es384).verifiedPayload(p256));
    }

    @Test
    void shouldRejectKeyAlgorithmMismatch() throws Exception
    {
        String token = Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_2048.getPrivate(), null, PAYLOAD);
        Jwk jwk = jwk(JwtTestKeys.RSA_2048, "RS512");

        assertThrows(JwtException.class, () -> Jws.parse(token).verifiedPayload(jwk));
    }

    @Test
    void shouldRejectWeakRsaKeyOnVerify() throws Exception
    {
        String header = Base64Url.encode("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64Url.encode(PAYLOAD.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        String token = signingInput + "." + Base64Url.encode(rsaSign(JwtTestKeys.RSA_1024.getPrivate(), signingInput));
        Jwk weak = jwk(JwtTestKeys.RSA_1024);

        assertThrows(JwtException.class, () -> Jws.parse(token).verifiedPayload(weak));
    }

    @Test
    void shouldRejectCriticalHeader() throws Exception
    {
        String header = Base64Url.encode("{\"alg\":\"RS256\",\"crit\":[\"exp\"],\"exp\":1}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64Url.encode(PAYLOAD.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        String token = signingInput + "." + Base64Url.encode(rsaSign(JwtTestKeys.RSA_2048.getPrivate(), signingInput));
        Jwk jwk = jwk(JwtTestKeys.RSA_2048);

        assertThrows(JwtException.class, () -> Jws.parse(token).verifiedPayload(jwk));
    }

    @Test
    void shouldRejectMalformedCompactSerialization()
    {
        assertThrows(JwtException.class, () -> Jws.parse(null));
        assertThrows(JwtException.class, () -> Jws.parse(""));
        assertThrows(JwtException.class, () -> Jws.parse("a.b"));
        assertThrows(JwtException.class, () -> Jws.parse("a.b.c.d"));
        assertThrows(JwtException.class, () -> Jws.parse("a.b.c.d.e"));
    }

    @Test
    void shouldRejectMalformedHeader()
    {
        String payload = Base64Url.encode(PAYLOAD.getBytes(StandardCharsets.UTF_8));

        assertThrows(JwtException.class, () -> Jws.parse("!!!." + payload + ".AAAA"));
        assertThrows(JwtException.class, () -> Jws.parse(
            Base64Url.encode("[]".getBytes(StandardCharsets.UTF_8)) + "." + payload + ".AAAA"));
        assertThrows(JwtException.class, () -> Jws.parse(
            Base64Url.encode("{\"alg\":\"RS256\",\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "." + payload + ".AAAA"));
        assertThrows(JwtException.class, () -> Jws.parse(
            Base64Url.encode("{\"alg\":1}".getBytes(StandardCharsets.UTF_8)) + "." + payload + ".AAAA"));
    }

    @Test
    void shouldRejectMalformedPayloadEncoding() throws Exception
    {
        String header = Base64Url.encode("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));

        assertThrows(JwtException.class, () -> Jws.parse(header + ".!!!.AAAA"));
    }

    @Test
    void shouldRejectSigningWithMismatchedKey()
    {
        assertThrows(JwtException.class,
            () -> Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.EC_P256.getPrivate(), null, PAYLOAD));
        assertThrows(JwtException.class,
            () -> Jws.sign(JwsAlgorithm.PS256, JwtTestKeys.EC_P256.getPrivate(), null, PAYLOAD));
        assertThrows(JwtException.class,
            () -> Jws.sign(JwsAlgorithm.ES256, JwtTestKeys.RSA_2048.getPrivate(), null, PAYLOAD));
        assertThrows(JwtException.class,
            () -> Jws.sign(JwsAlgorithm.ES256, JwtTestKeys.EC_P384.getPrivate(), null, PAYLOAD));
        assertThrows(JwtException.class,
            () -> Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.RSA_1024.getPrivate(), null, PAYLOAD));
        assertThrows(JwtException.class,
            () -> Jws.sign(JwsAlgorithm.EDDSA, JwtTestKeys.RSA_2048.getPrivate(), null, PAYLOAD));
        assertThrows(JwtException.class,
            () -> Jws.sign(JwsAlgorithm.RS256, JwtTestKeys.ED25519.getPrivate(), null, PAYLOAD));
    }

    @Test
    void shouldLookUpAlgorithmByName()
    {
        assertEquals(JwsAlgorithm.PS384, JwsAlgorithm.of("PS384"));
        assertEquals(JwsAlgorithm.EDDSA, JwsAlgorithm.of("EdDSA"));
        assertEquals("EdDSA", JwsAlgorithm.EDDSA.joseName());
        assertNull(JwsAlgorithm.of("none"));
        assertNull(JwsAlgorithm.of("HS256"));
        assertNull(JwsAlgorithm.of(null));
    }

    private static Jwk jwk(
        KeyPair pair) throws Exception
    {
        return jwk(pair, null);
    }

    private static Jwk jwk(
        KeyPair pair,
        String algorithm) throws Exception
    {
        return Jwk.parse(JwtTestKeys.jwk(pair.getPublic(), null, algorithm));
    }

    private static byte[] rsaSign(
        PrivateKey key,
        String signingInput) throws Exception
    {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signature.sign();
    }
}
