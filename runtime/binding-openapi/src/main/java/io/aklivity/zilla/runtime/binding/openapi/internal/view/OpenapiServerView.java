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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiServer;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiServerView
{
    public final URI url;

    OpenapiServerView(
        OpenapiResolver resolver,
        OpenapiServer model)
    {
        this(resolver, model, null);
    }

    OpenapiServerView(
        OpenapiResolver resolver,
        OpenapiServer model,
        OpenapiServerConfig config)
    {
        Map<String, OpenapiVariableView> variables = model.variables != null
                ? model.variables.entrySet().stream()
                    .map(e -> new OpenapiVariableView(e.getKey(), e.getValue()))
                    .collect(toMap(v -> v.name, identity()))
                : Map.of();

        this.url = model.url != null
                ? resolvePorts(URI.create(new VariableMatcher(variables::get, model.url)
                        .resolve(config != null ? config.url : null)))
                : null;
    }


    public String requestPath(
        String path)
    {
        String serverPath = url.getPath();

        return serverPath != null && path != null
            ? serverPath.endsWith("/") ? serverPath.concat(path.substring(1)) : serverPath.concat(path)
            : path;
    }

    public static final class VariableMatcher
    {
        private static final Pattern VARIABLE = Pattern.compile("\\{([^}]*.?)\\}");

        private final Matcher matcher;
        private final String defaultValue;

        public String resolve(
            String value)
        {
            return value != null && matcher.reset(value).matches() ? value : defaultValue;
        }

        private VariableMatcher(
            Function<String, OpenapiVariableView> resolver,
            String value)
        {
            String regex = VARIABLE.matcher(value)
                .replaceAll(mr -> resolver.apply(mr.group(1)).values.stream()
                    .collect(joining("|", "(", ")")));

            this.matcher = Pattern.compile(regex).matcher("");
            this.defaultValue = VARIABLE.matcher(value)
                    .replaceAll(mr -> resolver.apply(mr.group(1)).defaultValue);
        }
    }

    private static URI resolvePorts(
        URI url)
    {
        String scheme = url.getScheme();
        int port = url.getPort();

        if (port == -1)
        {
            String userInfo = url.getUserInfo();
            String host = url.getHost();
            String path = url.getPath();
            String query = url.getQuery();
            String fragment = url.getFragment();

            int defaultPort = switch (scheme)
            {
            case "http" -> 80;
            case "https" -> 443;
            default -> port;
            };

            try
            {
                url = new URI(scheme, userInfo, host, defaultPort, path, query, fragment);
            }
            catch (URISyntaxException ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return url;
    }
}
