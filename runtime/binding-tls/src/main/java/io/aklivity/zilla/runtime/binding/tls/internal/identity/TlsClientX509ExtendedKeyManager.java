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

import static io.aklivity.zilla.runtime.common.x509.X509Fields.X5T_S256;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;
import io.aklivity.zilla.runtime.common.x509.X509Fields;

public final class TlsClientX509ExtendedKeyManager extends X509ExtendedKeyManager implements X509KeyManager
{
    /**
     * Names the certificate properties the handshake must match, resolved at route time and read
     * back during the handshake. {@code chooseEngineClientAlias} receives only the {@link SSLEngine},
     * and key managers are built into the {@code SSLContext} once per binding, so the session is the
     * only per-stream channel available.
     */
    public static final String CERTIFICATE_FIELDS_KEY = "certificate.fields";

    // candidates are exposed only per key algorithm, so the index is the union over the algorithms
    // a candidate key could use; an algorithm with no candidates simply contributes none
    private static final List<String> KEY_ALGORITHMS =
        List.of("RSA", "RSASSA-PSS", "EC", "DSA", "Ed25519", "Ed448");

    private final X509ExtendedKeyManager delegate;
    private final boolean debug;

    // Keyed by leaf certificate rather than by alias: a key manager alias carries a counter that
    // advances on every enumeration, so the same key yields a different alias each time it is
    // listed. The keystore is fixed for the binding's lifetime, so this is populated once and
    // selection is a lookup rather than a certificate parse per handshake.
    private final Map<X509Certificate, Map<String, List<String>>> fieldsByCertificate;

    public TlsClientX509ExtendedKeyManager(
        TlsConfiguration config,
        X509ExtendedKeyManager delegate)
    {
        this.debug = config.debug();
        this.delegate = delegate;
        this.fieldsByCertificate = indexCertificates(delegate);
    }

    /**
     * Reports whether any candidate key matches {@code selector}, so a selection that can never
     * succeed is observable before the handshake begins. Reads only the pre-built index, so it is
     * safe to call from the worker thread while resolving a route.
     */
    public boolean matchesAny(
        Map<String, String> selector)
    {
        return fieldsByCertificate.values().stream().anyMatch(fields -> matches(fields, selector));
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
    @SuppressWarnings("unchecked")
    public String chooseEngineClientAlias(
        String[] keyTypes,
        Principal[] issuers,
        SSLEngine engine)
    {
        String alias = null;

        SSLSession session = engine.getSession();
        Map<String, String> selector = (Map<String, String>) session.getValue(CERTIFICATE_FIELDS_KEY);

        if (selector == null)
        {
            alias = delegate.chooseEngineClientAlias(keyTypes, issuers, engine);
        }
        else if (keyTypes != null)
        {
            for (String keyType : keyTypes)
            {
                String[] candidates = delegate.getClientAliases(keyType, issuers);
                if (candidates != null)
                {
                    for (String candidate : candidates)
                    {
                        if (matches(candidate, selector) && supersedes(candidate, alias))
                        {
                            alias = candidate;
                        }
                    }
                }
            }

            if (alias == null && debug)
            {
                System.out.printf("[binding-tls] No match found for Certificate [%s], Key Types [%s], Issuers [%s] \n",
                    selector,
                    String.join(", ", keyTypes),
                    issuers != null
                        ? Arrays.stream(issuers).map(Principal::getName).collect(Collectors.joining(", "))
                        : null);
            }
        }

        return alias;
    }

    @Override
    public String chooseEngineServerAlias(
        String keyType,
        Principal[] issuers,
        SSLEngine engine)
    {
        return delegate.chooseEngineServerAlias(keyType, issuers, engine);
    }

    @Override
    public X509Certificate[] getCertificateChain(
        String alias)
    {
        return delegate.getCertificateChain(alias);
    }

    @Override
    public PrivateKey getPrivateKey(
        String alias)
    {
        return delegate.getPrivateKey(alias);
    }

    /**
     * Orders two matching candidates so selection is stable across restarts: the later
     * {@code notBefore} wins, which also prefers the new certificate while an old one is still
     * valid during a rotation. Keystore enumeration order carries no such guarantee, so taking
     * the first match found would vary between runs of the same configuration.
     */
    private boolean supersedes(
        String candidate,
        String alias)
    {
        boolean supersedes = alias == null;

        if (!supersedes)
        {
            int compared = notBefore(candidate).compareTo(notBefore(alias));

            supersedes = compared > 0 || compared == 0 && thumbprint(candidate).compareTo(thumbprint(alias)) > 0;
        }

        return supersedes;
    }

    private Date notBefore(
        String alias)
    {
        X509Certificate[] chain = delegate.getCertificateChain(alias);
        return chain[0].getNotBefore();
    }

    private String thumbprint(
        String alias)
    {
        List<String> values = resolveFields(alias).get(X5T_S256);
        return values != null && !values.isEmpty() ? values.get(0) : "";
    }

    private boolean matches(
        String alias,
        Map<String, String> selector)
    {
        return matches(resolveFields(alias), selector);
    }

    private static boolean matches(
        Map<String, List<String>> fields,
        Map<String, String> selector)
    {
        boolean matches = fields != null;

        if (matches)
        {
            for (Map.Entry<String, String> entry : selector.entrySet())
            {
                List<String> values = fields.get(entry.getKey());

                if (values == null || !values.contains(entry.getValue()))
                {
                    matches = false;
                    break;
                }
            }
        }

        return matches;
    }

    private Map<String, List<String>> resolveFields(
        String alias)
    {
        X509Certificate[] chain = delegate.getCertificateChain(alias);

        // a key algorithm outside the pre-indexed set still resolves, and is indexed on first use
        return chain != null && chain.length != 0
            ? fieldsByCertificate.computeIfAbsent(chain[0], X509Fields::resolve)
            : null;
    }

    private static Map<X509Certificate, Map<String, List<String>>> indexCertificates(
        X509ExtendedKeyManager delegate)
    {
        Map<X509Certificate, Map<String, List<String>>> fieldsByCertificate = new HashMap<>();

        for (String keyAlgorithm : KEY_ALGORITHMS)
        {
            String[] aliases = delegate.getClientAliases(keyAlgorithm, null);

            if (aliases != null)
            {
                for (String alias : aliases)
                {
                    X509Certificate[] chain = delegate.getCertificateChain(alias);

                    if (chain != null && chain.length != 0)
                    {
                        fieldsByCertificate.computeIfAbsent(chain[0], X509Fields::resolve);
                    }
                }
            }
        }

        return fieldsByCertificate;
    }
}
