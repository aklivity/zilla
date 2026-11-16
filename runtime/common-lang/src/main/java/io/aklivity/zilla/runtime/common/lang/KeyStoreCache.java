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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Remembers the keys and certificate chains that a wrapped key store returns, so each is decoded once
 * instead of on every lookup.
 * <p>
 * A key store may protect its entries, so that every lookup decodes the key and every certificate chain read
 * allocates again, and hands each caller its own copy of the key. This cache keeps the first decoded key for an
 * alias and returns that same instance to every caller.
 * <p>
 * The wrapped key store is not watched, so it must not be changed directly: change it through
 * {@link #keyStore()}, which forgets what it changes, or wrap the changed key store again.
 * <p>
 * Loading without a stream only initializes the wrapper; the wrapped key store keeps its entries.
 */
public final class KeyStoreCache extends KeyStoreSpi
{
    private final KeyStore delegate;
    private final ConcurrentMap<String, CachedKey> keys;
    private final ConcurrentMap<String, Certificate[]> chains;
    private final KeyStore keyStore;

    public KeyStoreCache(
        KeyStore delegate)
    {
        this.delegate = delegate;
        this.keys = new ConcurrentHashMap<>();
        this.chains = new ConcurrentHashMap<>();
        this.keyStore = new CachedKeyStore(this, delegate.getType());

        try
        {
            keyStore.load(null, null);
        }
        catch (IOException | NoSuchAlgorithmException | CertificateException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    public KeyStore keyStore()
    {
        return keyStore;
    }

    @Override
    public Key engineGetKey(
        String alias,
        char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException
    {
        CachedKey cached = keys.get(alias);

        Key key = null;

        if (cached != null && Arrays.equals(cached.password, password))
        {
            key = cached.key;
        }
        else
        {
            try
            {
                key = delegate.getKey(alias, password);
            }
            catch (KeyStoreException ex)
            {
                throw new IllegalStateException(ex);
            }

            if (key != null && password != null)
            {
                keys.put(alias, new CachedKey(password.clone(), key));
            }
        }

        return key;
    }

    @Override
    public Certificate[] engineGetCertificateChain(
        String alias)
    {
        Certificate[] chain = chains.get(alias);

        if (chain == null)
        {
            try
            {
                chain = delegate.getCertificateChain(alias);
            }
            catch (KeyStoreException ex)
            {
                throw new IllegalStateException(ex);
            }

            if (chain != null)
            {
                chains.put(alias, chain);
            }
        }

        return chain != null ? chain.clone() : null;
    }

    @Override
    public Certificate engineGetCertificate(
        String alias)
    {
        Certificate certificate = null;

        try
        {
            certificate = delegate.getCertificate(alias);
        }
        catch (KeyStoreException ex)
        {
            throw new IllegalStateException(ex);
        }

        return certificate;
    }

    @Override
    public Date engineGetCreationDate(
        String alias)
    {
        Date date = null;

        try
        {
            date = delegate.getCreationDate(alias);
        }
        catch (KeyStoreException ex)
        {
            throw new IllegalStateException(ex);
        }

        return date;
    }

    @Override
    public void engineSetKeyEntry(
        String alias,
        Key key,
        char[] password,
        Certificate[] chain) throws KeyStoreException
    {
        delegate.setKeyEntry(alias, key, password, chain);
        forget(alias);
    }

    @Override
    public void engineSetKeyEntry(
        String alias,
        byte[] key,
        Certificate[] chain) throws KeyStoreException
    {
        delegate.setKeyEntry(alias, key, chain);
        forget(alias);
    }

    @Override
    public void engineSetCertificateEntry(
        String alias,
        Certificate certificate) throws KeyStoreException
    {
        delegate.setCertificateEntry(alias, certificate);
        forget(alias);
    }

    @Override
    public void engineDeleteEntry(
        String alias) throws KeyStoreException
    {
        delegate.deleteEntry(alias);
        forget(alias);
    }

    @Override
    public Enumeration<String> engineAliases()
    {
        Enumeration<String> aliases = null;

        try
        {
            aliases = delegate.aliases();
        }
        catch (KeyStoreException ex)
        {
            throw new IllegalStateException(ex);
        }

        return aliases;
    }

    @Override
    public boolean engineContainsAlias(
        String alias)
    {
        boolean contains = false;

        try
        {
            contains = delegate.containsAlias(alias);
        }
        catch (KeyStoreException ex)
        {
            throw new IllegalStateException(ex);
        }

        return contains;
    }

    @Override
    public int engineSize()
    {
        int size = 0;

        try
        {
            size = delegate.size();
        }
        catch (KeyStoreException ex)
        {
            throw new IllegalStateException(ex);
        }

        return size;
    }

    @Override
    public boolean engineIsKeyEntry(
        String alias)
    {
        boolean key = false;

        try
        {
            key = delegate.isKeyEntry(alias);
        }
        catch (KeyStoreException ex)
        {
            throw new IllegalStateException(ex);
        }

        return key;
    }

    @Override
    public boolean engineIsCertificateEntry(
        String alias)
    {
        boolean certificate = false;

        try
        {
            certificate = delegate.isCertificateEntry(alias);
        }
        catch (KeyStoreException ex)
        {
            throw new IllegalStateException(ex);
        }

        return certificate;
    }

    @Override
    public String engineGetCertificateAlias(
        Certificate certificate)
    {
        String alias = null;

        try
        {
            alias = delegate.getCertificateAlias(certificate);
        }
        catch (KeyStoreException ex)
        {
            throw new IllegalStateException(ex);
        }

        return alias;
    }

    @Override
    public void engineStore(
        OutputStream stream,
        char[] password) throws IOException, NoSuchAlgorithmException, CertificateException
    {
        try
        {
            delegate.store(stream, password);
        }
        catch (KeyStoreException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void engineLoad(
        InputStream stream,
        char[] password) throws IOException, NoSuchAlgorithmException, CertificateException
    {
        if (stream != null)
        {
            delegate.load(stream, password);
        }

        forget();
    }

    private void forget()
    {
        keys.clear();
        chains.clear();
    }

    private void forget(
        String alias)
    {
        keys.remove(alias);
        chains.remove(alias);
    }

    private static final class CachedKey
    {
        private final char[] password;
        private final Key key;

        private CachedKey(
            char[] password,
            Key key)
        {
            this.password = password;
            this.key = key;
        }
    }

    private static final class CachedKeyStore extends KeyStore
    {
        private CachedKeyStore(
            KeyStoreSpi spi,
            String type)
        {
            super(spi, null, type);
        }
    }
}
