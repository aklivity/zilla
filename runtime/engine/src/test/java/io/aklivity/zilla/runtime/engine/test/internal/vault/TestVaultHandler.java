/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.test.internal.vault;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.agrona.LangUtil;

import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.test.internal.vault.config.TestVaultEntryConfig;
import io.aklivity.zilla.config.engine.test.internal.vault.config.TestVaultOptionsConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManager;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManagerFactory;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public final class TestVaultHandler implements VaultHandler
{
    private static final Pattern PATTERN_KEY_ENTRY =
        Pattern.compile(
            "(?<key>-----BEGIN PRIVATE KEY-----[^-]+-----END PRIVATE KEY-----[^-]*)" +
            "(?<chain>(?:-----BEGIN CERTIFICATE-----[^-]+-----END CERTIFICATE-----[^-]*)+)");

    private static final Pattern PATTERN_SECRET_KEY_ENTRY =
        Pattern.compile("-----BEGIN SECRET KEY-----(?<body>[^-]+)-----END SECRET KEY-----");

    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final List<TestVaultEntryConfig> keys;
    private final TestVaultEntryConfig signer;
    private final List<TestVaultEntryConfig> trust;
    private final Map<String, SecretKey> wraps;

    public TestVaultHandler(
        VaultConfig vault)
    {
        TestVaultOptionsConfig options = (TestVaultOptionsConfig) vault.options;
        this.keys = options != null ? options.keys : null;
        this.signer = options != null ? options.signer : null;
        this.trust = options != null ? options.trust : null;
        this.wraps = options != null ? newWraps(options.wrap) : null;
    }

    private static Map<String, SecretKey> newWraps(
        List<TestVaultEntryConfig> wrap)
    {
        Map<String, SecretKey> wraps = null;

        if (wrap != null)
        {
            wraps = new HashMap<>();
            for (TestVaultEntryConfig config : wrap)
            {
                wraps.put(config.alias, newSecretKey(config.entry));
            }
        }

        return wraps;
    }

    private static SecretKey newSecretKey(
        String pem)
    {
        SecretKey key = null;

        Matcher matcher = PATTERN_SECRET_KEY_ENTRY.matcher(pem);
        if (matcher.find())
        {
            String base64 = matcher.group("body").replaceAll("[^a-zA-Z0-9+/=]", "");
            byte[] encoded = Base64.getMimeDecoder().decode(base64);
            key = new SecretKeySpec(encoded, KEY_ALGORITHM);
        }

        return key;
    }

    @Override
    public void wrap(
        long traceId,
        String key,
        DirectBufferEx bytes,
        int index,
        int length,
        BytesConsumer next)
    {
        SecretKey secretKey = wraps != null ? wraps.get(key) : null;

        if (secretKey == null)
        {
            next.accept(null, 0, 0);
        }
        else
        {
            try
            {
                byte[] wrapped = wrapNamed(secretKey, key, bytes, index, length);
                next.accept(new UnsafeBufferEx(wrapped), 0, wrapped.length);
            }
            catch (Exception ex)
            {
                next.accept(null, 0, 0);
            }
        }
    }

    @Override
    public void unwrap(
        long traceId,
        DirectBufferEx bytes,
        int index,
        int length,
        BytesConsumer next)
    {
        String key = nameOf(bytes, index, length);
        SecretKey secretKey = key != null && wraps != null ? wraps.get(key) : null;

        if (secretKey == null)
        {
            next.accept(null, 0, 0);
        }
        else
        {
            try
            {
                byte[] plaintext = unwrapNamed(secretKey, bytes, index, length);
                next.accept(new UnsafeBufferEx(plaintext), 0, plaintext.length);
            }
            catch (Exception ex)
            {
                next.accept(null, 0, 0);
            }
        }
    }

    // wraps bytes with a length-prefixed copy of `key` ahead of the iv + ciphertext, so a
    // matching #unwrapNamed can recover which key to unwrap under with no key argument
    private static byte[] wrapNamed(
        SecretKey secretKey,
        String key,
        DirectBufferEx bytes,
        int index,
        int length)
        throws Exception
    {
        byte[] iv = new byte[GCM_IV_LENGTH];
        RANDOM.nextBytes(iv);

        byte[] plaintext = new byte[length];
        bytes.getBytes(index, plaintext);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] name = key.getBytes(UTF_8);
        byte[] wrapped = new byte[Integer.BYTES + name.length + iv.length + ciphertext.length];
        putInt(wrapped, 0, name.length);
        System.arraycopy(name, 0, wrapped, Integer.BYTES, name.length);
        System.arraycopy(iv, 0, wrapped, Integer.BYTES + name.length, iv.length);
        System.arraycopy(ciphertext, 0, wrapped, Integer.BYTES + name.length + iv.length, ciphertext.length);

        return wrapped;
    }

    // recovers the key name embedded by #wrapNamed, or null if the bytes are too short or
    // foreign (i.e. do not carry a recoverable name) to have been produced by it
    private static String nameOf(
        DirectBufferEx bytes,
        int index,
        int length)
    {
        String name = null;

        int remaining = length - Integer.BYTES;
        if (remaining >= 0)
        {
            int nameLength = bytes.getInt(index, ByteOrder.BIG_ENDIAN);

            if (nameLength >= 0 && nameLength <= remaining - GCM_IV_LENGTH)
            {
                byte[] nameBytes = new byte[nameLength];
                bytes.getBytes(index + Integer.BYTES, nameBytes);
                name = new String(nameBytes, UTF_8);
            }
        }

        return name;
    }

    // decrypts the iv + ciphertext following the name embedded by #wrapNamed
    private static byte[] unwrapNamed(
        SecretKey secretKey,
        DirectBufferEx bytes,
        int index,
        int length)
        throws Exception
    {
        int nameLength = bytes.getInt(index, ByteOrder.BIG_ENDIAN);
        int ivOffset = index + Integer.BYTES + nameLength;

        // codeql[java/static-initialization-vector]
        byte[] iv = new byte[GCM_IV_LENGTH];
        bytes.getBytes(ivOffset, iv);

        int cipherOffset = ivOffset + GCM_IV_LENGTH;
        int cipherLength = length - (cipherOffset - index);
        byte[] ciphertext = new byte[cipherLength];
        bytes.getBytes(cipherOffset, ciphertext);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return cipher.doFinal(ciphertext);
    }

    private static void putInt(
        byte[] buffer,
        int offset,
        int value)
    {
        buffer[offset] = (byte) (value >>> 24);
        buffer[offset + 1] = (byte) (value >>> 16);
        buffer[offset + 2] = (byte) (value >>> 8);
        buffer[offset + 3] = (byte) value;
    }

    @Override
    public KeyManagerFactory initKeys(
        List<String> aliases)
    {
        KeyManagerFactory factory = null;

        List<TestVaultEntryConfig> matched = matchedKeys(aliases);

        if (!matched.isEmpty())
        {
            try
            {
                KeyStore.PasswordProtection protection = new KeyStore.PasswordProtection("test".toCharArray());

                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(null, protection.getPassword());

                for (TestVaultEntryConfig key : matched)
                {
                    final Matcher matchKey = PATTERN_KEY_ENTRY.matcher(key.entry);
                    if (matchKey.matches())
                    {
                        store.setEntry(key.alias, newKeyEntry(matchKey), protection);
                    }
                }

                if (store.size() != 0)
                {
                    factory = KeyManagerFactory.getInstance("PKIX");
                    factory.init(store, protection.getPassword());
                }
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return factory;
    }

    private static KeyStore.PrivateKeyEntry newKeyEntry(
        Matcher matchKey)
        throws Exception
    {
        String encodedKey = matchKey.group("key");
        String encodedChain = matchKey.group("chain");

        CertificateFactory x509 = CertificateFactory.getInstance("X509");

        InputStream exportedBytes = new ByteArrayInputStream(encodedChain.getBytes(US_ASCII));

        Certificate[] chain = x509.generateCertificates(exportedBytes).toArray(Certificate[]::new);

        String base64 = encodedKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("[^a-zA-Z0-9+/=]", "");
        byte[] encoded = Base64.getMimeDecoder().decode(base64);

        KeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        PrivateKey rsaKey = rsa.generatePrivate(keySpec);

        return new KeyStore.PrivateKeyEntry(rsaKey, chain);
    }

    private List<TestVaultEntryConfig> matchedKeys(
        List<String> aliases)
    {
        List<TestVaultEntryConfig> matched = new ArrayList<>();
        if (aliases != null && keys != null)
        {
            for (TestVaultEntryConfig key : keys)
            {
                if (aliases.contains(key.alias))
                {
                    matched.add(key);
                }
            }
        }
        return matched;
    }

    @Override
    public KeyManagerFactory initKeys()
    {
        return keys != null ? initKeys(keys.stream().map(entry -> entry.alias).toList()) : null;
    }

    @Override
    public KeyManagerFactory initSigners(
        List<String> aliases)
    {
        KeyManagerFactory factory = null;

        if (aliases != null && signer != null && aliases.contains(signer.alias))
        {
            List<String> signed = keys != null
                ? keys.stream().filter(key -> key.entry.contains(signer.entry)).map(key -> key.alias).toList()
                : List.of();

            if (!signed.isEmpty())
            {
                factory = initKeys(signed);
            }
        }

        return factory;
    }

    @Override
    public KeyManagerFactory initSigners()
    {
        return signer != null ? initSigners(List.of(signer.alias)) : null;
    }

    @Override
    public TrustManagerFactory initTrust(
        KeyStore cacerts)
    {
        List<String> certAliases = trust != null ? trust.stream().map(entry -> entry.alias).toList() : null;
        return initTrust(certAliases, cacerts);
    }

    @Override
    public TrustManagerFactory initTrust(
        List<String> certAliases,
        KeyStore cacerts)
    {
        TrustManagerFactory factory = null;

        List<TestVaultEntryConfig> matched = matchedTrust(certAliases);

        if (!matched.isEmpty() || cacerts != null)
        {
            try
            {
                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(null, null);

                if (!matched.isEmpty())
                {
                    CertificateFactory x509 = CertificateFactory.getInstance("X509");
                    for (TestVaultEntryConfig entry : matched)
                    {
                        InputStream certificateBytes = new ByteArrayInputStream(entry.entry.getBytes(US_ASCII));
                        Certificate certificate = x509.generateCertificate(certificateBytes);

                        KeyStore.TrustedCertificateEntry trusted = new KeyStore.TrustedCertificateEntry(certificate);

                        store.setEntry(entry.alias, trusted, null);
                    }
                }

                if (cacerts != null)
                {
                    List<String> aliases = Collections.list(cacerts.aliases());
                    for (String alias : aliases)
                    {
                        if (cacerts.isCertificateEntry(alias) &&
                            cacerts.getEntry(alias, null) instanceof TrustedCertificateEntry cacert)
                        {
                            store.setEntry(alias, cacert, null);
                        }
                    }
                }

                factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init(store);
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return factory;
    }

    private List<TestVaultEntryConfig> matchedTrust(
        List<String> certAliases)
    {
        List<TestVaultEntryConfig> matched = new ArrayList<>();
        if (certAliases != null && trust != null)
        {
            for (TestVaultEntryConfig entry : trust)
            {
                if (certAliases.contains(entry.alias))
                {
                    matched.add(entry);
                }
            }
        }
        return matched;
    }

    @Override
    public SecretKeyManagerFactory initSecretKeys(
        List<String> aliases)
    {
        Map<String, SecretKey> matched = matchedWraps(aliases);
        SecretKeyManager manager = !matched.isEmpty() ? new TestSecretKeyManager(matched) : null;
        return manager != null ? () -> manager : null;
    }

    private Map<String, SecretKey> matchedWraps(
        List<String> aliases)
    {
        Map<String, SecretKey> matched = new HashMap<>();
        if (aliases != null && wraps != null)
        {
            for (String alias : aliases)
            {
                SecretKey key = wraps.get(alias);
                if (key != null)
                {
                    matched.put(alias, key);
                }
            }
        }
        return matched;
    }

    private static final class TestSecretKeyManager implements SecretKeyManager
    {
        private final Map<String, SecretKey> keys;

        private TestSecretKeyManager(
            Map<String, SecretKey> keys)
        {
            this.keys = keys;
        }

        @Override
        public int wrap(
            String keyName,
            DirectBufferEx bytes,
            int index,
            int length,
            MutableDirectBufferEx dst,
            int dstIndex)
        {
            SecretKey secretKey = keys.get(keyName);
            int written = -1;

            if (secretKey != null)
            {
                try
                {
                    byte[] wrapped = wrapNamed(secretKey, keyName, bytes, index, length);
                    dst.putBytes(dstIndex, wrapped);
                    written = wrapped.length;
                }
                catch (Exception ex)
                {
                    written = -1;
                }
            }

            return written;
        }

        @Override
        public int unwrap(
            DirectBufferEx bytes,
            int index,
            int length,
            MutableDirectBufferEx dst,
            int dstIndex)
        {
            String keyName = nameOf(bytes, index, length);
            SecretKey secretKey = keyName != null ? keys.get(keyName) : null;
            int written = -1;

            if (secretKey != null)
            {
                try
                {
                    byte[] plaintext = unwrapNamed(secretKey, bytes, index, length);
                    dst.putBytes(dstIndex, plaintext);
                    written = plaintext.length;
                }
                catch (Exception ex)
                {
                    written = -1;
                }
            }

            return written;
        }
    }
}
