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
package io.aklivity.zilla.runtime.binding.agui.internal.config;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.agui.AguiOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class AguiBindingConfig
{
    public static final String CREDENTIALS_PLACEHOLDER = "{credentials}";

    private static final String HTTP_HEADER_AUTHORIZATION = "authorization";
    private static final String SCHEME_DEFAULT = "Bearer";

    private static final Runnable NOOP = () ->
    {
    };

    public final long id;
    public final GuardHandler guard;

    private final List<AguiRouteConfig> routes;
    private final Pattern credentialsPattern;
    private final String scheme;

    public AguiBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        final AguiOptionsConfig options = (AguiOptionsConfig) binding.options;

        this.id = binding.id;
        this.routes = binding.routes.stream()
            .map(AguiRouteConfig::new)
            .toList();

        this.guard = Optional.ofNullable(options)
            .map(o -> o.authorization)
            .map(a -> a.name)
            .map(binding.resolveId::applyAsLong)
            .map(context::supplyGuard)
            .orElse(null);

        final String credentials = Optional.ofNullable(options)
            .map(o -> o.authorization)
            .map(a -> a.credentials)
            .filter(c -> !c.isEmpty())
            .orElse(null);

        final int placeholder = credentials != null ? credentials.indexOf(CREDENTIALS_PLACEHOLDER) : -1;

        this.credentialsPattern = placeholder >= 0
            ? Pattern.compile(Pattern.quote(credentials.substring(0, placeholder)) +
                "(?<credentials>[^\\s]+)" +
                Pattern.quote(credentials.substring(placeholder + CREDENTIALS_PLACEHOLDER.length())))
            : null;

        final String prefix = placeholder > 0 ? credentials.substring(0, placeholder).trim() : "";
        this.scheme = prefix.isEmpty() ? SCHEME_DEFAULT : prefix;
    }

    public AguiRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public AguiAuthorizationResult authorize(
        long traceId,
        long routedId,
        long initialId,
        long authorization,
        HttpBeginExFW httpBeginEx)
    {
        AguiAuthorizationResult result = new AguiAuthorizationResult(authorization, null, NOOP);

        if (guard != null)
        {
            final String authorizationHeader = Optional.ofNullable(httpBeginEx.headers()
                    .matchFirst(h -> HTTP_HEADER_AUTHORIZATION.equals(h.name().asString())))
                .map(h -> h.value().asString())
                .orElse(null);

            if (authorizationHeader == null)
            {
                result = new AguiAuthorizationResult(authorization, AguiAuthorizationError.MISSING, NOOP);
            }
            else
            {
                final Matcher credentialsMatcher = credentialsPattern.matcher(authorizationHeader);
                final String credentials = credentialsMatcher.matches()
                    ? credentialsMatcher.group("credentials")
                    : null;

                final long sessionAuth = credentials != null
                    ? guard.reauthorize(traceId, routedId, initialId, credentials)
                    : GuardHandler.NOT_AUTHORIZED;

                result = (sessionAuth & GuardHandler.MASK_AUTHORIZED) != 0L
                    ? new AguiAuthorizationResult(sessionAuth, null, () -> guard.deauthorize(sessionAuth))
                    : new AguiAuthorizationResult(authorization, credentials == null
                        ? AguiAuthorizationError.INVALID_REQUEST
                        : AguiAuthorizationError.INVALID_TOKEN, NOOP);
            }
        }

        return result;
    }

    public String challenge(
        AguiAuthorizationError error)
    {
        final String challenge;

        switch (error)
        {
        case INVALID_REQUEST:
            challenge = scheme + " error=\"invalid_request\"";
            break;
        case INVALID_TOKEN:
            challenge = scheme + " error=\"invalid_token\"";
            break;
        default:
            challenge = scheme;
            break;
        }

        return challenge;
    }
}
