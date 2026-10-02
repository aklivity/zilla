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

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JwtClaimsTest
{
    @Test
    void shouldReadStringClaims() throws Exception
    {
        JwtClaims claims = JwtClaims.parse("{\"iss\":\"issuer\",\"sub\":\"subject\",\"tid\":\"tenant\"}");

        assertEquals("issuer", claims.getIssuer());
        assertEquals("subject", claims.getSubject());
        assertEquals("tenant", claims.getStringClaim("tid"));
    }

    @Test
    void shouldReturnNullForAbsentClaims() throws Exception
    {
        JwtClaims claims = JwtClaims.parse("{}");

        assertNull(claims.getIssuer());
        assertNull(claims.getSubject());
        assertNull(claims.getStringClaim("tid"));
        assertEquals(List.of(), claims.getAudience());
        assertNull(claims.getNotBefore());
        assertNull(claims.getExpirationTime());
        assertNull(claims.getClaimValue("missing"));
    }

    @Test
    void shouldRejectNonStringNamedClaim() throws Exception
    {
        JwtClaims claims = JwtClaims.parse("{\"sub\":42,\"tid\":[\"a\"]}");

        assertThrows(JwtException.class, claims::getSubject);
        assertThrows(JwtException.class, () -> claims.getStringClaim("tid"));
    }

    @Test
    void shouldReadAudienceString() throws Exception
    {
        assertEquals(List.of("a"), JwtClaims.parse("{\"aud\":\"a\"}").getAudience());
    }

    @Test
    void shouldReadAudienceArray() throws Exception
    {
        assertEquals(List.of("a", "b"), JwtClaims.parse("{\"aud\":[\"a\",\"b\"]}").getAudience());
    }

    @Test
    void shouldRejectMalformedAudience() throws Exception
    {
        assertThrows(JwtException.class, () -> JwtClaims.parse("{\"aud\":[\"a\",1]}").getAudience());
        assertThrows(JwtException.class, () -> JwtClaims.parse("{\"aud\":1}").getAudience());
    }

    @Test
    void shouldReadNumericDates() throws Exception
    {
        JwtClaims claims = JwtClaims.parse("{\"exp\":1790000000,\"nbf\":1789999990}");

        assertEquals(Instant.ofEpochSecond(1790000000L), claims.getExpirationTime());
        assertEquals(Instant.ofEpochSecond(1789999990L), claims.getNotBefore());
    }

    @Test
    void shouldTruncateFractionalNumericDate() throws Exception
    {
        JwtClaims claims = JwtClaims.parse("{\"exp\":1790000000.9}");

        assertEquals(Instant.ofEpochSecond(1790000000L), claims.getExpirationTime());
    }

    @Test
    void shouldRejectNonNumericDate() throws Exception
    {
        assertThrows(JwtException.class, () -> JwtClaims.parse("{\"exp\":\"soon\"}").getExpirationTime());
        assertThrows(JwtException.class, () -> JwtClaims.parse("{\"nbf\":true}").getNotBefore());
    }

    @Test
    void shouldExposePlainClaimValues() throws Exception
    {
        JwtClaims claims = JwtClaims.parse("""
            {"s":"x","i":7,"d":1.5,"t":true,"f":false,"n":null,"a":["p",2],"o":{"k":{"v":"w"}}}""");

        assertEquals("x", claims.getClaimValue("s"));
        assertEquals(7L, claims.getClaimValue("i"));
        assertEquals(1.5d, claims.getClaimValue("d"));
        assertEquals(Boolean.TRUE, claims.getClaimValue("t"));
        assertEquals(Boolean.FALSE, claims.getClaimValue("f"));
        assertNull(claims.getClaimValue("n"));
        assertEquals(List.of("p", 2L), claims.getClaimValue("a"));
        assertEquals(Map.of("k", Map.of("v", "w")), claims.getClaimValue("o"));
    }

    @Test
    void shouldExposeIntegersBeyondLongRangeAsBigInteger() throws Exception
    {
        JwtClaims claims = JwtClaims.parse("{\"big\":12345678901234567890}");

        assertEquals(new BigInteger("12345678901234567890"), claims.getClaimValue("big"));
    }

    @Test
    void shouldRejectDuplicateClaimNames()
    {
        assertThrows(JwtException.class, () -> JwtClaims.parse("{\"sub\":\"a\",\"sub\":\"b\"}"));
        assertThrows(JwtException.class, () -> JwtClaims.parse("{\"o\":{\"k\":1,\"k\":2}}"));
        assertThrows(JwtException.class, () -> JwtClaims.parse("{\"a\":[{\"k\":1,\"k\":2}]}"));
    }

    @Test
    void shouldAcceptRepeatedNamesAtDifferentLevels() throws Exception
    {
        JwtClaims claims = JwtClaims.parse("{\"k\":1,\"o\":{\"k\":2},\"a\":[{\"k\":3},{\"k\":4}]}");

        assertEquals(1L, claims.getClaimValue("k"));
    }

    @Test
    void shouldRejectClaimsThatAreNotAnObject()
    {
        assertThrows(JwtException.class, () -> JwtClaims.parse("[1]"));
        assertThrows(JwtException.class, () -> JwtClaims.parse("\"x\""));
        assertThrows(JwtException.class, () -> JwtClaims.parse("42"));
        assertThrows(JwtException.class, () -> JwtClaims.parse("{"));
        assertThrows(JwtException.class, () -> JwtClaims.parse(""));
        assertThrows(JwtException.class, () -> JwtClaims.parse(null));
    }
}
