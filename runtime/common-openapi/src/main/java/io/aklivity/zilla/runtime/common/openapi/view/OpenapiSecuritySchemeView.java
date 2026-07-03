/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.openapi.view;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.aklivity.zilla.runtime.common.openapi.model.OpenapiSecurityScheme;
import io.aklivity.zilla.runtime.common.openapi.model.resolver.OpenapiResolver;

public final class OpenapiSecuritySchemeView
{
    public final String name;
    public final String type;
    public final String in;
    public final String scheme;
    public final String bearerFormat;
    public final String openidConnectUrl;
    public final OpenapiOAuthFlowsView flows;
    public final List<String> scopes;

    private final Map<String, Object> extensions;

    OpenapiSecuritySchemeView(
        OpenapiResolver resolver,
        OpenapiSecurityScheme model)
    {
        this(resolver, resolver.securitySchemes.resolveRef(model.ref), model);
    }

    OpenapiSecuritySchemeView(
        OpenapiResolver resolver,
        String name,
        OpenapiSecurityScheme model)
    {
        final OpenapiSecurityScheme resolved = resolver.securitySchemes.resolve(model);

        this.name = name;
        this.type = resolved.type;
        this.in = resolved.in;
        this.scheme = resolved.scheme;
        this.bearerFormat = resolved.bearerFormat;
        this.openidConnectUrl = resolved.openidConnectUrl;
        this.flows = resolved.flows != null ? new OpenapiOAuthFlowsView(resolved.flows) : null;
        this.scopes = this.flows != null ? resolveScopes(this.flows) : List.of();
        this.extensions = resolved.extensions;
    }

    public boolean hasExtension(
        String name)
    {
        return extensions != null && extensions.containsKey(name);
    }

    public <T> Optional<T> extension(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(extensions != null ? type.cast(extensions.get(name)) : null);
    }

    private static List<String> resolveScopes(
        OpenapiOAuthFlowsView flows)
    {
        Set<String> scopes = new LinkedHashSet<>();
        Stream.of(flows.implicit, flows.password, flows.clientCredentials, flows.authorizationCode)
            .filter(Objects::nonNull)
            .map(flow -> flow.scopes)
            .filter(Objects::nonNull)
            .forEach(flowScopes -> scopes.addAll(flowScopes.keySet()));

        return List.copyOf(scopes);
    }
}
