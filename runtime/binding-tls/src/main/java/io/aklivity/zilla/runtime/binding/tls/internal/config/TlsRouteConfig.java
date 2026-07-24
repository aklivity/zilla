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
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.security.cert.Certificate;
import java.util.List;
import java.util.function.UnaryOperator;

import javax.net.ssl.TrustManagerFactory;

import io.aklivity.zilla.config.binding.tls.TlsConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public final class TlsRouteConfig
{
    public final long id;

    private final List<TlsConditionConfig> conditions;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;
    private List<TlsConditionMatcher> when;

    public TlsRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.conditions = route.when.stream()
            .map(TlsConditionConfig.class::cast)
            .collect(toList());
        this.authorized = route.authorized;
        this.when = conditions.stream()
            .map(c -> new TlsConditionMatcher(c, null))
            .collect(toList());
    }

    public void init(
        VaultHandler vault)
    {
        this.when = conditions.stream()
            .map(c -> new TlsConditionMatcher(c, newTrustFactory(vault, c.trust)))
            .collect(toList());
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matches(
        String hostname,
        String alpn,
        int port,
        Certificate[] clientCerts)
    {
        return when.isEmpty() ||
                when.stream().anyMatch(m -> m.matches(hostname, alpn, port, clientCerts));
    }

    boolean matchesIgnoringCert(
        String hostname,
        String alpn,
        int port)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesIgnoringCert(hostname, alpn, port));
    }

    boolean matchesPortOnly(
        int port)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesPortOnly(port));
    }

    private static TrustManagerFactory newTrustFactory(
        VaultHandler vault,
        List<String> trustRefs)
    {
        return vault != null && trustRefs != null && !trustRefs.isEmpty()
            ? vault.initTrust(trustRefs, null)
            : null;
    }
}
