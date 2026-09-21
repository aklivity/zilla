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
package io.aklivity.zilla.config.binding.llm.internal;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.binding.llm.LlmOptionsConfig;
import io.aklivity.zilla.config.binding.llm.LlmOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class LlmOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String DIALECT_NAME = "dialect";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_DEFAULT = "Bearer {credentials}";
    private static final String SERVER_NAME = "server";

    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";
    private static final int PORT_HTTP = 80;
    private static final int PORT_HTTPS = 443;
    private static final String DEFAULT_PATH = "/v1";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        LlmOptionsConfig llmOptions = (LlmOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (llmOptions.dialect != null)
        {
            object.add(DIALECT_NAME, llmOptions.dialect);
        }

        if (llmOptions.authorization != null && llmOptions.authorization.name != null)
        {
            JsonObjectBuilder authorization = Json.createObjectBuilder();
            JsonObjectBuilder guardObject = Json.createObjectBuilder();
            if (llmOptions.authorization.credentials != null)
            {
                guardObject.add(AUTHORIZATION_CREDENTIALS_NAME, llmOptions.authorization.credentials);
            }
            authorization.add(llmOptions.authorization.name, guardObject);
            object.add(AUTHORIZATION_NAME, authorization);
        }

        if (llmOptions.server != null)
        {
            object.add(SERVER_NAME, llmOptions.server.toString());
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        LlmOptionsConfigBuilder<LlmOptionsConfig> llmOptions = LlmOptionsConfig.builder();

        if (object.containsKey(DIALECT_NAME))
        {
            llmOptions.dialect(object.getString(DIALECT_NAME));
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            JsonObject authorization = object.getJsonObject(AUTHORIZATION_NAME);
            authorization.forEach((guard, value) ->
            {
                JsonObject guardObject = (JsonObject) value;
                String credentials = guardObject.containsKey(AUTHORIZATION_CREDENTIALS_NAME)
                    ? ((JsonString) guardObject.get(AUTHORIZATION_CREDENTIALS_NAME)).getString()
                    : AUTHORIZATION_CREDENTIALS_DEFAULT;
                llmOptions.authorization()
                    .name(guard)
                    .credentials(credentials)
                    .build();
            });
        }

        if (object.containsKey(SERVER_NAME))
        {
            adaptServer(llmOptions, object.getString(SERVER_NAME));
        }

        return llmOptions.build();
    }

    // a server value that fails to parse as an http(s) URI is left absent, the same as an
    // unparseable host:port was before the option became a full URL
    private static void adaptServer(
        LlmOptionsConfigBuilder<LlmOptionsConfig> llmOptions,
        String server)
    {
        try
        {
            URI uri = new URI(server);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (host != null && (SCHEME_HTTP.equals(scheme) || SCHEME_HTTPS.equals(scheme)))
            {
                int port = uri.getPort() != -1 ? uri.getPort() : defaultPort(scheme);
                String path = uri.getPath() == null || uri.getPath().isEmpty() ? DEFAULT_PATH : uri.getPath();

                llmOptions.server()
                    .scheme(scheme)
                    .host(host)
                    .port(port)
                    .path(path)
                    .build();
            }
        }
        catch (URISyntaxException ex)
        {
        }
    }

    private static int defaultPort(
        String scheme)
    {
        return SCHEME_HTTPS.equals(scheme) ? PORT_HTTPS : PORT_HTTP;
    }
}
