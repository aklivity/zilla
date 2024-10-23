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
package io.aklivity.zilla.runtime.vault.filesystem.internal;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.vault.VaultHandler;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemStoreConfig;

public class FileSystemVaultHandler implements VaultHandler
{
    private static final String STORE_TYPE_DEFAULT = "pkcs12";

    private final Function<List<String>, KeyManagerFactory> supplyKeys;
    private final Function<List<String>, KeyManagerFactory> supplySigners;
    private final BiFunction<List<String>, KeyStore, TrustManagerFactory> supplyTrust;

    public FileSystemVaultHandler(
        FileSystemOptionsConfig options,
        Function<String, Path> resolvePath)
    {
        FileSystemStoreInfo keys = supplyStoreInfo(resolvePath, options.keys);
        supplyKeys = keys != null
            ? keys::newKeysFactory
            : aliases -> null;

        FileSystemStoreInfo signers = supplyStoreInfo(resolvePath, options.signers);
        supplySigners = signers != null && keys != null
            ? aliases -> newSignersFactory(aliases, signers, keys)
            : aliases -> null;

        FileSystemStoreInfo trust = supplyStoreInfo(resolvePath, options.trust);
        supplyTrust = (aliases, cacerts) -> newTrustFactory(trust, aliases, cacerts);
    }

    @Override
    public KeyManagerFactory initKeys(
        List<String> aliases)
    {
        return supplyKeys.apply(aliases);
    }

    @Override
    public TrustManagerFactory initTrust(
        List<String> aliases,
        KeyStore cacerts)
    {
        return supplyTrust.apply(aliases, cacerts);
    }

    @Override
    public KeyManagerFactory initSigners(
        List<String> aliases)
    {
        return supplySigners.apply(aliases);
    }

    private static FileSystemStoreInfo supplyStoreInfo(
        Function<String, Path> resolvePath,
        FileSystemStoreConfig config)
    {
        FileSystemStoreInfo info = null;

        if (config != null)
        {
            try
            {
                Path storePath = resolvePath.apply(config.store);
                try (InputStream input = Files.newInputStream(storePath))
                {
                    String type = Optional.ofNullable(config.type).orElse(STORE_TYPE_DEFAULT);
                    char[] password = Optional.ofNullable(config.password).map(String::toCharArray).orElse(null);

                    KeyStore store = KeyStore.getInstance(type);
                    store.load(input, password);

                    info = new FileSystemStoreInfo(store, password);
                }
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return info;
    }

    private KeyManagerFactory newSignersFactory(
        List<String> aliases,
        FileSystemStoreInfo signers,
        FileSystemStoreInfo keys)
    {
        KeyManagerFactory factory = null;

        if (aliases != null)
        {
            factory = keys.newKeysFactory(aliases.stream()
                .map(signers::certificate)
                .filter(Objects::nonNull)
                .map(TrustedCertificateEntry::getTrustedCertificate)
                .filter(X509Certificate.class::isInstance)
                .map(X509Certificate.class::cast)
                .map(X509Certificate::getSubjectX500Principal)
                .map(keys::issuedKeys)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList());
        }

        return factory;
    }

    private TrustManagerFactory newTrustFactory(
        FileSystemStoreInfo store,
        List<String> aliases,
        KeyStore cacerts)
    {
        TrustManagerFactory factory = null;

        try
        {
            if (aliases != null || cacerts != null)
            {
                KeyStore trust = KeyStore.getInstance(STORE_TYPE_DEFAULT);
                trust.load(null, null);

                if (aliases != null && store != null)
                {
                    for (String alias : aliases)
                    {
                        TrustedCertificateEntry cert = store.certificate(alias);
                        if (cert != null)
                        {
                            trust.setEntry(alias, cert, null);
                        }
                    }
                }

                if (cacerts != null)
                {
                    for (String alias : aliases)
                    {
                        TrustedCertificateEntry cacert = FileSystemStoreInfo.certificate(cacerts, alias);
                        if (cacert != null)
                        {
                            trust.setEntry(alias, cacert, null);
                        }
                    }
                }

                factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init(trust);
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return factory;
    }

    private static final class FileSystemStoreInfo
    {
        private final KeyStore store;
        private final KeyStore.PasswordProtection protection;

        private FileSystemStoreInfo(
            KeyStore store,
            char[] password)
        {
            this.store = store;
            this.protection = password != null ? new KeyStore.PasswordProtection(password) : null;
        }

        private KeyManagerFactory newKeysFactory(
            List<String> aliases)
        {
            KeyManagerFactory factory = null;

            try
            {
                if (aliases != null)
                {
                    KeyStore keys = KeyStore.getInstance(STORE_TYPE_DEFAULT);
                    keys.load(null, protection.getPassword());

                    for (String alias : aliases)
                    {
                        PrivateKeyEntry key = key(alias);
                        if (key != null)
                        {
                            keys.setEntry(alias, key, protection);
                        }
                    }

                    factory = KeyManagerFactory.getInstance("PKIX");
                    factory.init(keys, protection.getPassword());
                }
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }

            return factory;
        }

        private PrivateKeyEntry key(
            String alias)
        {
            return entry(store, protection, alias, PrivateKeyEntry.class);
        }

        private TrustedCertificateEntry certificate(
            String alias)
        {
            return entry(store, null, alias, TrustedCertificateEntry.class);
        }

        private static TrustedCertificateEntry certificate(
            KeyStore store,
            String alias)
        {
            return entry(store, null, alias, TrustedCertificateEntry.class);
        }

        private static <T extends Entry> T entry(
            KeyStore store,
            KeyStore.PasswordProtection protection,
            String alias,
            Class<T> type)
        {
            T typed = null;

            try
            {
                Entry entry = store.getEntry(alias, protection);
                if (type.isInstance(entry))
                {
                    typed = type.cast(entry);
                }
            }
            catch (GeneralSecurityException ex)
            {
            }

            return typed;
        }

        private List<String> issuedKeys(
            X500Principal issuer)
        {
            List<String> keys = null;

            try
            {
                for (String alias : Collections.list(store.aliases()))
                {
                    PrivateKeyEntry key = key(alias);
                    Certificate certificate = key.getCertificate();
                    if (key != null &&
                        certificate instanceof X509Certificate &&
                        issuer.equals(((X509Certificate) certificate).getIssuerX500Principal()))
                    {
                        if (keys == null)
                        {
                            keys = new ArrayList<>();
                        }

                        keys.add(alias);
                    }
                }
            }
            catch (Exception ex)
            {
                // ignore
            }

            return keys;
        }
    }
}
