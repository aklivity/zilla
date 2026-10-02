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

import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public final class JwtClaims
{
    private final JsonObject claims;

    public static JwtClaims parse(
        String json) throws JwtException
    {
        return new JwtClaims(StrictJson.readObject(json));
    }

    public String getIssuer() throws JwtException
    {
        return getStringClaim("iss");
    }

    public String getSubject() throws JwtException
    {
        return getStringClaim("sub");
    }

    public String getStringClaim(
        String name) throws JwtException
    {
        JsonValue value = claims.get(name);

        String claim = null;
        if (value instanceof JsonString text)
        {
            claim = text.getString();
        }
        else if (present(value))
        {
            throw new JwtException("The value of the '" + name + "' claim is not a string");
        }

        return claim;
    }

    public List<String> getAudience() throws JwtException
    {
        JsonValue value = claims.get("aud");

        List<String> audience = List.of();
        if (value instanceof JsonString text)
        {
            audience = List.of(text.getString());
        }
        else if (value instanceof JsonArray array)
        {
            audience = new ArrayList<>(array.size());
            for (JsonValue element : array)
            {
                if (!(element instanceof JsonString text))
                {
                    throw new JwtException("The array value of the 'aud' claim contains non string values");
                }
                audience.add(text.getString());
            }
        }
        else if (present(value))
        {
            throw new JwtException("The value of the 'aud' claim is not an array of strings or a single string value");
        }

        return audience;
    }

    public Instant getNotBefore() throws JwtException
    {
        return getNumericDate("nbf");
    }

    public Instant getExpirationTime() throws JwtException
    {
        return getNumericDate("exp");
    }

    public Object getClaimValue(
        String name)
    {
        return plainValue(claims.get(name));
    }

    private JwtClaims(
        JsonObject claims)
    {
        this.claims = claims;
    }

    private Instant getNumericDate(
        String name) throws JwtException
    {
        JsonValue value = claims.get(name);

        Instant date = null;
        if (value instanceof JsonNumber number)
        {
            try
            {
                date = Instant.ofEpochSecond(number.longValue());
            }
            catch (DateTimeException ex)
            {
                throw new JwtException("The value of the '" + name + "' claim is out of range", ex);
            }
        }
        else if (present(value))
        {
            throw new JwtException("The value of the '" + name + "' claim is not a number");
        }

        return date;
    }

    private static boolean present(
        JsonValue value)
    {
        return value != null && value.getValueType() != JsonValue.ValueType.NULL;
    }

    private static Object plainValue(
        JsonValue value)
    {
        Object plain = null;

        if (value != null)
        {
            plain = switch (value.getValueType())
            {
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> plainNumber((JsonNumber) value);
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case ARRAY -> plainList(value.asJsonArray());
            case OBJECT -> plainMap(value.asJsonObject());
            case NULL -> null;
            };
        }

        return plain;
    }

    private static Object plainNumber(
        JsonNumber number)
    {
        Object plain;

        if (number.isIntegral())
        {
            BigInteger integer = number.bigIntegerValue();
            plain = integer.bitLength() < Long.SIZE ? Long.valueOf(integer.longValue()) : integer;
        }
        else
        {
            plain = number.doubleValue();
        }

        return plain;
    }

    private static List<Object> plainList(
        JsonArray array)
    {
        List<Object> plain = new ArrayList<>(array.size());

        for (JsonValue element : array)
        {
            plain.add(plainValue(element));
        }

        return plain;
    }

    private static Map<String, Object> plainMap(
        JsonObject object)
    {
        Map<String, Object> plain = new LinkedHashMap<>();

        for (Map.Entry<String, JsonValue> entry : object.entrySet())
        {
            plain.put(entry.getKey(), plainValue(entry.getValue()));
        }

        return plain;
    }
}
