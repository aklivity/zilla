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

import static java.util.stream.Collectors.toMap;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.ServiceLoader;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.llm.LlmOptionsConfig;
import io.aklivity.zilla.config.binding.llm.LlmOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.llm.LlmSignConfig;
import io.aklivity.zilla.config.binding.llm.LlmSignInfo;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.factory.Factory;

public final class LlmOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String DIALECT_NAME = "dialect";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_DEFAULT = "Bearer {credentials}";
    private static final String SERVER_NAME = "server";
    private static final String SIGN_NAME = "sign";
    private static final String SIGN_TYPE_NAME = "name";
    private static final String SIGN_OPTIONS_NAME = "options";

    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";
    private static final int PORT_HTTP = 80;
    private static final int PORT_HTTPS = 443;
    private static final String DEFAULT_PATH = "/v1";

    private final Map<String, ConfigAdapter<OptionsConfig, JsonObject>> signOptionsByType;

    public LlmOptionsConfigAdapter()
    {
        this.signOptionsByType = Factory.instantiate(ServiceLoader.load(LlmSignInfo.class))
            .stream()
            .collect(toMap(LlmSignInfo::type, LlmSignInfo::options));
    }

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

        if (llmOptions.sign != null)
        {
            object.add(SIGN_NAME, adaptSignToJson(llmOptions.sign));
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

        if (object.containsKey(SIGN_NAME))
        {
            llmOptions.sign(adaptSign(object.get(SIGN_NAME)));
        }

        return llmOptions.build();
    }

    private JsonValue adaptSignToJson(
        LlmSignConfig sign)
    {
        JsonValue value;
        if (sign.options != null)
        {
            JsonObjectBuilder signObject = Json.createObjectBuilder()
                .add(SIGN_TYPE_NAME, sign.name);

            ConfigAdapter<OptionsConfig, JsonObject> adapter = signOptionsByType.get(sign.name);
            if (adapter != null)
            {
                signObject.add(SIGN_OPTIONS_NAME, adapter.adaptToJson(sign.options));
            }

            value = signObject.build();
        }
        else
        {
            value = Json.createValue(sign.name);
        }
        return value;
    }

    private LlmSignConfig adaptSign(
        JsonValue value)
    {
        JsonObject object = value instanceof JsonString
            ? Json.createObjectBuilder().add(SIGN_TYPE_NAME, ((JsonString) value).getString()).build()
            : (JsonObject) value;

        String name = object.getString(SIGN_TYPE_NAME);
        OptionsConfig options = object.containsKey(SIGN_OPTIONS_NAME)
            ? adaptSignOptions(name, object.getJsonObject(SIGN_OPTIONS_NAME))
            : null;

        return LlmSignConfig.builder()
            .name(name)
            .options(options)
            .build();
    }

    private OptionsConfig adaptSignOptions(
        String name,
        JsonObject object)
    {
        ConfigAdapter<OptionsConfig, JsonObject> adapter = signOptionsByType.get(name);
        return adapter != null ? adapter.adaptFromJson(object) : null;
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
