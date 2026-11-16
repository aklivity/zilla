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
package io.aklivity.zilla.runtime.common.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KeyStoreCacheTest
{
    private static final char[] PASSWORD = "password".toCharArray();

    private static final String FIRST_KEY =
        """
        -----BEGIN PRIVATE KEY-----
        MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg9b45g5nB9v7KQ0fl
        oKUkt2j/B55unQ2LHwB2Kq51dLihRANCAATqFYnwWUgXdK17s0olbOcdRHVAk3jJ
        Hk+/W/Lojt2xqqx7NLC+3R/W1lLFRFFil8zWldlWZ4UCCevxPn2qv3Im
        -----END PRIVATE KEY-----
        """;

    private static final String FIRST_CERTIFICATE =
        """
        -----BEGIN CERTIFICATE-----
        MIIBczCCARmgAwIBAgIUD1BQIlGpHzjb47us+UtJtvssfE8wCgYIKoZIzj0EAwIw
        DjEMMAoGA1UEAwwDb25lMCAXDTI2MTExMjA0NDg0NFoYDzIxMjYxMDE5MDQ0ODQ0
        WjAOMQwwCgYDVQQDDANvbmUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATqFYnw
        WUgXdK17s0olbOcdRHVAk3jJHk+/W/Lojt2xqqx7NLC+3R/W1lLFRFFil8zWldlW
        Z4UCCevxPn2qv3Imo1MwUTAdBgNVHQ4EFgQUDiT02d6ZGopM/1QNbhnC1PH0kZMw
        HwYDVR0jBBgwFoAUDiT02d6ZGopM/1QNbhnC1PH0kZMwDwYDVR0TAQH/BAUwAwEB
        /zAKBggqhkjOPQQDAgNIADBFAiEAwsUGiz2ZVxbjqyo3pbZGiOf6oJICPX35WXm7
        D6OdQToCIG2p8vuOEy9l4xAohw+orcooermf8CORiyAhOSyQUgwE
        -----END CERTIFICATE-----
        """;

    private static final String SECOND_KEY =
        """
        -----BEGIN PRIVATE KEY-----
        MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgm1mafUDeuDiSLGfn
        Ca2ccGfJIazVWgcXNABgG0q/A2qhRANCAAQk48DFnx2Vuzyv+WJNtkry3qfTmFkV
        Xxbh3DWrLsCg/khLFrtnsS2lYNJji7cNVBiH12oqjTI3j0mcO7qTJHGH
        -----END PRIVATE KEY-----
        """;

    private static final String SECOND_CERTIFICATE =
        """
        -----BEGIN CERTIFICATE-----
        MIIBczCCARmgAwIBAgIUO9juAvcLyZnMak8CzuqcuQpyFp0wCgYIKoZIzj0EAwIw
        DjEMMAoGA1UEAwwDdHdvMCAXDTI2MTExMjA0NDg0NFoYDzIxMjYxMDE5MDQ0ODQ0
        WjAOMQwwCgYDVQQDDAN0d28wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQk48DF
        nx2Vuzyv+WJNtkry3qfTmFkVXxbh3DWrLsCg/khLFrtnsS2lYNJji7cNVBiH12oq
        jTI3j0mcO7qTJHGHo1MwUTAdBgNVHQ4EFgQUFj+M7X/9cvdKn6mdc9hfQlBzhs0w
        HwYDVR0jBBgwFoAUFj+M7X/9cvdKn6mdc9hfQlBzhs0wDwYDVR0TAQH/BAUwAwEB
        /zAKBggqhkjOPQQDAgNIADBFAiEA65QaINyCNpJDHdPmZN2rDciAE0H4wAQMgEtK
        W5PERbICIBsn19tpzsrqPl8ZQPshVA60WkRHyO9xKqgQ2J/S9A+y
        -----END CERTIFICATE-----
        """;

    private KeyStore delegate;
    private KeyStore keyStore;

    @BeforeEach
    public void init() throws Exception
    {
        delegate = KeyStore.getInstance("PKCS12");
        delegate.load(null, PASSWORD);
        setKeyEntry(delegate, "server", FIRST_KEY, FIRST_CERTIFICATE);

        keyStore = new KeyStoreCache(delegate).keyStore();
    }

    @Test
    public void shouldDecodeKeyOnceForRepeatedLookups() throws Exception
    {
        assertNotSame(delegate.getKey("server", PASSWORD), delegate.getKey("server", PASSWORD));

        Key first = keyStore.getKey("server", PASSWORD);

        assertNotNull(first);
        assertSame(first, keyStore.getKey("server", PASSWORD));
    }

    @Test
    public void shouldNotServeKeyForDifferentPassword() throws Exception
    {
        assertNotNull(keyStore.getKey("server", PASSWORD));

        assertThrows(UnrecoverableKeyException.class, () -> keyStore.getKey("server", "other".toCharArray()));
    }

    @Test
    public void shouldNotRememberMissingKey() throws Exception
    {
        assertNull(keyStore.getKey("missing", PASSWORD));

        setKeyEntry(delegate, "missing", SECOND_KEY, SECOND_CERTIFICATE);

        assertNotNull(keyStore.getKey("missing", PASSWORD));
    }

    @Test
    public void shouldServeChangedKeyEntryAfterSetKeyEntryThroughKeyStore() throws Exception
    {
        Certificate[] before = keyStore.getCertificateChain("server");
        Key beforeKey = keyStore.getKey("server", PASSWORD);

        setKeyEntry(keyStore, "server", SECOND_KEY, SECOND_CERTIFICATE);

        assertNotEquals(before[0], keyStore.getCertificateChain("server")[0]);
        assertNotEquals(beforeKey, keyStore.getKey("server", PASSWORD));
    }

    @Test
    public void shouldReturnCopyOfCertificateChain() throws Exception
    {
        Certificate[] chain = keyStore.getCertificateChain("server");
        Certificate certificate = chain[0];
        chain[0] = null;

        assertEquals(certificate, keyStore.getCertificateChain("server")[0]);
    }

    @Test
    public void shouldNotRememberMissingCertificateChain() throws Exception
    {
        assertNull(keyStore.getCertificateChain("missing"));
    }

    @Test
    public void shouldDelegateEntryQueries() throws Exception
    {
        delegate.setCertificateEntry("trust", certificate(SECOND_CERTIFICATE));

        assertEquals(2, keyStore.size());
        assertEquals(List.of("server", "trust"), Collections.list(keyStore.aliases()).stream().sorted().toList());
        assertTrue(keyStore.containsAlias("server"));
        assertFalse(keyStore.containsAlias("missing"));
        assertTrue(keyStore.isKeyEntry("server"));
        assertFalse(keyStore.isKeyEntry("trust"));
        assertTrue(keyStore.isCertificateEntry("trust"));
        assertFalse(keyStore.isCertificateEntry("server"));
        assertEquals(certificate(FIRST_CERTIFICATE), keyStore.getCertificate("server"));
        assertEquals("trust", keyStore.getCertificateAlias(certificate(SECOND_CERTIFICATE)));
        assertEquals(delegate.getCreationDate("server"), keyStore.getCreationDate("server"));
    }

    @Test
    public void shouldSetCertificateEntryThroughKeyStore() throws Exception
    {
        keyStore.setCertificateEntry("trust", certificate(SECOND_CERTIFICATE));

        assertTrue(delegate.isCertificateEntry("trust"));
    }

    @Test
    public void shouldDeleteEntryThroughKeyStore() throws Exception
    {
        assertNotNull(keyStore.getKey("server", PASSWORD));

        keyStore.deleteEntry("server");

        assertFalse(delegate.containsAlias("server"));
        assertNull(keyStore.getKey("server", PASSWORD));
        assertNull(keyStore.getCertificateChain("server"));
    }

    @Test
    public void shouldKeepWrappedEntriesWhenLoadedWithoutStream() throws Exception
    {
        keyStore.load(null, null);

        assertTrue(delegate.containsAlias("server"));
        assertNotNull(keyStore.getKey("server", PASSWORD));
    }

    @Test
    public void shouldStoreAndLoadWrappedKeyStore() throws Exception
    {
        ByteArrayOutputStream stored = new ByteArrayOutputStream();
        keyStore.store(stored, PASSWORD);

        Key before = keyStore.getKey("server", PASSWORD);
        keyStore.load(new ByteArrayInputStream(stored.toByteArray()), PASSWORD);

        assertTrue(delegate.containsAlias("server"));
        assertNotSame(before, keyStore.getKey("server", PASSWORD));
        assertEquals(before, keyStore.getKey("server", PASSWORD));
    }

    private static void assertNotEquals(
        Object unexpected,
        Object actual)
    {
        assertFalse(unexpected.equals(actual));
    }

    private static void setKeyEntry(
        KeyStore store,
        String alias,
        String key,
        String certificate) throws Exception
    {
        String base64 = key
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        PrivateKey decoded = KeyFactory.getInstance("EC")
            .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));

        store.setKeyEntry(alias, decoded, PASSWORD, new Certificate[] { certificate(certificate) });
    }

    private static Certificate certificate(
        String pem) throws Exception
    {
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }
}
