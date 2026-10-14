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
package io.aklivity.zilla.config.model.classify.internal;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ConfigExtAdapter;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.model.classify.ClassifyModelConfig;
import io.aklivity.zilla.config.model.classify.ClassifyModelConfigBuilder;
import io.aklivity.zilla.config.model.classify.ClassifyRejectConfig;

public final class ClassifyModelConfigAdapter extends ConfigAdapter.Extensible<ModelConfig, JsonValue>
{
    private static final String CLASSIFY = "classify";
    private static final String MODEL_NAME = "model";
    private static final String REJECT_NAME = "reject";

    public ClassifyModelConfigAdapter(
        List<ConfigExtAdapter<ModelConfig>> extensions)
    {
        super(extensions);
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig config)
    {
        ClassifyModelConfig model = (ClassifyModelConfig) config;
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(MODEL_NAME, CLASSIFY);

        JsonObjectBuilder reject = Json.createObjectBuilder();
        for (ClassifyRejectConfig entry : model.reject)
        {
            JsonArrayBuilder labels = Json.createArrayBuilder();
            entry.labels.forEach(labels::add);
            reject.add(entry.name, labels);
        }
        builder.add(REJECT_NAME, reject);

        injectExtensions(model, builder);

        return builder.build();
    }

    @Override
    public ModelConfig adaptFromJson(
        JsonValue value)
    {
        JsonObject object = (JsonObject) value;

        ClassifyModelConfigBuilder<ClassifyModelConfig> builder = ClassifyModelConfig.builder();

        if (object.containsKey(REJECT_NAME))
        {
            object.getJsonObject(REJECT_NAME).forEach((classifier, labels) ->
                builder.reject(classifier, asListString(labels.asJsonArray())));
        }

        injectExtensions(object, builder);

        return builder.build();
    }

    private static List<String> asListString(
        JsonArray array)
    {
        return array.stream()
            .map(ClassifyModelConfigAdapter::asString)
            .toList();
    }

    private static String asString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
