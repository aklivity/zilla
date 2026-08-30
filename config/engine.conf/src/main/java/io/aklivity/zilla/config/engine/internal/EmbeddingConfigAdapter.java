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
package io.aklivity.zilla.config.engine.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.EmbeddingConfig;
import io.aklivity.zilla.config.engine.EmbeddingInfo;
import io.aklivity.zilla.config.engine.GenericEmbeddingConfig;
import io.aklivity.zilla.config.engine.GenericEmbeddingConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class EmbeddingConfigAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String VAULT_NAME = "vault";
    private static final String OPTIONS_NAME = "options";

    private final String type;
    private final ConfigAdapter<OptionsConfig, JsonObject> options;

    public EmbeddingConfigAdapter(
        EmbeddingInfo info)
    {
        this.type = info.type();
        this.options = info.options();
    }

    public JsonObject adaptToJson(
        EmbeddingConfig embedding)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TYPE_NAME, embedding.type);

        if (embedding.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(embedding.options));
        }

        return object.build();
    }

    public EmbeddingConfig adaptFromJson(
        String namespace,
        String name,
        JsonObject object)
    {
        GenericEmbeddingConfigBuilder<GenericEmbeddingConfig> builder = GenericEmbeddingConfig.builder()
            .namespace(namespace)
            .name(name)
            .type(type);

        if (object.containsKey(VAULT_NAME))
        {
            builder.vault(object.getString(VAULT_NAME));
        }

        if (object.containsKey(OPTIONS_NAME))
        {
            builder.options(options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)));
        }

        return builder.build();
    }
}
