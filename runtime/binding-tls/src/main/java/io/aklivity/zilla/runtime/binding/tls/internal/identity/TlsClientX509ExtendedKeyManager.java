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

import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_DN;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * Reports the subject distinguished names of two candidate certificates that a selection on
     * {@code fieldNames} cannot tell apart, sharing a value for every named property. Both sides
     * are static, so this is decidable at configuration load, where a clear error beats selecting
     * arbitrarily by keystore order at handshake time.
     */
    public List<String> indistinguishableSubjects(
        Set<String> fieldNames)
    {
        List<String> subjects = List.of();

        List<Map<String, List<String>>> candidates = new ArrayList<>(fieldsByCertificate.values());

        indistinguishable:
        for (int i = 0; i < candidates.size(); i++)
        {
            for (int j = i + 1; j < candidates.size(); j++)
            {
                Map<String, List<String>> first = candidates.get(i);
                Map<String, List<String>> second = candidates.get(j);

                if (indistinguishable(first, second, fieldNames))
                {
                    subjects = List.of(subject(first), subject(second));
                    break indistinguishable;
                }
            }
        }

        return subjects;
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

        alias:
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
                        if (matches(candidate, selector))
                        {
                            alias = candidate;
                            break alias;
                        }
                    }
                }
            }

            if (debug)
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

    private boolean matches(
        String alias,
        Map<String, String> selector)
    {
        Map<String, List<String>> fields = resolveFields(alias);

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

    private static boolean indistinguishable(
        Map<String, List<String>> first,
        Map<String, List<String>> second,
        Set<String> fieldNames)
    {
        boolean indistinguishable = true;

        for (String fieldName : fieldNames)
        {
            List<String> firstValues = first.get(fieldName);
            List<String> secondValues = second.get(fieldName);

            // an absent property never matches, so it can never make two certificates ambiguous
            if (firstValues == null || secondValues == null ||
                firstValues.stream().noneMatch(secondValues::contains))
            {
                indistinguishable = false;
                break;
            }
        }

        return indistinguishable;
    }

    private static String subject(
        Map<String, List<String>> fields)
    {
        List<String> subject = fields.get(SUBJECT_DN);
        return subject != null && !subject.isEmpty() ? subject.get(0) : "";
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
