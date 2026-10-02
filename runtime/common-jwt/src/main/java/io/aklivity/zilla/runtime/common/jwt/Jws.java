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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.PrivateKey;

import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public final class Jws
{
    private final String encodedHeader;
    private final String encodedPayload;
    private final String encodedSignature;
    private final JsonObject header;
    private final String algorithm;
    private final String keyId;
    private final String payload;

    public static Jws parse(
        String compact) throws JwtException
    {
        if (compact == null)
        {
            throw new JwtException("Missing JWS compact serialization");
        }

        String[] parts = compact.split("\\.", -1);
        if (parts.length != 3)
        {
            throw new JwtException("A JWS compact serialization must have exactly 3 parts");
        }

        JsonObject header = StrictJson.readObject(new String(Base64Url.decode(parts[0]), UTF_8));
        String payload = new String(Base64Url.decode(parts[1]), UTF_8);

        return new Jws(parts[0], parts[1], parts[2], header, member(header, "alg"), member(header, "kid"), payload);
    }

    public static String sign(
        JwsAlgorithm algorithm,
        PrivateKey key,
        String keyId,
        String payload) throws JwtException
    {
        JsonObjectBuilder header = StrictJson.PROVIDER.createObjectBuilder().add("alg", algorithm.joseName());
        if (keyId != null)
        {
            header.add("kid", keyId);
        }

        String signingInput = Base64Url.encode(header.build().toString().getBytes(UTF_8)) +
            "." + Base64Url.encode(payload.getBytes(UTF_8));

        return signingInput + "." + Base64Url.encode(algorithm.sign(key, signingInput.getBytes(US_ASCII)));
    }

    public JsonObject header()
    {
        return header;
    }

    public String algorithm()
    {
        return algorithm;
    }

    public String keyId()
    {
        return keyId;
    }

    public String unverifiedPayload()
    {
        return payload;
    }

    public String verifiedPayload(
        Jwk key) throws JwtException
    {
        if (header.containsKey("crit"))
        {
            throw new JwtException("Unrecognized critical header");
        }

        JwsAlgorithm algorithm = JwsAlgorithm.of(this.algorithm);
        if (algorithm == null)
        {
            throw new JwtException("Unsupported algorithm: " + this.algorithm);
        }

        if (key.algorithm() != null && !key.algorithm().equals(algorithm.joseName()))
        {
            throw new JwtException("Key algorithm does not match header algorithm");
        }

        byte[] input = (encodedHeader + "." + encodedPayload).getBytes(US_ASCII);
        byte[] signature = decodeSignature();

        return signature != null && algorithm.verify(key.publicKey(), input, signature) ? payload : null;
    }

    private Jws(
        String encodedHeader,
        String encodedPayload,
        String encodedSignature,
        JsonObject header,
        String algorithm,
        String keyId,
        String payload)
    {
        this.encodedHeader = encodedHeader;
        this.encodedPayload = encodedPayload;
        this.encodedSignature = encodedSignature;
        this.header = header;
        this.algorithm = algorithm;
        this.keyId = keyId;
        this.payload = payload;
    }

    private byte[] decodeSignature()
    {
        byte[] signature;
        try
        {
            signature = Base64Url.decode(encodedSignature);
        }
        catch (JwtException ex)
        {
            signature = null;
        }

        return signature;
    }

    private static String member(
        JsonObject header,
        String name) throws JwtException
    {
        JsonValue value = header.get(name);

        String member = null;
        if (value instanceof JsonString text)
        {
            member = text.getString();
        }
        else if (value != null && value.getValueType() != JsonValue.ValueType.NULL)
        {
            throw new JwtException("Invalid JWS header member: " + name);
        }

        return member;
    }
}
