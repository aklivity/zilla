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
package io.aklivity.zilla.config.embedding.openai.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.embedding.openai.OpenaiOptionsConfig;
import io.aklivity.zilla.config.embedding.openai.OpenaiOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class OpenaiOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String MODEL_NAME = "model";
    private static final String ENDPOINT_NAME = "endpoint";
    private static final String CREDENTIALS_NAME = "credentials";

    private final OpenaiCredentialsConfigAdapter credentials = new OpenaiCredentialsConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        OpenaiOptionsConfig config = (OpenaiOptionsConfig) options;
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(MODEL_NAME, config.model);

        if (!OpenaiOptionsConfig.DEFAULT_ENDPOINT.equals(config.endpoint))
        {
            object.add(ENDPOINT_NAME, config.endpoint);
        }

        object.add(CREDENTIALS_NAME, credentials.adaptToJson(config.credentials));

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        OpenaiOptionsConfigBuilder<OpenaiOptionsConfig> options = OpenaiOptionsConfig.builder();

        options.model(object.getString(MODEL_NAME));

        if (object.containsKey(ENDPOINT_NAME))
        {
            options.endpoint(object.getString(ENDPOINT_NAME));
        }

        options.credentials(credentials.adaptFromJson(object.getJsonObject(CREDENTIALS_NAME)));

        return options.build();
    }
}
