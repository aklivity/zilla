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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.view;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiServerConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiServer;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.AsyncapiServerBindings;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver.AsyncapiResolver;

public final class AsyncapiServerView
{
    public final String name;
    public final String url;
    public final String host;
    public final String hostname;
    public final int port;
    public final String pathname;
    public final String protocol;
    public final AsyncapiServerBindings bindings;

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

        this.name = name;
        this.url = model.url != null
            ? new VariableMatcher(variables::get, model.url)
                    .resolve(config != null ? config.url : null)
            : null;

        this.host = model.host != null
            ? new VariableMatcher(variables::get, model.host)
                    .resolve(config != null ? config.host : null)
            : null;

        String urlOrHost = url != null ? url : "tcp://%s".formatted(host);
        this.hostname = urlOrHost != null
            ? URI.create(urlOrHost).getHost()
            : null;

        this.port = urlOrHost != null
            ? URI.create(urlOrHost).getPort()
            : 0;

        this.pathname = model.pathname != null
            ? new VariableMatcher(variables::get, model.pathname)
                    .resolve(config != null ? config.pathname : null)
            : null;

        this.protocol = model.protocol;
        this.bindings = model.bindings;
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
