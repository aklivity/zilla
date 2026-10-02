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

import static io.aklivity.zilla.runtime.common.jwt.JwtTestKeys.RFC7515_ES256_X;
import static io.aklivity.zilla.runtime.common.jwt.JwtTestKeys.RFC7515_ES256_Y;
import static io.aklivity.zilla.runtime.common.jwt.JwtTestKeys.RFC7515_RS256_E;
import static io.aklivity.zilla.runtime.common.jwt.JwtTestKeys.RFC7515_RS256_N;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class JwkTest
{
    @Test
    void shouldParseRsaKey() throws Exception
    {
        JsonObject json = Json.createObjectBuilder()
            .add("kty", "RSA")
            .add("kid", "test")
            .add("alg", "RS256")
            .add("use", "sig")
            .add("n", RFC7515_RS256_N)
            .add("e", RFC7515_RS256_E)
            .build();

        Jwk jwk = Jwk.parse(json);

        assertEquals("RSA", jwk.keyType());
        assertEquals("test", jwk.keyId());
        assertEquals("RS256", jwk.algorithm());
        assertEquals("sig", jwk.use());

        RSAPublicKey expected = (RSAPublicKey) JwtTestKeys.RFC7515_RS256.getPublic();
        RSAPublicKey actual = (RSAPublicKey) jwk.publicKey();
        assertEquals(expected.getModulus(), actual.getModulus());
        assertEquals(BigInteger.valueOf(65537L), actual.getPublicExponent());
    }

    @Test
    void shouldParseRsaKeyWithStandardPaddedBase64() throws Exception
    {
        JsonObject json = Json.createObjectBuilder()
            .add("kty", "RSA")
            .add("n", JwtTestKeys.standardBase64(RFC7515_RS256_N))
            .add("e", JwtTestKeys.standardBase64(RFC7515_RS256_E))
            .build();

        RSAPublicKey expected = (RSAPublicKey) JwtTestKeys.RFC7515_RS256.getPublic();

        assertEquals(expected.getModulus(), ((RSAPublicKey) Jwk.parse(json).publicKey()).getModulus());
    }

    @Test
    void shouldParseOptionalMembersAbsent() throws Exception
    {
        Jwk jwk = Jwk.parse(Json.createObjectBuilder()
            .add("kty", "RSA")
            .add("n", RFC7515_RS256_N)
            .add("e", RFC7515_RS256_E)
            .build());

        assertNull(jwk.keyId());
        assertNull(jwk.algorithm());
        assertNull(jwk.use());
    }

    @Test
    void shouldParseEcKeyFromRfc7515() throws Exception
    {
        JsonObject json = Json.createObjectBuilder()
            .add("kty", "EC")
            .add("crv", "P-256")
            .add("x", RFC7515_ES256_X)
            .add("y", RFC7515_ES256_Y)
            .build();

        ECPublicKey expected = (ECPublicKey) JwtTestKeys.RFC7515_ES256.getPublic();
        ECPublicKey actual = (ECPublicKey) Jwk.parse(json).publicKey();

        assertEquals("EC", Jwk.parse(json).keyType());
        assertEquals(expected.getW(), actual.getW());
    }

    @Test
    void shouldParseGeneratedEcKeys() throws Exception
    {
        for (var pair : new KeyPair[] {JwtTestKeys.EC_P256, JwtTestKeys.EC_P384, JwtTestKeys.EC_P521})
        {
            ECPublicKey expected = (ECPublicKey) pair.getPublic();
            ECPublicKey actual = (ECPublicKey) Jwk.parse(JwtTestKeys.jwk(expected, "ec", null)).publicKey();

            assertEquals(expected.getW(), actual.getW());
            assertEquals(expected.getParams().getCurve(), actual.getParams().getCurve());
        }
    }

    @Test
    void shouldIgnoreUnknownMembers() throws Exception
    {
        JsonObject json = Json.createObjectBuilder()
            .add("kty", "RSA")
            .add("n", RFC7515_RS256_N)
            .add("e", RFC7515_RS256_E)
            .add("x5t", "ignored")
            .build();

        assertEquals("RSA", Jwk.parse(json).keyType());
    }

    @Test
    void shouldRejectMissingOrUnsupportedKeyType()
    {
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder().build()));
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "oct")
            .add("k", "AAAA")
            .build()));
    }

    @Test
    void shouldParseOkpKeyFromRfc8037() throws Exception
    {
        Jwk jwk = Jwk.parse(Json.createObjectBuilder()
            .add("kty", "OKP")
            .add("crv", "Ed25519")
            .add("x", JwtTestKeys.RFC8037_ED25519_X)
            .build());

        assertEquals("OKP", jwk.keyType());
        assertEquals("Ed25519", ((EdECPublicKey) jwk.publicKey()).getParams().getName());
    }

    @Test
    void shouldParseGeneratedOkpKeys() throws Exception
    {
        for (var pair : new KeyPair[] {JwtTestKeys.ED25519, JwtTestKeys.ED448})
        {
            EdECPublicKey expected = (EdECPublicKey) pair.getPublic();
            EdECPublicKey actual = (EdECPublicKey) Jwk.parse(JwtTestKeys.jwk(expected, "ed", null)).publicKey();

            assertEquals(expected.getPoint().getY(), actual.getPoint().getY());
            assertEquals(expected.getPoint().isXOdd(), actual.getPoint().isXOdd());
            assertEquals(expected.getParams().getName(), actual.getParams().getName());
        }
    }

    @Test
    void shouldRejectIncompleteOrUnsupportedOkpKey()
    {
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "OKP")
            .add("x", JwtTestKeys.RFC8037_ED25519_X)
            .build()));
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "OKP")
            .add("crv", "Ed25519")
            .build()));
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "OKP")
            .add("crv", "X25519")
            .add("x", JwtTestKeys.RFC8037_ED25519_X)
            .build()));
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "OKP")
            .add("crv", "Ed25519")
            .add("x", "AAAA")
            .build()));
    }

    @Test
    void shouldRejectIncompleteRsaKey()
    {
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "RSA")
            .add("e", RFC7515_RS256_E)
            .build()));
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "RSA")
            .add("n", RFC7515_RS256_N)
            .build()));
    }

    @Test
    void shouldRejectIncompleteEcKey()
    {
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "EC")
            .add("x", RFC7515_ES256_X)
            .add("y", RFC7515_ES256_Y)
            .build()));
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "EC")
            .add("crv", "P-256")
            .add("y", RFC7515_ES256_Y)
            .build()));
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "EC")
            .add("crv", "P-256")
            .add("x", RFC7515_ES256_X)
            .build()));
    }

    @Test
    void shouldRejectUnknownCurve()
    {
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "EC")
            .add("crv", "secp256k1")
            .add("x", RFC7515_ES256_X)
            .add("y", RFC7515_ES256_Y)
            .build()));
    }

    @Test
    void shouldRejectPointNotOnCurve()
    {
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "EC")
            .add("crv", "P-256")
            .add("x", RFC7515_ES256_X)
            .add("y", "y" + RFC7515_ES256_Y.substring(1))
            .build()));
    }

    @Test
    void shouldRejectNonStringMembers()
    {
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "RSA")
            .add("n", 1)
            .add("e", RFC7515_RS256_E)
            .build()));
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", 1)
            .build()));
    }

    @Test
    void shouldRejectInvalidBase64Members()
    {
        assertThrows(JwtException.class, () -> Jwk.parse(Json.createObjectBuilder()
            .add("kty", "RSA")
            .add("n", "!!!")
            .add("e", RFC7515_RS256_E)
            .build()));
    }
}
