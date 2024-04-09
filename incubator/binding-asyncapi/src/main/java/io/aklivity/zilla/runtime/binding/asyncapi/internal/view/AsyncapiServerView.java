/*
 * Copyright 2021-2023 Aklivity Inc
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

import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiProtocol;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiServer;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiVariable;

public final class AsyncapiServerView
{
    private static final Pattern VARIABLE = Pattern.compile("\\{([^}]*.?)\\}");
    private final Matcher variable = VARIABLE.matcher("");

    private final AsyncapiServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AsyncapiProtocol asyncapiProtocol;
    private String defaultHost;
    private String defaultUrl;
    private String defaultPathname;

    public Matcher hostMatcher;
    public Matcher urlMatcher;
    public Matcher pathnameMatcher;

    public URI url()
    {
        return URI.create(server.host);
    }

    public List<Map<String, List<String>>> security()
    {
        List<Map<String, List<String>>> security = null;
        if (server.security != null)
        {
            try
            {
                security = objectMapper.readValue(server.security.toString(), new TypeReference<>()
                {
                });
            }
            catch (JsonProcessingException e)
            {
                rethrowUnchecked(e);
            }
        }

        return security;
    }

    public String protocol()
    {
        return server.protocol;
    }

    public void resolveHost(
        String host,
        String url)
    {
        if (!defaultHost.isEmpty())
        {
            server.host = (host == null || host.isEmpty()) ? defaultHost : host;
        }
        else
        {
            server.host = (url == null || url.isEmpty()) ? defaultUrl : url;
        }
    }

    public String host()
    {
        return server.host;
    }

    public int getPort()
    {
        final String[] hostAndPort = host().split(":");
        return Integer.parseInt(hostAndPort[1]);
    }

    public void resolveUrl(
        String url)
    {
        server.url = (url == null || url.isEmpty()) ? defaultHost : url;
    }

    public String url2()
    {
        return server.url;
    }

    public String scheme()
    {
        return url().getScheme();
    }

    public String authority()
    {
        return String.format("%s:%d", url().getHost(), url().getPort());
    }

    public void setAsyncapiProtocol(
        AsyncapiProtocol protocol)
    {
        this.asyncapiProtocol = protocol;
    }

    public AsyncapiProtocol getAsyncapiProtocol()
    {
        return this.asyncapiProtocol;
    }

    public static AsyncapiServerView of(
        AsyncapiServer asyncapiServer)
    {
        return new AsyncapiServerView(asyncapiServer);
    }

    public static AsyncapiServerView of(
        AsyncapiServer asyncapiServer,
        Map<String, AsyncapiVariable> variables)
    {
        return new AsyncapiServerView(asyncapiServer, variables);
    }

    private AsyncapiServerView(
        AsyncapiServer server)
    {
        this.server = server;
    }

    private AsyncapiServerView(
        AsyncapiServer server,
        Map<String, AsyncapiVariable> variables)
    {
        this.server = server;
        Pattern hostPattern = Pattern.compile(variable.reset(Optional.ofNullable(server.host).orElse(""))
            .replaceAll(mr -> AsyncapiVariableView.of(variables, server.variables.get(mr.group(1))).values().stream()
                .collect(Collectors.joining("|", "(", ")"))));
        this.hostMatcher = hostPattern.matcher("");
        Pattern urlPattern = Pattern.compile(variable.reset(Optional.ofNullable(server.url).orElse(""))
            .replaceAll(mr -> AsyncapiVariableView.of(variables, server.variables.get(mr.group(1))).values().stream()
                .collect(Collectors.joining("|", "(", ")"))));
        this.urlMatcher = urlPattern.matcher("");
        Pattern pathnamePattern = Pattern.compile(variable.reset(Optional.ofNullable(server.pathname).orElse(""))
            .replaceAll(mr -> AsyncapiVariableView.of(variables, server.variables.get(mr.group(1))).values().stream()
                .collect(Collectors.joining("|", "(", ")"))));
        this.pathnameMatcher = pathnamePattern.matcher("");
        this.defaultHost = variable.reset(Optional.ofNullable(server.host).orElse(""))
            .replaceAll(mr -> AsyncapiVariableView.of(variables, server.variables.get(mr.group(1))).defaultValue());
        this.defaultUrl = variable.reset(Optional.ofNullable(server.url).orElse(""))
            .replaceAll(mr -> AsyncapiVariableView.of(variables, server.variables.get(mr.group(1))).defaultValue());
        this.defaultPathname = variable.reset(Optional.ofNullable(server.pathname).orElse(""))
            .replaceAll(mr -> AsyncapiVariableView.of(variables, server.variables.get(mr.group(1))).defaultValue());
    }
}
