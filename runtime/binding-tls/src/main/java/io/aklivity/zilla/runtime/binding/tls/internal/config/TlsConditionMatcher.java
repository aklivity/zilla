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
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.agrona.collections.IntHashSet;

import io.aklivity.zilla.runtime.binding.tls.config.TlsConditionConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsMutualConfig;

public final class TlsConditionMatcher
{
    public final Matcher authorityMatch;
    public final Matcher alpnMatch;
    public final IntHashSet ports;
    public final TlsMutualConfig mutual;
    public final TrustManagerFactory trustFactory;

    public TlsConditionMatcher(
        TlsConditionConfig condition,
        TrustManagerFactory trustFactory)
    {
        this.authorityMatch = condition.authority != null ? asMatcher(condition.authority) : null;
        this.alpnMatch = condition.alpn != null ? asMatcher(condition.alpn) : null;
        this.ports = condition.ports != null ? asIntHashSet(condition.ports) : null;
        // `trust` implies `mutual: required`
        this.mutual = condition.mutual != null
            ? condition.mutual
            : condition.trust != null ? TlsMutualConfig.REQUIRED : null;
        this.trustFactory = trustFactory;
    }

    public boolean matches(
        String authority,
        String alpn,
        int port,
        Certificate[] clientCerts)
    {
        return matchesAuthority(authority) &&
                matchesAlpn(alpn) &&
                matchesPort(port) &&
                matchesMutual(clientCerts) &&
                matchesTrust(clientCerts);
    }

    public boolean matchesIgnoringCert(
        String authority,
        String alpn,
        int port)
    {
        return matchesAuthority(authority) &&
                matchesAlpn(alpn) &&
                matchesPort(port);
    }

    public boolean matchesPortOnly(
        int port)
    {
        return matchesPort(port);
    }

    private boolean matchesAuthority(
        String sni)
    {
        return authorityMatch == null || sni != null && authorityMatch.reset(sni).matches();
    }

    private boolean matchesAlpn(
        String alpn)
    {
        return alpnMatch == null || alpn != null && alpnMatch.reset(alpn).matches();
    }

    private boolean matchesPort(
        int port)
    {
        return ports == null || ports.contains(port);
    }

    private boolean matchesMutual(
        Certificate[] clientCerts)
    {
        boolean matches = true;
        boolean present = clientCerts != null && clientCerts.length > 0;
        if (mutual == TlsMutualConfig.REQUIRED)
        {
            matches = present;
        }
        else if (mutual == TlsMutualConfig.NONE)
        {
            matches = !present;
        }
        return matches;
    }

    private boolean matchesTrust(
        Certificate[] clientCerts)
    {
        boolean matches = true;
        if (trustFactory != null)
        {
            matches = false;
            if (clientCerts != null && clientCerts.length > 0)
            {
                X509Certificate[] chain = asX509Chain(clientCerts);
                if (chain != null)
                {
                    String authType = chain[0].getPublicKey().getAlgorithm();
                    for (TrustManager tm : trustFactory.getTrustManagers())
                    {
                        if (tm instanceof X509TrustManager x509tm)
                        {
                            try
                            {
                                x509tm.checkClientTrusted(chain, authType);
                                matches = true;
                                break;
                            }
                            catch (CertificateException ex)
                            {
                                // not trusted by this trust manager
                            }
                        }
                    }
                }
            }
        }
        return matches;
    }

    private static X509Certificate[] asX509Chain(
        Certificate[] certs)
    {
        X509Certificate[] chain = new X509Certificate[certs.length];
        for (int i = 0; i < certs.length; i++)
        {
            if (!(certs[i] instanceof X509Certificate x509))
            {
                return null;
            }
            chain[i] = x509;
        }
        return chain;
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard.replace(".", "\\.").replace("*", ".*")).matcher("");
    }

    private static IntHashSet asIntHashSet(
        int[] ports)
    {
        IntHashSet set = new IntHashSet(ports.length);
        Arrays.stream(ports).forEach(set::add);
        return set;
    }
}
