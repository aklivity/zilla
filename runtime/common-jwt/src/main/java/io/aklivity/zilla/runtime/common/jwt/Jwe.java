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

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

public final class Jwe
{
    private static final String CONTENT_ENCRYPTION = "A256GCM";
    private static final int KEY_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String encodedHeader;
    private final JsonObject header;
    private final String algorithm;
    private final String encryption;
    private final String keyId;
    private final byte[] encryptedKey;
    private final byte[] iv;
    private final byte[] ciphertext;
    private final byte[] tag;

    public static Jwe parse(
        String compact) throws JwtException
    {
        if (compact == null)
        {
            throw new JwtException("Missing JWE compact serialization");
        }

        int[] ends = new int[4];
        int from = 0;
        for (int i = 0; i < ends.length; i++)
        {
            ends[i] = compact.indexOf('.', from);
            if (ends[i] == -1)
            {
                throw new JwtException("A JWE compact serialization must have exactly 5 parts");
            }
            from = ends[i] + 1;
        }

        if (compact.indexOf('.', from) != -1)
        {
            throw new JwtException("A JWE compact serialization must have exactly 5 parts");
        }

        String encodedHeader = compact.substring(0, ends[0]);
        JsonObject header = Jws.readHeader(Base64Url.decode(encodedHeader));

        return new Jwe(
            encodedHeader,
            header,
            Jws.member(header, "alg"),
            Jws.member(header, "enc"),
            Jws.member(header, "kid"),
            Base64Url.decode(compact.substring(ends[0] + 1, ends[1])),
            Base64Url.decode(compact.substring(ends[1] + 1, ends[2])),
            Base64Url.decode(compact.substring(ends[2] + 1, ends[3])),
            Base64Url.decode(compact.substring(ends[3] + 1)));
    }

    public static String seal(
        JweKeyManager keys,
        String keyId,
        byte[] plaintext) throws JwtException
    {
        JsonObjectBuilder header = Json.createObjectBuilder()
            .add("alg", keys.algorithm())
            .add("enc", CONTENT_ENCRYPTION);
        if (keyId != null)
        {
            header.add("kid", keyId);
        }

        String encodedHeader = Base64Url.encode(header.build().toString().getBytes(UTF_8));

        byte[] key = new byte[KEY_LENGTH];
        RANDOM.nextBytes(key);

        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);

        String sealed;
        try
        {
            byte[] encryptedKey = keys.wrap(key);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH * 8, iv));
            cipher.updateAAD(encodedHeader.getBytes(US_ASCII));
            byte[] encrypted = cipher.doFinal(plaintext);

            int split = encrypted.length - TAG_LENGTH;

            sealed = String.join(".",
                encodedHeader,
                Base64Url.encode(encryptedKey),
                Base64Url.encode(iv),
                Base64Url.encode(Arrays.copyOfRange(encrypted, 0, split)),
                Base64Url.encode(Arrays.copyOfRange(encrypted, split, encrypted.length)));
        }
        catch (GeneralSecurityException ex)
        {
            throw new JwtException("Unable to encrypt", ex);
        }
        finally
        {
            Arrays.fill(key, (byte) 0);
        }

        return sealed;
    }

    public JsonObject header()
    {
        return header;
    }

    public String algorithm()
    {
        return algorithm;
    }

    public String encryption()
    {
        return encryption;
    }

    public String keyId()
    {
        return keyId;
    }

    public byte[] encryptedKey()
    {
        return encryptedKey;
    }

    public byte[] open(
        JweKeyManager keys) throws JwtException
    {
        if (header.containsKey("crit"))
        {
            throw new JwtException("Unrecognized critical header");
        }

        if (header.containsKey("zip"))
        {
            throw new JwtException("Unsupported compression");
        }

        if (algorithm == null || !algorithm.equals(keys.algorithm()))
        {
            throw new JwtException("Unsupported algorithm: " + algorithm);
        }

        if (!CONTENT_ENCRYPTION.equals(encryption))
        {
            throw new JwtException("Unsupported content encryption: " + encryption);
        }

        if (iv.length != IV_LENGTH || tag.length != TAG_LENGTH)
        {
            throw new JwtException("Invalid initialization vector or authentication tag");
        }

        byte[] key = keys.unwrap(encryptedKey);

        byte[] plaintext;
        try
        {
            if (key == null || key.length != KEY_LENGTH)
            {
                throw new JwtException("Invalid content encryption key");
            }

            byte[] encrypted = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, encrypted, 0, ciphertext.length);
            System.arraycopy(tag, 0, encrypted, ciphertext.length, tag.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH * 8, iv));
            cipher.updateAAD(encodedHeader.getBytes(US_ASCII));
            plaintext = cipher.doFinal(encrypted);
        }
        catch (GeneralSecurityException ex)
        {
            throw new JwtException("Unable to decrypt", ex);
        }
        finally
        {
            if (key != null)
            {
                Arrays.fill(key, (byte) 0);
            }
        }

        return plaintext;
    }

    private Jwe(
        String encodedHeader,
        JsonObject header,
        String algorithm,
        String encryption,
        String keyId,
        byte[] encryptedKey,
        byte[] iv,
        byte[] ciphertext,
        byte[] tag)
    {
        this.encodedHeader = encodedHeader;
        this.header = header;
        this.algorithm = algorithm;
        this.encryption = encryption;
        this.keyId = keyId;
        this.encryptedKey = encryptedKey;
        this.iv = iv;
        this.ciphertext = ciphertext;
        this.tag = tag;
    }
}
