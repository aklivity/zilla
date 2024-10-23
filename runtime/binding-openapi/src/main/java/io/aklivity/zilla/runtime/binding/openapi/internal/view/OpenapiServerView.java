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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiServer;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiVariable;

public final class OpenapiServerView
{
    private static final Pattern VARIABLE = Pattern.compile("\\{([^}]*.?)\\}");
    private final Matcher variable = VARIABLE.matcher("");

    private final OpenapiServer server;
    private String defaultUrl;

    public Matcher urlMatcher;

    public URI url()
    {
        return URI.create(server.url);
    }

    public int getPort()
    {
        return URI.create(server.url).getPort();
    }

    public void resolveURL(
        String url)
    {
        server.url = (url == null || url.isEmpty()) ? defaultUrl : url;
    }

    public static OpenapiServerView of(
        OpenapiServer server)
    {
        return new OpenapiServerView(server);
    }

    public static OpenapiServerView of(
        OpenapiServer server,
        Map<String, OpenapiVariable> variables)
    {
        return new OpenapiServerView(server, variables);
    }

    private OpenapiServerView(
        OpenapiServer server)
    {
        this.server = server;
    }

    private OpenapiServerView(
        OpenapiServer server,
        Map<String, OpenapiVariable> variables)
    {
        this.server = server;
        Pattern urlPattern = Pattern.compile(variable.reset(Optional.ofNullable(server.url).orElse(""))
            .replaceAll(mr -> OpenapiVariableView.of(variables, server.variables.get(mr.group(1))).values().stream()
                .collect(Collectors.joining("|", "(", ")"))));
        this.urlMatcher = urlPattern.matcher("");
        this.defaultUrl = variable.reset(Optional.ofNullable(server.url).orElse(""))
            .replaceAll(mr -> OpenapiVariableView.of(variables, server.variables.get(mr.group(1))).defaultValue());
    }
}
