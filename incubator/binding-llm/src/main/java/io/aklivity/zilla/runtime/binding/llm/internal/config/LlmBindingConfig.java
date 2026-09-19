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
package io.aklivity.zilla.runtime.binding.llm.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.llm.LlmOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.internal.dialect.LlmDialectResolver;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class LlmBindingConfig
{
    public static final String CREDENTIALS_PLACEHOLDER = "{credentials}";

    private static final Runnable NOOP = () ->
    {
    };

    private static final LlmOptionsConfig DEFAULT_OPTIONS = LlmOptionsConfig.builder().build();

    public final long id;
    public final String name;
    public final KindConfig kind;
    public final LlmOptionsConfig options;
    public final List<LlmRouteConfig> routes;
    public final GuardHandler guard;
    public final String credentials;

    private final LlmDialectResolver dialects;
    private final Pattern credentialsPattern;

    public LlmBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = binding.options instanceof LlmOptionsConfig o ? o : DEFAULT_OPTIONS;
        this.routes = binding.routes.stream().map(LlmRouteConfig::new).collect(toList());
        this.dialects = new LlmDialectResolver(this.options.dialect);
        this.guard = Optional.ofNullable(this.options.authorization)
            .map(a -> a.name)
            .map(binding.resolveId::applyAsLong)
            .map(context::supplyGuard)
            .orElse(null);
        this.credentials = Optional.ofNullable(this.options.authorization)
            .map(a -> a.credentials)
            .filter(c -> !c.isEmpty())
            .orElse(null);
        this.credentialsPattern = credentials != null
            ? Pattern.compile(credentials.replace(CREDENTIALS_PLACEHOLDER, "(?<credentials>[^\\s]+)"))
            : null;
    }

    public LlmAuthorizationResult authorize(
        long traceId,
        long routedId,
        long initialId,
        long authorization,
        JsonEnvelope envelope,
        LlmDialect dialect)
    {
        LlmAuthorizationResult result = new LlmAuthorizationResult(authorization, true, NOOP);

        if (guard != null)
        {
            final DirectBufferEx value = envelope.get(dialect.credentialsHeader(), 0);
            final String header = value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
            final Matcher credentialsMatcher = header != null ? credentialsPattern.matcher(header) : null;
            final String credentials = credentialsMatcher != null && credentialsMatcher.matches()
                ? credentialsMatcher.group("credentials")
                : null;

            final long sessionAuth = credentials != null
                ? guard.reauthorize(traceId, routedId, initialId, credentials)
                : GuardHandler.NOT_AUTHORIZED;

            result = (sessionAuth & GuardHandler.MASK_AUTHORIZED) != 0L
                ? new LlmAuthorizationResult(sessionAuth, true, () -> guard.deauthorize(sessionAuth))
                : new LlmAuthorizationResult(authorization, false, NOOP);
        }

        return result;
    }

    public LlmRouteConfig resolve(
        long authorization)
    {
        LlmRouteConfig resolved = null;
        for (LlmRouteConfig route : routes)
        {
            if (route.authorized(authorization))
            {
                resolved = route;
                break;
            }
        }
        return resolved;
    }

    public LlmRouteConfig resolve(
        long authorization,
        String dialect,
        String model)
    {
        LlmRouteConfig resolved = null;
        for (LlmRouteConfig route : routes)
        {
            if (route.authorized(authorization) && route.matches(dialect, model))
            {
                resolved = route;
                break;
            }
        }
        return resolved;
    }

    public LlmDialect resolveDialect(
        JsonEnvelope headers)
    {
        return dialects.resolve(headers);
    }

    public LlmDialect dialectNamed(
        String name)
    {
        return dialects.dialectNamed(name);
    }
}
