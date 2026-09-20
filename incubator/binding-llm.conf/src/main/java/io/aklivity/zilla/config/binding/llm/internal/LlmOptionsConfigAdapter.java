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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String BASE_PATH_NAME = "basePath";

    private static final Pattern SERVER_PATTERN = Pattern.compile("([^\\:]+):(\\d+)");

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
            object.add(SERVER_NAME, String.format("%s:%d", llmOptions.server.host, llmOptions.server.port));
        }

        if (llmOptions.basePath != null)
        {
            object.add(BASE_PATH_NAME, llmOptions.basePath);
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
            Matcher matcher = SERVER_PATTERN.matcher(object.getString(SERVER_NAME));
            if (matcher.matches())
            {
                llmOptions.server()
                    .host(matcher.group(1))
                    .port(Integer.parseInt(matcher.group(2)))
                    .build();
            }
        }

        if (object.containsKey(BASE_PATH_NAME))
        {
            llmOptions.basePath(object.getString(BASE_PATH_NAME));
        }

        return llmOptions.build();
    }
}
