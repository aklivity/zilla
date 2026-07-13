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
package io.aklivity.zilla.runtime.common.asyncapi.view;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiServerConfig;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiServer;
import io.aklivity.zilla.runtime.common.asyncapi.model.resolver.AsyncapiResolver;

public final class AsyncapiServerView
{
    public final String name;
    public final String url;
    public final String literalUrl;
    public final String host;
    public final String literalHost;
    public final String hostname;
    public final int port;
    public final String pathname;
    public final String literalPathname;
    public final String title;
    public final String summary;
    public final String description;
    public final String protocol;
    public final String protocolVersion;
    public final List<String> tags;

    private final Map<String, Object> bindings;
    private final Map<String, Object> extensions;

    public boolean hasBinding(
        String name)
    {
        return bindings != null && bindings.containsKey(name);
    }

    public <T> Optional<T> binding(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(bindings != null ? type.cast(bindings.get(name)) : null);
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

    AsyncapiServerView(
        AsyncapiResolver resolver,
        String name,
        AsyncapiServer model,
        AsyncapiServerConfig config)
    {
        Map<String, AsyncapiServerVariableView> variables = model.variables != null
                ? model.variables.entrySet().stream()
                    .map(e -> new AsyncapiServerVariableView(resolver, e.getKey(), e.getValue()))
                    .collect(toMap(v -> v.name, identity()))
                : Map.of();

        VariableMatcher urlMatcher = model.url != null
            ? new VariableMatcher(variables::get, model.url)
            : null;
        VariableMatcher hostMatcher = model.host != null
            ? new VariableMatcher(variables::get, model.host)
            : null;
        VariableMatcher pathnameMatcher = model.pathname != null
            ? new VariableMatcher(variables::get, model.pathname)
            : null;

        this.name = name;
        this.url = urlMatcher != null
            ? urlMatcher.resolve(config != null ? config.url : null)
            : null;
        this.literalUrl = urlMatcher != null
            ? urlMatcher.resolve(null)
            : null;

        this.host = hostMatcher != null
            ? hostMatcher.resolve(config != null ? config.host : null)
            : null;
        this.literalHost = hostMatcher != null
            ? hostMatcher.resolve(null)
            : null;

        String urlOrHost = url != null ? url : "tcp://%s".formatted(host);
        this.hostname = urlOrHost != null
            ? URI.create(urlOrHost).getHost()
            : null;

        this.port = urlOrHost != null
            ? URI.create(urlOrHost).getPort()
            : 0;

        this.pathname = pathnameMatcher != null
            ? pathnameMatcher.resolve(config != null ? config.pathname : null)
            : null;
        this.literalPathname = pathnameMatcher != null
            ? pathnameMatcher.resolve(null)
            : null;

        this.title = model.title;
        this.summary = model.summary;
        this.description = model.description;
        this.protocol = model.protocol;
        this.protocolVersion = model.protocolVersion;
        this.tags = model.tags != null
            ? model.tags.stream()
                .map(tag -> tag.name)
                .toList()
            : null;
        this.bindings = model.bindings;
        this.extensions = model.extensions;
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
            Function<String, AsyncapiServerVariableView> resolver,
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
}
