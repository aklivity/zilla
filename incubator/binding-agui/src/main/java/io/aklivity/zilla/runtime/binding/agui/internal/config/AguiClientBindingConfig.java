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

import java.net.URI;
import java.util.List;

import io.aklivity.zilla.config.binding.agui.AguiAuthorizationConfig;
import io.aklivity.zilla.config.binding.agui.AguiOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class AguiClientBindingConfig
{
    public static final String CREDENTIALS_PLACEHOLDER = "{credentials}";

    private static final String DEFAULT_CREDENTIALS = "Bearer " + CREDENTIALS_PLACEHOLDER;
    private static final String DEFAULT_PATH = "/";

    public final long id;
    public final GuardHandler guard;
    public final String serverScheme;
    public final String serverAuthority;
    public final String serverPath;

    private final String credentials;
    private final List<AguiClientRouteConfig> routes;

    public AguiClientBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        final AguiOptionsConfig options = (AguiOptionsConfig) binding.options;
        final AguiAuthorizationConfig authorization = options != null ? options.authorization : null;
        final URI server = URI.create(options.server);

        this.id = binding.id;
        this.guard = authorization != null
            ? context.supplyGuard(binding.resolveId.applyAsLong(authorization.name))
            : null;
        this.credentials = authorization != null && authorization.credentials != null
            ? authorization.credentials
            : DEFAULT_CREDENTIALS;
        this.serverScheme = server.getScheme();
        this.serverAuthority = authorityOf(server);
        this.serverPath = pathOf(server);
        this.routes = binding.routes.stream()
            .map(r -> new AguiClientRouteConfig(r, server, this.serverPath))
            .toList();
    }

    public AguiClientRouteConfig resolve(
        String agent,
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.matches(agent, authorization))
            .findFirst()
            .orElse(null);
    }

    public String authorizationOf(
        long authorization)
    {
        final String credentials = guard != null && (authorization & GuardHandler.MASK_AUTHORIZED) != 0L
            ? guard.credentials(authorization)
            : null;

        return credentials != null ? this.credentials.replace(CREDENTIALS_PLACEHOLDER, credentials) : null;
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

        return path == null || path.isEmpty() ? DEFAULT_PATH : path;
    }
}
