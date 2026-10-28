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

import static java.util.stream.Collectors.toList;

import java.net.URI;
import java.util.List;

import io.aklivity.zilla.config.binding.a2a.A2aAuthorizationConfig;
import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class A2aClientBindingConfig
{
    public static final String CREDENTIALS_PLACEHOLDER = "{credentials}";

    private static final String DEFAULT_CREDENTIALS = "Bearer " + CREDENTIALS_PLACEHOLDER;
    private static final List<String> DEFAULT_VERSIONS = List.of("1.0", "0.3");

    public final long id;
    public final List<String> versions;
    public final GuardHandler guard;
    public final String credentials;
    public final String serverScheme;
    public final String serverAuthority;
    public final String serverPath;

    private final List<A2aClientRouteConfig> routes;

    public A2aClientBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        this.id = binding.id;

        final A2aOptionsConfig options = (A2aOptionsConfig) binding.options;
        final A2aAuthorizationConfig authorization = options != null ? options.authorization : null;

        this.versions = options != null && options.versions != null ? options.versions : DEFAULT_VERSIONS;
        this.guard = authorization != null
            ? context.supplyGuard(binding.resolveId.applyAsLong(authorization.name))
            : null;
        this.credentials = authorization != null && authorization.credentials != null
            ? authorization.credentials
            : DEFAULT_CREDENTIALS;

        final URI server = options != null && options.server != null ? URI.create(options.server) : null;
        this.serverScheme = server != null ? server.getScheme() : null;
        this.serverAuthority = server != null ? authorityOf(server) : null;
        this.serverPath = server != null ? pathOf(server) : null;

        this.routes = binding.routes.stream()
            .map(A2aClientRouteConfig::new)
            .collect(toList());
    }

    public A2aClientRouteConfig resolve(
        long authorization)
    {
        A2aClientRouteConfig resolved = null;

        for (A2aClientRouteConfig route : routes)
        {
            if (route.skills.isEmpty() && route.authorized(authorization))
            {
                resolved = route;
                break;
            }
        }

        return resolved;
    }

    public List<String> rolesOf(
        String skill)
    {
        return routes.stream()
            .filter(r -> r.guardsSkill(skill))
            .flatMap(r -> r.requiredRoles().stream())
            .distinct()
            .collect(toList());
    }

    public List<String> deniedRoles(
        List<String> skills,
        long authorization)
    {
        List<String> denied = List.of();

        if (!skills.isEmpty())
        {
            boolean restricted = true;
            boolean permitted = false;
            for (String skill : skills)
            {
                final List<A2aClientRouteConfig> guarding = routes.stream()
                    .filter(r -> r.guardsSkill(skill) && !r.roles.isEmpty())
                    .collect(toList());
                restricted &= !guarding.isEmpty();
                permitted |= guarding.stream().anyMatch(r -> r.authorized(authorization));
            }

            if (restricted && !permitted)
            {
                denied = skills.stream()
                    .flatMap(s -> rolesOf(s).stream())
                    .distinct()
                    .collect(toList());
            }
        }

        return denied;
    }

    public String authorizationOf(
        String credentials)
    {
        String header = null;

        if (this.credentials != null)
        {
            header = this.credentials.contains(CREDENTIALS_PLACEHOLDER)
                ? credentials != null ? this.credentials.replace(CREDENTIALS_PLACEHOLDER, credentials) : null
                : this.credentials;
        }

        return header;
    }

    private static String authorityOf(
        URI uri)
    {
        final int port = uri.getPort() != -1
            ? uri.getPort()
            : "https".equals(uri.getScheme()) ? 443 : 80;
        return String.format("%s:%d", uri.getHost(), port);
    }

    private static String pathOf(
        URI uri)
    {
        final String path = uri.getRawPath();
        return path == null || "/".equals(path) ? "" : path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
