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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;
import java.util.regex.MatchResult;

import javax.net.ssl.TrustManagerFactory;

import io.aklivity.zilla.config.binding.tls.TlsConditionConfig;
import io.aklivity.zilla.config.binding.tls.TlsWithConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public final class TlsRouteConfig
{
    public final long id;
    public final TlsWithResolver with;

    private final List<TlsConditionConfig> conditions;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;
    private List<TlsConditionMatcher> when;

    public TlsRouteConfig(
        EngineContext context,
        String qname,
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
        this.with = newWithResolver(context, qname, route);
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

    private static TlsWithResolver newWithResolver(
        EngineContext context,
        String qname,
        RouteConfig route)
    {
        TlsWithResolver resolver = null;

        if (route.with != null)
        {
            final TlsWithConfig with = (TlsWithConfig) route.with;
            final Map<String, LongFunction<String>> identifiers = new HashMap<>();
            final Map<String, LongObjectBiFunction<String, String>> attributors = new HashMap<>();

            final Set<String> guardNames = TlsWithResolver.extractGuardNames(with);

            for (String guardName : guardNames)
            {
                final long guardId = route.resolveId.applyAsLong(guardName);
                final GuardHandler guard = context.supplyGuard(guardId);

                // an unresolvable guard would silently select no certificate on every stream,
                // surfacing only as a handshake failure at the far end
                if (guard == null)
                {
                    throw new IllegalArgumentException(String.format(
                        "binding %s route with.certificate refers to unresolved guard: %s", qname, guardName));
                }

                identifiers.put(guardName, guard::identity);
                attributors.put(guardName, guard::attribute);
            }

            final LongFunction<String> defaultIdentifier = a -> null;
            final LongObjectBiFunction<MatchResult, String> identityReplacer = (a, r) ->
            {
                final LongFunction<String> identifier = identifiers.getOrDefault(r.group(1), defaultIdentifier);
                final String identity = identifier.apply(a);
                return identity != null ? identity : "";
            };

            final LongObjectBiFunction<String, String> defaultAttributor = (sessionId, name) -> null;
            final LongObjectBiFunction<MatchResult, String> attributeReplacer = (sessionId, match) ->
            {
                final LongObjectBiFunction<String, String> attributor =
                    attributors.getOrDefault(match.group(1), defaultAttributor);

                final String value = attributor.apply(sessionId, match.group(2));
                return value != null ? value : "";
            };

            resolver = new TlsWithResolver(qname, identityReplacer, attributeReplacer, with);
        }

        return resolver;
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
