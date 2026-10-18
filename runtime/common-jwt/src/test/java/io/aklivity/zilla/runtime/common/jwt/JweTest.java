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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class JweTest
{
    private static final String ALGORITHM = "io.aklivity.zilla.test.v1";

    private static final byte[] KEK = new byte[32];

    private static final int[] RFC7516_CEK = {
        177, 161, 244, 128, 84, 143, 225, 115, 63, 180, 3, 255, 107, 154, 212, 246,
        138, 7, 110, 91, 112, 46, 34, 105, 47, 130, 203, 46, 122, 234, 64, 252};

    private static final String RFC7516_HEADER = "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ";
    private static final String RFC7516_IV = "48V1_ALb6US04U3b";
    private static final String RFC7516_CIPHERTEXT =
        "5eym8TW_c8SuK0ltJ3rpYIzOeDQz7TALvtu6UG9oMo4vpzs9tX_EFShS8iB7j6jiSdiwkIr3ajwQzaBtQD_A";
    private static final String RFC7516_TAG = "XFBoMYUZodetZdvTiFvSkQ";
    private static final String RFC7516_PLAINTEXT = "The true sign of intelligence is not knowledge but imagination.";

    static
    {
        for (int i = 0; i < KEK.length; i++)
        {
            KEK[i] = (byte) (i + 1);
        }
    }

    @Test
    void shouldOpenRfc7516ContentEncryptionVector() throws Exception
    {
        byte[] cek = new byte[RFC7516_CEK.length];
        for (int i = 0; i < cek.length; i++)
        {
            cek[i] = (byte) RFC7516_CEK[i];
        }
        JweKeyManager keys = new FixedKeyManager("RSA-OAEP", cek);

        String compact = String.join(".", RFC7516_HEADER, "AAAA", RFC7516_IV, RFC7516_CIPHERTEXT, RFC7516_TAG);

        assertEquals(RFC7516_PLAINTEXT, new String(Jwe.parse(compact).open(keys), UTF_8));
    }

    @Test
    void shouldSealAndOpen() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();

        String compact = Jwe.seal(keys, "key-1", "{\"sub\":\"alice\"}".getBytes(UTF_8));
        Jwe jwe = Jwe.parse(compact);

        assertEquals(5, compact.split("\\.", -1).length);
        assertEquals(ALGORITHM, jwe.algorithm());
        assertEquals("A256GCM", jwe.encryption());
        assertEquals("key-1", jwe.keyId());
        assertEquals("{\"sub\":\"alice\"}", new String(jwe.open(keys), UTF_8));
    }

    @Test
    void shouldSealHeaderWithoutKeyIdWhenAbsent() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();

        Jwe jwe = Jwe.parse(Jwe.seal(keys, null, "x".getBytes(UTF_8)));

        assertNull(jwe.keyId());
        assertEquals(headerOf(ALGORITHM, "A256GCM"), jwe.header());
    }

    @Test
    void shouldSealEmptyPlaintext() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();

        assertEquals(0, Jwe.parse(Jwe.seal(keys, "key-1", new byte[0])).open(keys).length);
    }

    @Test
    void shouldSealDifferentlyEachTime() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        byte[] plaintext = "same".getBytes(UTF_8);

        assertNotEquals(Jwe.seal(keys, "key-1", plaintext), Jwe.seal(keys, "key-1", plaintext));
    }

    @Test
    void shouldExposeEncryptedKeyBytes() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();

        Jwe jwe = Jwe.parse(Jwe.seal(keys, "key-1", "x".getBytes(UTF_8)));

        assertEquals(40, jwe.encryptedKey().length);
    }

    @Test
    void shouldRejectTamperedHeader() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String[] parts = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8)).split("\\.", -1);
        parts[0] = encode("{\"alg\":\"" + ALGORITHM + "\",\"enc\":\"A256GCM\",\"kid\":\"key-2\"}");

        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", parts)).open(keys));
    }

    @Test
    void shouldRejectTamperedCiphertext() throws Exception
    {
        assertRejectsTamperedPart(3);
    }

    @Test
    void shouldRejectTamperedTag() throws Exception
    {
        assertRejectsTamperedPart(4);
    }

    @Test
    void shouldRejectTamperedInitializationVector() throws Exception
    {
        assertRejectsTamperedPart(2);
    }

    @Test
    void shouldRejectTamperedEncryptedKey() throws Exception
    {
        assertRejectsTamperedPart(1);
    }

    @Test
    void shouldRejectWrongAlgorithm() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String compact = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8));

        assertThrows(JwtException.class, () -> Jwe.parse(compact).open(new FixedKeyManager("other", new byte[32])));
    }

    @Test
    void shouldRejectWrongContentEncryption() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String[] parts = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8)).split("\\.", -1);
        parts[0] = encode("{\"alg\":\"" + ALGORITHM + "\",\"enc\":\"A128GCM\"}");

        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", parts)).open(keys));
    }

    @Test
    void shouldRejectMissingAlgorithmOrContentEncryption() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String[] parts = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8)).split("\\.", -1);

        parts[0] = encode("{\"enc\":\"A256GCM\"}");
        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", parts)).open(keys));

        parts[0] = encode("{\"alg\":\"" + ALGORITHM + "\"}");
        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", parts)).open(keys));
    }

    @Test
    void shouldRejectCompressionAndCriticalHeaders() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String[] parts = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8)).split("\\.", -1);

        parts[0] = encode("{\"alg\":\"" + ALGORITHM + "\",\"enc\":\"A256GCM\",\"zip\":\"DEF\"}");
        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", parts)).open(keys));

        parts[0] = encode("{\"alg\":\"" + ALGORITHM + "\",\"enc\":\"A256GCM\",\"crit\":[\"x\"],\"x\":1}");
        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", parts)).open(keys));
    }

    @Test
    void shouldRejectDuplicateHeaderNames() throws Exception
    {
        String compact = String.join(".",
            encode("{\"alg\":\"" + ALGORITHM + "\",\"enc\":\"A256GCM\",\"enc\":\"A256GCM\"}"), "AAAA", "AAAA", "AAAA", "AAAA");

        assertThrows(JwtException.class, () -> Jwe.parse(compact));
    }

    @Test
    void shouldRejectWrongPartCount() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String compact = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8));

        assertThrows(JwtException.class, () -> Jwe.parse(compact.substring(0, compact.lastIndexOf('.'))));
        assertThrows(JwtException.class, () -> Jwe.parse(compact + ".AAAA"));
        assertThrows(JwtException.class, () -> Jwe.parse("a.b.c"));
        assertThrows(JwtException.class, () -> Jwe.parse(null));
    }

    @Test
    void shouldRejectWrongInitializationVectorOrTagLength() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String[] parts = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8)).split("\\.", -1);

        String[] shortIv = parts.clone();
        shortIv[2] = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[8]);
        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", shortIv)).open(keys));

        String[] shortTag = parts.clone();
        shortTag[4] = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[8]);
        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", shortTag)).open(keys));
    }

    @Test
    void shouldRejectWrongContentKeyLength() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String compact = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8));

        assertThrows(JwtException.class, () -> Jwe.parse(compact).open(new FixedKeyManager(ALGORITHM, new byte[31])));
    }

    @Test
    void shouldPropagateKeyManagerFailure() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String compact = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8));
        JweKeyManager failing = new JweKeyManager()
        {
            @Override
            public String algorithm()
            {
                return ALGORITHM;
            }

            @Override
            public byte[] wrap(
                byte[] key) throws JwtException
            {
                throw new JwtException("wrap failed");
            }

            @Override
            public byte[] unwrap(
                byte[] wrapped) throws JwtException
            {
                throw new JwtException("vault unavailable");
            }
        };

        JwtException unwrap = assertThrows(JwtException.class, () -> Jwe.parse(compact).open(failing));
        JwtException wrap = assertThrows(JwtException.class, () -> Jwe.seal(failing, "key-1", "x".getBytes(UTF_8)));

        assertEquals("vault unavailable", unwrap.getMessage());
        assertEquals("wrap failed", wrap.getMessage());
    }

    @Test
    void shouldRejectInvalidBase64Segments() throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String[] parts = Jwe.seal(keys, "key-1", "x".getBytes(UTF_8)).split("\\.", -1);
        parts[3] = "!!!";

        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", parts)).open(keys));
    }

    private static void assertRejectsTamperedPart(
        int index) throws Exception
    {
        JweKeyManager keys = new WrapKeyManager();
        String[] parts = Jwe.seal(keys, "key-1", "tamper me".getBytes(UTF_8)).split("\\.", -1);

        byte[] bytes = Base64.getUrlDecoder().decode(parts[index]);
        bytes[0] ^= 0x01;
        parts[index] = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        assertThrows(JwtException.class, () -> Jwe.parse(String.join(".", parts)).open(keys));
    }

    private static String encode(
        String json)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(UTF_8));
    }

    private static JsonObject headerOf(
        String alg,
        String enc)
    {
        return Json.createObjectBuilder().add("alg", alg).add("enc", enc).build();
    }

    private static final class WrapKeyManager implements JweKeyManager
    {
        @Override
        public String algorithm()
        {
            return ALGORITHM;
        }

        @Override
        public byte[] wrap(
            byte[] key) throws JwtException
        {
            return cipher(Cipher.WRAP_MODE, key);
        }

        @Override
        public byte[] unwrap(
            byte[] wrapped) throws JwtException
        {
            return cipher(Cipher.UNWRAP_MODE, wrapped);
        }

        private static byte[] cipher(
            int mode,
            byte[] input) throws JwtException
        {
            try
            {
                Cipher cipher = Cipher.getInstance("AESWrap");
                cipher.init(mode, new SecretKeySpec(KEK, "AES"));
                return mode == Cipher.WRAP_MODE
                    ? cipher.wrap(new SecretKeySpec(input, "AES"))
                    : cipher.unwrap(input, "AES", Cipher.SECRET_KEY).getEncoded();
            }
            catch (GeneralSecurityException ex)
            {
                throw new JwtException("Unable to protect key", ex);
            }
        }
    }

    private static final class FixedKeyManager implements JweKeyManager
    {
        private final String algorithm;
        private final byte[] key;

        private FixedKeyManager(
            String algorithm,
            byte[] key)
        {
            this.algorithm = algorithm;
            this.key = key;
        }

        @Override
        public String algorithm()
        {
            return algorithm;
        }

        @Override
        public byte[] wrap(
            byte[] key) throws JwtException
        {
            throw new JwtException("Unsupported");
        }

        @Override
        public byte[] unwrap(
            byte[] wrapped)
        {
            return key;
        }
    }
}
