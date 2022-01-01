/*
 * Copyright 2021-2021 Aklivity Inc.
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
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.security.auth.x500.X500Principal;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.cog.vault.BindingVault;
import io.aklivity.zilla.runtime.vault.filesystem.internal.config.FileSystemOptions;
import io.aklivity.zilla.runtime.vault.filesystem.internal.config.FileSystemStore;

public class FileSystemVault implements BindingVault
{
    private static final String TYPE_DEFAULT = "PKCS12";

    private final Function<String, KeyStore.PrivateKeyEntry> lookupKey;
    private final Function<String, KeyStore.TrustedCertificateEntry> lookupTrust;
    private final Function<String, KeyStore.TrustedCertificateEntry> lookupSigner;
    private final Function<Predicate<X500Principal>, KeyStore.PrivateKeyEntry[]> lookupKeys;

    public FileSystemVault(
        FileSystemOptions options,
        Function<String, URL> resolvePath)
    {
        lookupKey = supplyLookupPrivateKeyEntry(resolvePath, options.keys);
        lookupTrust = supplyLookupTrustedCertificateEntry(resolvePath, options.trust);
        lookupSigner = supplyLookupTrustedCertificateEntry(resolvePath, options.signers);
        lookupKeys = supplyLookupPrivateKeyEntries(resolvePath, options.keys);
    }

    @Override
    public KeyStore.PrivateKeyEntry key(
        String alias)
    {
        return lookupKey.apply(alias);
    }

    @Override
    public KeyStore.TrustedCertificateEntry certificate(
        String alias)
    {
        return lookupTrust.apply(alias);
    }

    @Override
    public PrivateKeyEntry[] keys(
        String signer)
    {
        KeyStore.PrivateKeyEntry[] keys = null;

        TrustedCertificateEntry trusted = lookupSigner.apply(signer);
        if (trusted != null)
        {
            Certificate certificate = trusted.getTrustedCertificate();
            if (certificate instanceof X509Certificate)
            {
                X509Certificate x509 = (X509Certificate) certificate;
                X500Principal issuer = x509.getSubjectX500Principal();
                keys = lookupKeys.apply(issuer::equals);
            }
        }

        return keys;
    }

    private static Function<String, KeyStore.PrivateKeyEntry> supplyLookupPrivateKeyEntry(
        Function<String, URL> resolvePath,
        FileSystemStore aliases)
    {
        return supplyLookupAlias(resolvePath, aliases, FileSystemVault::lookupPrivateKeyEntry);
    }

    private static Function<String, KeyStore.TrustedCertificateEntry> supplyLookupTrustedCertificateEntry(
        Function<String, URL> resolvePath,
        FileSystemStore aliases)
    {
        return supplyLookupAlias(resolvePath, aliases, FileSystemVault::lookupTrustedCertificateEntry);
    }

    private Function<Predicate<X500Principal>, KeyStore.PrivateKeyEntry[]> supplyLookupPrivateKeyEntries(
        Function<String, URL> resolvePath,
        FileSystemStore entries)
    {
        Function<Predicate<X500Principal>, KeyStore.PrivateKeyEntry[]> lookupKeys = p -> null;

        if (entries != null)
        {
            try
            {
                URL storeURL = resolvePath.apply(entries.store);
                URLConnection connection = storeURL.openConnection();
                try (InputStream input = connection.getInputStream())
                {
                    String type = Optional.ofNullable(entries.type).orElse(TYPE_DEFAULT);
                    char[] password = Optional.ofNullable(entries.password).map(String::toCharArray).orElse(null);

                    KeyStore store = KeyStore.getInstance(type);
                    store.load(input, password);
                    KeyStore.PasswordProtection protection = new KeyStore.PasswordProtection(password);

                    List<String> aliases = Collections.list(store.aliases());

                    lookupKeys = matchSigner ->
                    {
                        List<KeyStore.PrivateKeyEntry> keys = null;

                        for (String alias : aliases)
                        {
                            PrivateKeyEntry key = lookupPrivateKeyEntry(alias, store, protection);
                            Certificate certificate = key.getCertificate();
                            if (key != null &&
                                certificate instanceof X509Certificate &&
                                matchSigner.test(((X509Certificate) certificate).getIssuerX500Principal()))
                            {
                                if (keys == null)
                                {
                                    keys = new ArrayList<>();
                                }

                                keys.add(key);
                            }
                        }

                        return keys != null ? keys.toArray(KeyStore.PrivateKeyEntry[]::new) : null;
                    };
                }
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return lookupKeys;
    }

    private static <R> Function<String, R> supplyLookupAlias(
        Function<String, URL> resolvePath,
        FileSystemStore aliases,
        Lookup<R> lookup)
    {
        Function<String, R> lookupAlias = a -> null;

        if (aliases != null)
        {
            try
            {
                URL storeURL = resolvePath.apply(aliases.store);
                URLConnection connection = storeURL.openConnection();
                try (InputStream input = connection.getInputStream())
                {
                    String type = Optional.ofNullable(aliases.type).orElse(TYPE_DEFAULT);
                    char[] password = Optional.ofNullable(aliases.password).map(String::toCharArray).orElse(null);

                    KeyStore store = KeyStore.getInstance(type);
                    store.load(input, password);
                    KeyStore.PasswordProtection protection = new KeyStore.PasswordProtection(password);

                    lookupAlias = alias -> lookup.apply(alias, store, protection);
                }
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return lookupAlias;
    }

    private static KeyStore.Entry lookupEntry(
        String alias,
        KeyStore store,
        KeyStore.PasswordProtection protection)
    {
        KeyStore.Entry entry = null;

        try
        {
            entry = store.getEntry(alias, protection);
        }
        catch (Exception ex)
        {
            try
            {
                entry = store.getEntry(alias, null);
            }
            catch (Exception e)
            {
                e.addSuppressed(ex);
                LangUtil.rethrowUnchecked(e);
            }
        }

        return entry;
    }

    private static KeyStore.PrivateKeyEntry lookupPrivateKeyEntry(
        String alias,
        KeyStore store,
        KeyStore.PasswordProtection protection)
    {
        Entry entry = lookupEntry(alias, store, protection);

        return entry instanceof KeyStore.PrivateKeyEntry ? (KeyStore.PrivateKeyEntry) entry : null;
    }

    private static KeyStore.TrustedCertificateEntry lookupTrustedCertificateEntry(
        String alias,
        KeyStore store,
        KeyStore.PasswordProtection protection)
    {
        Entry entry = lookupEntry(alias, store, protection);

        return entry instanceof KeyStore.TrustedCertificateEntry ? (KeyStore.TrustedCertificateEntry) entry : null;
    }

    @FunctionalInterface
    private interface Lookup<T>
    {
        T apply(String alias, KeyStore store, KeyStore.PasswordProtection protection);
    }
}
