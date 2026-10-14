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

import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.config.engine.ClassifierInfo;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.GenericClassifierConfig;
import io.aklivity.zilla.config.engine.GenericClassifierConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class ClassifierConfigAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String EMBEDDING_NAME = "embedding";
    private static final String STORE_NAME = "store";
    private static final String OPTIONS_NAME = "options";

    private final String type;
    private final ConfigAdapter<OptionsConfig, JsonObject> options;

    public ClassifierConfigAdapter(
        ClassifierInfo info)
    {
        this.type = info.type();
        this.options = info.options();
    }

    public JsonObject adaptToJson(
        ClassifierConfig classifier)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TYPE_NAME, classifier.type);

        if (classifier.embedding != null)
        {
            object.add(EMBEDDING_NAME, classifier.embedding);
        }

        if (classifier.store != null)
        {
            object.add(STORE_NAME, classifier.store);
        }

        if (classifier.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(classifier.options));
        }

        return object.build();
    }

    public ClassifierConfig adaptFromJson(
        String namespace,
        String name,
        JsonObject object)
    {
        GenericClassifierConfigBuilder<GenericClassifierConfig> builder = GenericClassifierConfig.builder()
            .namespace(namespace)
            .name(name)
            .type(type);

        if (object.containsKey(EMBEDDING_NAME))
        {
            builder.embedding(object.getString(EMBEDDING_NAME));
        }

        if (object.containsKey(STORE_NAME))
        {
            builder.store(object.getString(STORE_NAME));
        }

        if (object.containsKey(OPTIONS_NAME))
        {
            builder.options(options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)));
        }

        return builder.build();
    }
}
