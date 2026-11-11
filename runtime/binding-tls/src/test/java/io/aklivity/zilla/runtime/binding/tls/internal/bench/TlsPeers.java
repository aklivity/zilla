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
package io.aklivity.zilla.runtime.binding.tls.internal.bench;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

/**
 * Builds the in-memory TLS endpoints that sit opposite the binding under test, from the same key material the
 * binding spec configurations use: the {@code localhost} key chain signed by {@code serverca}. Session caches are
 * bounded to one entry so the peer does not accumulate state that would be mistaken for the binding's.
 */
final class TlsPeers
{
    static final String TLS_1_3 = "TLSv1.3";
    static final String TLS_1_2 = "TLSv1.2";

    private static final String STORES = "io/aklivity/zilla/specs/binding/tls/config/stores/";
    private static final String KEYS = STORES + "server/keys.localhost.pem";
    private static final String TRUST = STORES + "client/trust.serverca.pem";
    private static final Pattern PEM = Pattern.compile("-----BEGIN ([A-Z ]+)-----([^-]+)-----END \\1-----");
    private static final int SESSION_CACHE_SIZE = 1;
    private static final char[] PASSWORD = new char[0];

    private final SSLContext client;
    private final SSLContext server;

    TlsPeers() throws GeneralSecurityException, IOException
    {
        this.client = context(null, trust());
        this.server = context(keys(), null);
    }

    SSLEngine newClient(
        String version)
    {
        final SSLEngine engine = client.createSSLEngine();
        engine.setUseClientMode(true);
        engine.setEnabledProtocols(new String[] { version });
        return engine;
    }

    SSLEngine newServer(
        String version)
    {
        final SSLEngine engine = server.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setEnabledProtocols(new String[] { version });
        return engine;
    }

    private static SSLContext context(
        KeyManagerFactory keys,
        TrustManagerFactory trust) throws GeneralSecurityException
    {
        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(
            keys != null ? keys.getKeyManagers() : null,
            trust != null ? trust.getTrustManagers() : null,
            null);
        context.getClientSessionContext().setSessionCacheSize(SESSION_CACHE_SIZE);
        context.getServerSessionContext().setSessionCacheSize(SESSION_CACHE_SIZE);
        return context;
    }

    private static KeyManagerFactory keys() throws GeneralSecurityException, IOException
    {
        final List<Certificate> chain = new ArrayList<>();
        PrivateKey key = null;

        final CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        final Matcher blocks = PEM.matcher(read(KEYS));
        while (blocks.find())
        {
            final byte[] der = Base64.getMimeDecoder().decode(blocks.group(2));
            if ("PRIVATE KEY".equals(blocks.group(1)))
            {
                key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            }
            else
            {
                chain.add(certificates.generateCertificate(new ByteArrayInputStream(der)));
            }
        }

        final KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("localhost", key, PASSWORD, chain.toArray(Certificate[]::new));

        final KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(store, PASSWORD);
        return factory;
    }

    private static TrustManagerFactory trust() throws GeneralSecurityException, IOException
    {
        final CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        final Matcher blocks = PEM.matcher(read(TRUST));

        final KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        for (int index = 0; blocks.find(); index++)
        {
            final byte[] der = Base64.getMimeDecoder().decode(blocks.group(2));
            store.setCertificateEntry("serverca" + index, certificates.generateCertificate(new ByteArrayInputStream(der)));
        }

        final TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return factory;
    }

    private static String read(
        String resource) throws IOException
    {
        try (InputStream input = TlsPeers.class.getClassLoader().getResourceAsStream(resource))
        {
            return new String(input.readAllBytes(), US_ASCII);
        }
    }
}
