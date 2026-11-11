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
package io.aklivity.zilla.runtime.binding.tls.internal.identity;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedKeyManager;

/**
 * Remembers which server alias the wrapped key manager chose for a handshake.
 * <p>
 * The key manager produced by a keystore searches the keystore again on every choice, and a choice is made
 * several times for each handshake. That search allocates heavily and leaves a copy of the private key
 * referenced by every established connection. The keystore behind a key manager is fixed once the key manager
 * is initialized, so the choice depends only on what the handshake presents: the key type, the issuers, the
 * signature algorithms the peer supports and the server names the peer requested.
 * <p>
 * The server names are chosen by the peer, so the number of remembered choices is bounded; a choice that does
 * not fit is made by the wrapped key manager each time.
 */
public final class TlsServerAliasCachedX509ExtendedKeyManager extends X509ExtendedKeyManager
{
    static final int MAXIMUM_CHOICES = 64;

    private final X509ExtendedKeyManager delegate;
    private final ConcurrentMap<List<Object>, String> serverAliases;

    public TlsServerAliasCachedX509ExtendedKeyManager(
        X509ExtendedKeyManager delegate)
    {
        this.delegate = delegate;
        this.serverAliases = new ConcurrentHashMap<>();
    }

    @Override
    public String chooseEngineServerAlias(
        String keyType,
        Principal[] issuers,
        SSLEngine engine)
    {
        final SSLSession session = engine != null ? engine.getHandshakeSession() : null;
        final List<Object> choice = session instanceof ExtendedSSLSession extended
            ? List.of(
                String.valueOf(keyType),
                issuers != null ? Arrays.asList(issuers) : List.of(),
                Arrays.asList(extended.getPeerSupportedSignatureAlgorithms()),
                extended.getRequestedServerNames())
            : null;

        String alias = choice != null ? serverAliases.get(choice) : null;

        if (alias == null)
        {
            alias = delegate.chooseEngineServerAlias(keyType, issuers, engine);

            if (alias != null && choice != null && serverAliases.size() < MAXIMUM_CHOICES)
            {
                serverAliases.put(choice, alias);
            }
        }

        return alias;
    }

    @Override
    public PrivateKey getPrivateKey(
        String alias)
    {
        return delegate.getPrivateKey(alias);
    }

    @Override
    public X509Certificate[] getCertificateChain(
        String alias)
    {
        return delegate.getCertificateChain(alias);
    }

    @Override
    public String[] getClientAliases(
        String keyType,
        Principal[] issuers)
    {
        return delegate.getClientAliases(keyType, issuers);
    }

    @Override
    public String chooseClientAlias(
        String[] keyType,
        Principal[] issuers,
        Socket socket)
    {
        return delegate.chooseClientAlias(keyType, issuers, socket);
    }

    @Override
    public String[] getServerAliases(
        String keyType,
        Principal[] issuers)
    {
        return delegate.getServerAliases(keyType, issuers);
    }

    @Override
    public String chooseServerAlias(
        String keyType,
        Principal[] issuers,
        Socket socket)
    {
        return delegate.chooseServerAlias(keyType, issuers, socket);
    }

    @Override
    public String chooseEngineClientAlias(
        String[] keyType,
        Principal[] issuers,
        SSLEngine engine)
    {
        return delegate.chooseEngineClientAlias(keyType, issuers, engine);
    }
}
