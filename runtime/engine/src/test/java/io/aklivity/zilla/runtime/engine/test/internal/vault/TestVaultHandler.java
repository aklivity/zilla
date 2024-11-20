/*
 * Copyright 2021-2024 Aklivity Inc.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.test.internal.vault.config.TestVaultEntryConfig;
import io.aklivity.zilla.runtime.engine.test.internal.vault.config.TestVaultOptionsConfig;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public final class TestVaultHandler implements VaultHandler
{
    private static final Pattern PATTERN_KEY_ENTRY =
        Pattern.compile(
            "(?<key>-----BEGIN PRIVATE KEY-----[^-]+-----END PRIVATE KEY-----[^-]+)" +
            "(?<chain>(?:-----BEGIN CERTIFICATE-----[^-]+-----END CERTIFICATE-----[^-]+)+)");

    private final TestVaultEntryConfig key;
    private final TestVaultEntryConfig signer;
    private final TestVaultEntryConfig trust;

    public TestVaultHandler(
        VaultConfig vault)
    {
        TestVaultOptionsConfig options = (TestVaultOptionsConfig) vault.options;
        this.key = options != null ? options.key : null;
        this.signer = options != null ? options.signer : null;
        this.trust = options != null ? options.trust : null;
    }

    @Override
    public KeyManagerFactory initKeys(
        List<String> aliases)
    {
        KeyManagerFactory factory = null;

        if (aliases != null && key != null && aliases.contains(key.alias))
        {
            final Matcher matchKey = PATTERN_KEY_ENTRY.matcher(key.entry);
            if (matchKey.matches())
            {
                String encodedKey = matchKey.group("key");
                String encodedChain = matchKey.group("chain");

                try
                {
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

                    KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(rsaKey, chain);

                    KeyStore store = KeyStore.getInstance("PKCS12");
                    KeyStore.PasswordProtection protection = new KeyStore.PasswordProtection("test".toCharArray());
                    store.load(null, protection.getPassword());

                    store.setEntry(key.alias, entry, protection);

                    factory = KeyManagerFactory.getInstance("PKIX");
                    factory.init(store, protection.getPassword());
                }
                catch (Exception ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }
            }
        }

        return factory;
    }

    @Override
    public KeyManagerFactory initSigners(
        List<String> aliases)
    {
        KeyManagerFactory factory = null;

        if (aliases != null && signer != null && aliases.contains(signer.alias))
        {
            if (key != null && key.entry.contains(signer.entry))
            {
                factory = initKeys(List.of(key.alias));
            }
        }

        return factory;
    }

    @Override
    public TrustManagerFactory initTrust(
        List<String> certAliases,
        KeyStore cacerts)
    {
        TrustManagerFactory factory = null;

        if (certAliases != null && trust != null && certAliases.contains(trust.alias) ||
            cacerts != null)
        {
            try
            {
                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(null, null);

                if (certAliases != null && trust != null && certAliases.contains(trust.alias))
                {
                    CertificateFactory x509 = CertificateFactory.getInstance("X509");

                    InputStream certificateBytes = new ByteArrayInputStream(trust.entry.getBytes(US_ASCII));
                    Certificate certificate = x509.generateCertificate(certificateBytes);

                    KeyStore.TrustedCertificateEntry entry = new KeyStore.TrustedCertificateEntry(certificate);

                    store.setEntry(trust.alias, entry, null);
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
}
