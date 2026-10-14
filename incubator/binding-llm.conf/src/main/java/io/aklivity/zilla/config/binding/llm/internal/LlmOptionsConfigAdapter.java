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
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.llm.LlmGuardrailConfig;
import io.aklivity.zilla.config.binding.llm.LlmGuardrailConfigBuilder;
import io.aklivity.zilla.config.binding.llm.LlmGuardrailStageConfig;
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
    private static final String GUARDRAILS_NAME = "guardrails";
    private static final String REQUEST_NAME = "request";
    private static final String RESPONSE_NAME = "response";

    private final LlmGuardrailStageConfigAdapter stage = new LlmGuardrailStageConfigAdapter();

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

        if (!llmOptions.guardrails.isEmpty())
        {
            JsonObjectBuilder guardrails = Json.createObjectBuilder();
            for (LlmGuardrailConfig guardrail : llmOptions.guardrails)
            {
                JsonObjectBuilder entry = Json.createObjectBuilder();
                if (!guardrail.request.isEmpty())
                {
                    entry.add(REQUEST_NAME, adaptStagesToJson(guardrail.request));
                }
                if (!guardrail.response.isEmpty())
                {
                    entry.add(RESPONSE_NAME, adaptStagesToJson(guardrail.response));
                }
                guardrails.add(guardrail.name, entry);
            }
            object.add(GUARDRAILS_NAME, guardrails);
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

        if (object.containsKey(GUARDRAILS_NAME))
        {
            object.getJsonObject(GUARDRAILS_NAME).forEach((name, entry) ->
            {
                JsonObject guardrailObject = (JsonObject) entry;
                LlmGuardrailConfigBuilder<LlmGuardrailConfig> guardrail = LlmGuardrailConfig.builder().name(name);
                adaptStagesFromJson(guardrailObject, REQUEST_NAME).forEach(guardrail::request);
                adaptStagesFromJson(guardrailObject, RESPONSE_NAME).forEach(guardrail::response);
                llmOptions.guardrail(guardrail.build());
            });
        }

        return llmOptions.build();
    }

    private JsonArrayBuilder adaptStagesToJson(
        List<LlmGuardrailStageConfig> stages)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        stages.forEach(s -> array.add(stage.adaptToJson(s)));
        return array;
    }

    private List<LlmGuardrailStageConfig> adaptStagesFromJson(
        JsonObject guardrail,
        String direction)
    {
        JsonArray stages = guardrail.getOrDefault(direction, JsonValue.EMPTY_JSON_ARRAY).asJsonArray();
        return stages.stream()
            .map(JsonObject.class::cast)
            .map(stage::adaptFromJson)
            .toList();
    }

    private static void adaptServer(
        LlmOptionsConfigBuilder<LlmOptionsConfig> llmOptions,
        String server)
    {
        URI uri = URI.create(server);
        String scheme = uri.getScheme();
        int port = uri.getPort() != -1 ? uri.getPort() : defaultPort(scheme);
        String path = uri.getPath().isEmpty() ? DEFAULT_PATH : uri.getPath();

        llmOptions.server()
            .scheme(scheme)
            .host(uri.getHost())
            .port(port)
            .path(path)
            .build();
    }

    private static int defaultPort(
        String scheme)
    {
        return SCHEME_HTTPS.equals(scheme) ? PORT_HTTPS : PORT_HTTP;
    }
}
