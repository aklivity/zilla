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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;

class Jose4jCompatibilityTest
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
    void shouldVerifyTokensSignedByJose4j() throws Exception
    {
        for (Object[] row : ALGORITHMS)
        {
            JwsAlgorithm algorithm = (JwsAlgorithm) row[0];
            KeyPair pair = (KeyPair) row[1];

            JsonWebSignature signer = new JsonWebSignature();
            signer.setPayload(PAYLOAD);
            signer.setKey(pair.getPrivate());
            signer.setKeyIdHeaderValue("test");
            signer.setAlgorithmHeaderValue(algorithm.joseName());

            Jws jws = Jws.parse(signer.getCompactSerialization());
            Jwk jwk = Jwk.parse(JwtTestKeys.jwk(pair.getPublic(), "test", algorithm.joseName()));

            assertEquals(PAYLOAD, jws.verifiedPayload(jwk), algorithm.joseName());
        }
    }

    @Test
    void shouldProduceTokensVerifiedByJose4j() throws Exception
    {
        for (Object[] row : ALGORITHMS)
        {
            JwsAlgorithm algorithm = (JwsAlgorithm) row[0];
            KeyPair pair = (KeyPair) row[1];

            JsonWebSignature verifier = new JsonWebSignature();
            verifier.setCompactSerialization(Jws.sign(algorithm, pair.getPrivate(), "test", PAYLOAD));
            verifier.setKey(pair.getPublic());

            assertTrue(verifier.verifySignature(), algorithm.joseName());
            assertEquals(PAYLOAD, verifier.getPayload(), algorithm.joseName());
            assertEquals("test", verifier.getKeyIdHeaderValue(), algorithm.joseName());
        }
    }

    @Test
    void shouldVerifyJose4jSignatureOverPaddedSegments() throws Exception
    {
        JsonWebSignature signer = new JsonWebSignature();
        signer.setPayload(PAYLOAD);
        signer.setKey(JwtTestKeys.RSA_2048.getPrivate());
        signer.setAlgorithmHeaderValue("RS256");

        String[] parts = signer.getCompactSerialization().split("\\.");
        String token = parts[0] + "." + parts[1] + "." + JwtTestKeys.standardBase64(parts[2]);

        JsonWebSignature jose4j = new JsonWebSignature();
        jose4j.setCompactSerialization(token);
        jose4j.setKey(JwtTestKeys.RSA_2048.getPublic());

        assertTrue(jose4j.verifySignature());
        assertEquals(PAYLOAD, Jws.parse(token).verifiedPayload(Jwk.parse(
            JwtTestKeys.jwk(JwtTestKeys.RSA_2048.getPublic(), null, null))));
    }

    @Test
    void shouldParseKeysLikeJose4j() throws Exception
    {
        KeyPair[] pairs =
        {
            JwtTestKeys.RFC7515_RS256, JwtTestKeys.RSA_2048, JwtTestKeys.RFC7515_ES256,
            JwtTestKeys.EC_P256, JwtTestKeys.EC_P384, JwtTestKeys.EC_P521, JwtTestKeys.ED25519, JwtTestKeys.ED448
        };

        for (KeyPair pair : pairs)
        {
            JsonObject json = JwtTestKeys.jwk(pair.getPublic(), "test", null);

            JsonWebKey expected = JsonWebKey.Factory.newJwk(members(json));

            assertArrayEquals(expected.getKey().getEncoded(), Jwk.parse(json).publicKey().getEncoded(), json.toString());
        }
    }

    @Test
    void shouldParseStandardPaddedKeyMembersLikeJose4j() throws Exception
    {
        Map<String, Object> members = new HashMap<>();
        members.put("kty", "RSA");
        members.put("n", JwtTestKeys.standardBase64(JwtTestKeys.RFC7515_RS256_N));
        members.put("e", JwtTestKeys.standardBase64(JwtTestKeys.RFC7515_RS256_E));

        JsonWebKey expected = JsonWebKey.Factory.newJwk(members);
        JsonObject json = StrictJson.PROVIDER.createObjectBuilder()
            .add("kty", "RSA")
            .add("n", JwtTestKeys.standardBase64(JwtTestKeys.RFC7515_RS256_N))
            .add("e", JwtTestKeys.standardBase64(JwtTestKeys.RFC7515_RS256_E))
            .build();

        assertArrayEquals(expected.getKey().getEncoded(), Jwk.parse(json).publicKey().getEncoded());
    }

    @Test
    void shouldReadClaimsLikeJose4j() throws Exception
    {
        List<String> documents = List.of(
            "{}",
            "{\"iss\":\"i\",\"sub\":\"s\",\"aud\":\"a\",\"exp\":1790000000,\"nbf\":1789999990}",
            "{\"aud\":[\"a\",\"b\"],\"exp\":1790000000.9}",
            "{\"s\":\"x\",\"i\":7,\"big\":12345678901234567890,\"d\":1.5,\"t\":true,\"n\":null,\"a\":[\"p\",2]," +
                "\"o\":{\"k\":{\"v\":\"w\"}}}");

        for (String document : documents)
        {
            org.jose4j.jwt.JwtClaims expected = org.jose4j.jwt.JwtClaims.parse(document);
            JwtClaims actual = JwtClaims.parse(document);

            assertEquals(expected.getIssuer(), actual.getIssuer(), document);
            assertEquals(expected.getSubject(), actual.getSubject(), document);
            assertEquals(expected.getAudience(), actual.getAudience(), document);
            assertEquals(seconds(expected.getExpirationTime()), seconds(actual.getExpirationTime()), document);
            assertEquals(seconds(expected.getNotBefore()), seconds(actual.getNotBefore()), document);

            for (String name : List.of("s", "i", "big", "d", "t", "n", "a", "o", "missing"))
            {
                assertEquals(expected.getClaimValue(name), actual.getClaimValue(name), document + " " + name);
            }
        }
    }

    @Test
    void shouldRejectMalformedClaimsLikeJose4j() throws Exception
    {
        for (String document : List.of("{\"sub\":42}", "{\"aud\":1}", "{\"aud\":[\"a\",1]}", "{\"exp\":\"soon\"}",
            "{\"nbf\":true}"))
        {
            org.jose4j.jwt.JwtClaims expected = org.jose4j.jwt.JwtClaims.parse(document);
            JwtClaims actual = JwtClaims.parse(document);

            assertThrows(MalformedClaimException.class, () -> read(expected), document);
            assertThrows(JwtException.class, () -> read(actual), document);
        }
    }

    @Test
    void shouldRejectInvalidClaimDocumentsLikeJose4j()
    {
        for (String document : List.of("[1]", "\"x\"", "{", "", "{\"sub\":\"a\",\"sub\":\"b\"}", "{\"o\":{\"k\":1,\"k\":2}}"))
        {
            assertThrows(InvalidJwtException.class, () -> org.jose4j.jwt.JwtClaims.parse(document), document);
            assertThrows(JwtException.class, () -> JwtClaims.parse(document), document);
        }
    }

    @Test
    void shouldRejectUnsafeTokensLikeJose4j() throws Exception
    {
        String payload = Base64Url.encode(PAYLOAD.getBytes(StandardCharsets.UTF_8));
        Jwk rsa = Jwk.parse(JwtTestKeys.jwk(JwtTestKeys.RSA_2048.getPublic(), null, null));

        String none = Base64Url.encode("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "." + payload + ".";
        String hmac = Base64Url.encode("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8)) + "." + payload + ".AAAA";
        String critical = signed("{\"alg\":\"RS256\",\"crit\":[\"exp\"],\"exp\":1}", payload);

        for (String token : List.of(none, hmac, critical))
        {
            JsonWebSignature jose4j = new JsonWebSignature();
            jose4j.setCompactSerialization(token);
            jose4j.setKey(JwtTestKeys.RSA_2048.getPublic());

            assertThrows(JoseException.class, jose4j::verifySignature, token);
            assertThrows(JwtException.class, () -> Jws.parse(token).verifiedPayload(rsa), token);
        }
    }

    private static String signed(
        String headerJson,
        String payload) throws Exception
    {
        String signingInput = Base64Url.encode(headerJson.getBytes(StandardCharsets.UTF_8)) + "." + payload;

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(JwtTestKeys.RSA_2048.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));

        return signingInput + "." + Base64Url.encode(signature.sign());
    }

    private static Map<String, Object> members(
        JsonObject json)
    {
        Map<String, Object> members = new HashMap<>();
        json.forEach((name, value) -> members.put(name, ((JsonString) value).getString()));
        return members;
    }

    private static Long seconds(
        NumericDate date)
    {
        return date != null ? date.getValue() : null;
    }

    private static Long seconds(
        Instant instant)
    {
        return instant != null ? instant.getEpochSecond() : null;
    }

    private static void read(
        org.jose4j.jwt.JwtClaims claims) throws Exception
    {
        claims.getSubject();
        claims.getAudience();
        claims.getExpirationTime();
        claims.getNotBefore();
    }

    private static void read(
        JwtClaims claims) throws Exception
    {
        claims.getSubject();
        claims.getAudience();
        claims.getExpirationTime();
        claims.getNotBefore();
    }
}
