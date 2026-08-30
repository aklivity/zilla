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
package io.aklivity.zilla.config.model.vector.internal;

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
import io.aklivity.zilla.config.model.vector.VectorModelConfig;
import io.aklivity.zilla.config.model.vector.VectorModelConfigBuilder;

public final class VectorModelConfigAdapter extends ConfigAdapter.Extensible<ModelConfig, JsonValue>
{
    private static final String VECTOR = "vector";
    private static final String MODEL_NAME = "model";
    private static final String EMBEDDING_NAME = "embedding";
    private static final String REJECT_NAME = "reject";
    private static final String THRESHOLD_NAME = "threshold";

    public VectorModelConfigAdapter(
        List<ConfigExtAdapter<ModelConfig>> extensions)
    {
        super(extensions);
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig config)
    {
        VectorModelConfig model = (VectorModelConfig) config;
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(MODEL_NAME, VECTOR);

        if (model.embedding != null)
        {
            builder.add(EMBEDDING_NAME, model.embedding.name);
        }

        if (model.reject != null)
        {
            JsonArrayBuilder reject = Json.createArrayBuilder();
            model.reject.forEach(reject::add);
            builder.add(REJECT_NAME, reject);
        }

        builder.add(THRESHOLD_NAME, model.threshold);

        injectExtensions(model, builder);

        return builder.build();
    }

    @Override
    public ModelConfig adaptFromJson(
        JsonValue value)
    {
        JsonObject object = (JsonObject) value;

        String embedding = object.containsKey(EMBEDDING_NAME)
            ? object.getString(EMBEDDING_NAME)
            : null;

        List<String> reject = object.containsKey(REJECT_NAME)
            ? asListString(object.getJsonArray(REJECT_NAME))
            : null;

        double threshold = object.containsKey(THRESHOLD_NAME)
            ? object.getJsonNumber(THRESHOLD_NAME).doubleValue()
            : 0.0;

        VectorModelConfigBuilder<VectorModelConfig> builder = VectorModelConfig.builder()
            .embedding(embedding)
            .reject(reject)
            .threshold(threshold);

        injectExtensions(object, builder);

        return builder.build();
    }

    private static List<String> asListString(
        JsonArray array)
    {
        return array.stream()
            .map(VectorModelConfigAdapter::asString)
            .toList();
    }

    private static String asString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
