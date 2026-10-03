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

import java.io.ByteArrayInputStream;
import java.security.PrivateKey;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonConfig;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public final class Jws
{
    private static final JsonReaderFactory HEADERS = Json.createReaderFactory(
        Map.of(JsonConfig.KEY_STRATEGY, JsonConfig.KeyStrategy.NONE));

    private final String signingInput;
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

        int headerEnd = compact.indexOf('.');
        int payloadEnd = headerEnd != -1 ? compact.indexOf('.', headerEnd + 1) : -1;
        if (payloadEnd == -1 || compact.indexOf('.', payloadEnd + 1) != -1)
        {
            throw new JwtException("A JWS compact serialization must have exactly 3 parts");
        }

        JsonObject header = readHeader(Base64Url.decode(compact.substring(0, headerEnd)));
        String payload = new String(Base64Url.decode(compact.substring(headerEnd + 1, payloadEnd)), UTF_8);

        return new Jws(compact.substring(0, payloadEnd), compact.substring(payloadEnd + 1), header,
            member(header, "alg"), member(header, "kid"), payload);
    }

    public static String sign(
        JwsAlgorithm algorithm,
        PrivateKey key,
        String keyId,
        String payload) throws JwtException
    {
        JsonObjectBuilder header = Json.createObjectBuilder().add("alg", algorithm.joseName());
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

        byte[] input = signingInput.getBytes(US_ASCII);
        byte[] signature = decodeSignature();

        return signature != null && algorithm.verify(key.publicKey(), input, signature) ? payload : null;
    }

    private Jws(
        String signingInput,
        String encodedSignature,
        JsonObject header,
        String algorithm,
        String keyId,
        String payload)
    {
        this.signingInput = signingInput;
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

    private static JsonObject readHeader(
        byte[] json) throws JwtException
    {
        JsonObject header;
        try (JsonReader reader = HEADERS.createReader(new ByteArrayInputStream(json)))
        {
            header = reader.readObject();
        }
        catch (JsonException | IllegalStateException ex)
        {
            throw new JwtException("Invalid JSON object", ex);
        }

        return header;
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
