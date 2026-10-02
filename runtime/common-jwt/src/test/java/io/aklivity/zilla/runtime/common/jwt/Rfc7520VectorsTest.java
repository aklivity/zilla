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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

/**
 * JWS compact serializations from RFC 7520, the JOSE Cookbook.
 */
class Rfc7520VectorsTest
{
    private static final String KID = "bilbo.baggins@hobbiton.example";
    private static final String PAYLOAD =
        "It\u2019s a dangerous business, Frodo, going out your door. " +
        "You step onto the road, and if you don't keep your feet, " +
        "there\u2019s no knowing where you might be swept off to.";

    private static final String RSA_N =
        "n4EPtAOCc9AlkeQHPzHStgAbgs7bTZLwUBZdR8_KuKPEHLd4rHVTeT-O-XV2jRojdNhxJWTDvNd7nqQ0VEiZQHz_AJmSCpMa" +
        "JMRBSFKrKb2wqVwGU_NsYOYL-QtiWN2lbzcEe6XC0dApr5ydQLrHqkHHig3RBordaZ6Aj-oBHqFEHYpPe7Tpe-OfVfHd1E6c" +
        "S6M1FZcD1NNLYD5lFHpPI9bTwJlsde3uhGqC0ZCuEHg8lhzwOHrtIQbS0FVbb9k3-tVTU4fg_3L_vniUFAKwuCLqKnS2BYwd" +
        "q_mzSnbLY7h_qixoR7jig3__kRhuaxwUkRz5iaiQkqgc5gHdrNP5zw";
    private static final String EC_X =
        "AHKZLLOsCOzz5cY97ewNUajB957y-C-U88c3v13nmGZx6sYl_oJXu9A5RkTKqjqvjyekWF-7ytDyRXYgCF5cj0Kt";
    private static final String EC_Y =
        "AdymlHvOiLxXkEhayXQnNCvDX4h9htZaCJN34kfmC6pV5OhQHiraVySsUdaQkAgDPrwQrJmbnX9cwlGfP-HqHZR1";

    private static final String RSA_V15_TOKEN =
        "eyJhbGciOiJSUzI1NiIsImtpZCI6ImJpbGJvLmJhZ2dpbnNAaG9iYml0b24uZXhhbXBsZSJ9.SXTigJlzIGEgZGFuZ2Vyb3V" +
        "zIGJ1c2luZXNzLCBGcm9kbywgZ29pbmcgb3V0IHlvdXIgZG9vci4gWW91IHN0ZXAgb250byB0aGUgcm9hZCwgYW5kIGlmIHl" +
        "vdSBkb24ndCBrZWVwIHlvdXIgZmVldCwgdGhlcmXigJlzIG5vIGtub3dpbmcgd2hlcmUgeW91IG1pZ2h0IGJlIHN3ZXB0IG9" +
        "mZiB0by4.MRjdkly7_-oTPTS3AXP41iQIGKa80A0ZmTuV5MEaHoxnW2e5CZ5NlKtainoFmKZopdHM1O2U4mwzJdQx996ivp8" +
        "3xuglII7PNDi84wnB-BDkoBwA78185hX-Es4JIwmDLJK3lfWRa-XtL0RnltuYv746iYTh_qHRD68BNt1uSNCrUCTJDt5aAE6" +
        "x8wW1Kt9eRo4QPocSadnHXFxnt8Is9UzpERV0ePPQdLuW3IS_de3xyIrDaLGdjluPxUAhb6L2aXic1U12podGU0KLUQSE_oI" +
        "-ZnmKJ3F4uOZDnd6QZWJushZ41Axf_fcIe8u9ipH84ogoree7vjbU5y18kDquDg";
    private static final String RSA_PSS_TOKEN =
        "eyJhbGciOiJQUzM4NCIsImtpZCI6ImJpbGJvLmJhZ2dpbnNAaG9iYml0b24uZXhhbXBsZSJ9.SXTigJlzIGEgZGFuZ2Vyb3V" +
        "zIGJ1c2luZXNzLCBGcm9kbywgZ29pbmcgb3V0IHlvdXIgZG9vci4gWW91IHN0ZXAgb250byB0aGUgcm9hZCwgYW5kIGlmIHl" +
        "vdSBkb24ndCBrZWVwIHlvdXIgZmVldCwgdGhlcmXigJlzIG5vIGtub3dpbmcgd2hlcmUgeW91IG1pZ2h0IGJlIHN3ZXB0IG9" +
        "mZiB0by4.cu22eBqkYDKgIlTpzDXGvaFfz6WGoz7fUDcfT0kkOy42miAh2qyBzk1xEsnk2IpN6-tPid6VrklHkqsGqDqHCdP" +
        "6O8TTB5dDDItllVo6_1OLPpcbUrhiUSMxbbXUvdvWXzg-UD8biiReQFlfz28zGWVsdiNAUf8ZnyPEgVFn442ZdNqiVJRmBqr" +
        "YRXe8P_ijQ7p8Vdz0TTrxUeT3lm8d9shnr2lfJT8ImUjvAA2Xez2Mlp8cBE5awDzT0qI0n6uiP1aCN_2_jLAeQTlqRHtfa64" +
        "QQSUmFAAjVKPbByi7xho0uTOcbH510a6GYmJUAfmWjwZ6oD4ifKo8DYM-X72Eaw";
    private static final String ECDSA_TOKEN =
        "eyJhbGciOiJFUzUxMiIsImtpZCI6ImJpbGJvLmJhZ2dpbnNAaG9iYml0b24uZXhhbXBsZSJ9.SXTigJlzIGEgZGFuZ2Vyb3V" +
        "zIGJ1c2luZXNzLCBGcm9kbywgZ29pbmcgb3V0IHlvdXIgZG9vci4gWW91IHN0ZXAgb250byB0aGUgcm9hZCwgYW5kIGlmIHl" +
        "vdSBkb24ndCBrZWVwIHlvdXIgZmVldCwgdGhlcmXigJlzIG5vIGtub3dpbmcgd2hlcmUgeW91IG1pZ2h0IGJlIHN3ZXB0IG9" +
        "mZiB0by4.AE_R_YZCChjn4791jSQCrdPZCNYqHXCTZH0-JZGYNlaAjP2kqaluUIIUnC9qvbu9Plon7KRTzoNEuT4Va2cmL1e" +
        "JAQy3mtPBu_u_sDDyYjnAMDxXPn7XrT0lw-kvAD890jl8e2puQens_IEKBpHABlsbEPX6sFY8OcGDqoRuBomu9xQ2";
    private static final String HMAC_TOKEN =
        "eyJhbGciOiJIUzI1NiIsImtpZCI6IjAxOGMwYWU1LTRkOWItNDcxYi1iZmQ2LWVlZjMxNGJjNzAzNyJ9.SXTigJlzIGEgZGF" +
        "uZ2Vyb3VzIGJ1c2luZXNzLCBGcm9kbywgZ29pbmcgb3V0IHlvdXIgZG9vci4gWW91IHN0ZXAgb250byB0aGUgcm9hZCwgYW5" +
        "kIGlmIHlvdSBkb24ndCBrZWVwIHlvdXIgZmVldCwgdGhlcmXigJlzIG5vIGtub3dpbmcgd2hlcmUgeW91IG1pZ2h0IGJlIHN" +
        "3ZXB0IG9mZiB0by4.s0h6KThzkfBBBkLspW1h84VsJZFTsPPqMDA7g1Md7p0";

