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
package io.aklivity.zilla.runtime.binding.a2a.internal.config;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aDialects;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBearerError;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class A2aBindingConfig
{
    public static final String CREDENTIALS_PLACEHOLDER = "{credentials}";

    private static final String HTTP_HEADER_AUTHORIZATION = "authorization";
    private static final String PATH_ROOT = "/";
    private static final String WELL_KNOWN_CARD = "/.well-known/agent-card.json";
    private static final String WELL_KNOWN_LEGACY_CARD = "/.well-known/agent.json";

    private static final Runnable NOOP = () ->
    {
    };

    public final long id;
    public final A2aOptionsConfig options;
    public final GuardHandler guard;
    public final A2aDialects dialects;
    public final String url;
    public final String basePath;
    public final String pushPath;

    private final List<A2aRouteConfig> routes;
    private final Pattern credentialsPattern;

    public A2aBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        this.id = binding.id;
        this.options = (A2aOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
            .map(A2aRouteConfig::new)
            .toList();

        this.guard = Optional.ofNullable(options)
            .map(o -> o.authorization)
            .map(a -> a.name)
            .map(binding.resolveId::applyAsLong)
            .map(context::supplyGuard)
            .orElse(null);

        this.credentialsPattern = Optional.ofNullable(options)
            .map(o -> o.authorization)
            .map(a -> a.credentials)
            .filter(c -> !c.isEmpty())
            .map(c -> Pattern.compile(c.replace(CREDENTIALS_PLACEHOLDER, "(?<credentials>[^\\s]+)")))
            .orElse(null);

        this.dialects = new A2aDialects(options != null ? options.versions : null);

        this.url = options != null ? options.url : null;
        this.basePath = url != null ? normalize(URI.create(url).getPath()) : null;
        this.pushPath = Optional.ofNullable(options)
            .map(o -> o.serverPush)
            .map(p -> p.path)
            .map(A2aBindingConfig::normalize)
            .orElse(null);
    }

    public A2aRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public A2aAuthorizationResult authorize(
        long traceId,
        long routedId,
        long initialId,
        long authorization,
        HttpBeginExFW httpBeginEx)
    {
        A2aAuthorizationResult result = new A2aAuthorizationResult(authorization, null, NOOP);

        if (guard != null)
        {
            final String authorizationHeader = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_AUTHORIZATION.equals(h.name().asString())))
                .map(h -> h.value().asString())
                .orElse(null);

            if (authorizationHeader != null)
            {
                final Matcher credentialsMatcher = credentialsPattern.matcher(authorizationHeader);
                final String credentials = credentialsMatcher.matches()
                    ? credentialsMatcher.group("credentials")
                    : null;

                final long sessionAuth = credentials != null
                    ? guard.reauthorize(traceId, routedId, initialId, credentials)
                    : GuardHandler.NOT_AUTHORIZED;

                result = (sessionAuth & GuardHandler.MASK_AUTHORIZED) != 0L
                    ? new A2aAuthorizationResult(sessionAuth, null, () -> guard.deauthorize(sessionAuth))
                    : new A2aAuthorizationResult(authorization, credentials == null
                        ? A2aBearerError.INVALID_REQUEST
                        : A2aBearerError.INVALID_TOKEN, NOOP);
            }
        }

        return result;
    }

    public boolean secured()
    {
        return guard != null;
    }

    public boolean pushConfigured()
    {
        return options != null && options.serverPush != null;
    }

    public boolean isPushPath(
        String path)
    {
        return pushPath != null && path != null && path.startsWith(pushPath + PATH_ROOT);
    }

    public String pushConfigId(
        String path)
    {
        return path.substring(pushPath.length() + 1);
    }

    public static boolean isCardPath(
        String path)
    {
        return path != null && (path.endsWith(WELL_KNOWN_CARD) || path.endsWith(WELL_KNOWN_LEGACY_CARD));
    }

    public static boolean isLegacyCardPath(
        String path)
    {
        return path != null && path.endsWith(WELL_KNOWN_LEGACY_CARD);
    }

    public static String cardPrefix(
        String path)
    {
        final int suffix = path.endsWith(WELL_KNOWN_LEGACY_CARD)
            ? path.length() - WELL_KNOWN_LEGACY_CARD.length()
            : path.length() - WELL_KNOWN_CARD.length();
        return path.substring(0, suffix);
    }

    private static String normalize(
        String path)
    {
        String normalized = path != null ? path : "";
        while (normalized.endsWith(PATH_ROOT))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