    @Test
    void shouldVerifyRsaV15Signature() throws Exception
    {
        Jws jws = Jws.parse(RSA_V15_TOKEN);

        assertEquals("RS256", jws.algorithm());
        assertEquals(KID, jws.keyId());
        assertEquals(PAYLOAD, jws.verifiedPayload(rsaKey()));
    }

    @Test
    void shouldVerifyRsaPssSignature() throws Exception
    {
        Jws jws = Jws.parse(RSA_PSS_TOKEN);

        assertEquals("PS384", jws.algorithm());
        assertEquals(KID, jws.keyId());
        assertEquals(PAYLOAD, jws.verifiedPayload(rsaKey()));
    }

    @Test
    void shouldVerifyEcdsaSignature() throws Exception
    {
        Jws jws = Jws.parse(ECDSA_TOKEN);

        assertEquals("ES512", jws.algorithm());
        assertEquals(KID, jws.keyId());
        assertEquals(PAYLOAD, jws.verifiedPayload(ecKey()));
    }

    @Test
    void shouldNotVerifyTamperedSignature() throws Exception
    {
        for (String token : new String[] {RSA_V15_TOKEN, RSA_PSS_TOKEN})
        {
            String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

            assertNull(Jws.parse(tampered).verifiedPayload(rsaKey()));
        }

        String tampered = ECDSA_TOKEN.substring(0, ECDSA_TOKEN.length() - 4) + "AAAA";
        assertNull(Jws.parse(tampered).verifiedPayload(ecKey()));
    }

    @Test
    void shouldNotVerifyWhenPayloadReplaced() throws Exception
    {
        String[] parts = RSA_V15_TOKEN.split("\\.");
        String replaced = parts[0] + "." + Base64Url.encode("{}".getBytes()) + "." + parts[2];

        assertNull(Jws.parse(replaced).verifiedPayload(rsaKey()));
    }

    @Test
    void shouldRejectKeyOfTheWrongTypeForTheAlgorithm() throws Exception
    {
        assertThrows(JwtException.class, () -> Jws.parse(RSA_V15_TOKEN).verifiedPayload(ecKey()));
        assertThrows(JwtException.class, () -> Jws.parse(RSA_PSS_TOKEN).verifiedPayload(ecKey()));
        assertThrows(JwtException.class, () -> Jws.parse(ECDSA_TOKEN).verifiedPayload(rsaKey()));
    }

    @Test
    void shouldRejectHmacIntegrityProtection() throws Exception
    {
        Jws jws = Jws.parse(HMAC_TOKEN);

        assertEquals("HS256", jws.algorithm());
        assertThrows(JwtException.class, () -> jws.verifiedPayload(rsaKey()));
        assertThrows(JwtException.class, () -> jws.verifiedPayload(ecKey()));
    }

    private static Jwk rsaKey() throws JwtException
    {
        JsonObject json = Json.createObjectBuilder()
            .add("kty", "RSA")
            .add("kid", KID)
            .add("use", "sig")
            .add("n", RSA_N)
            .add("e", "AQAB")
            .build();

        return Jwk.parse(json);
    }

    private static Jwk ecKey() throws JwtException
    {
        JsonObject json = Json.createObjectBuilder()
            .add("kty", "EC")
            .add("kid", KID)
            .add("use", "sig")
            .add("crv", "P-521")
            .add("x", EC_X)
            .add("y", EC_Y)
            .build();

        return Jwk.parse(json);
    }
}
