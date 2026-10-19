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
package io.aklivity.zilla.config.classifier.semantic.internal;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.classifier.semantic.SemanticClassifierOptionsConfig;
import io.aklivity.zilla.config.classifier.semantic.SemanticClassifierOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class SemanticClassifierOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String THRESHOLD_NAME = "threshold";
    private static final String LABELS_NAME = "labels";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        SemanticClassifierOptionsConfig semantic = (SemanticClassifierOptionsConfig) options;

        JsonObjectBuilder labels = Json.createObjectBuilder();
        semantic.labels.forEach((alias, phrases) ->
        {
            JsonArrayBuilder exemplars = Json.createArrayBuilder();
            phrases.forEach(exemplars::add);
            labels.add(alias, exemplars);
        });

        return Json.createObjectBuilder()
            .add(THRESHOLD_NAME, semantic.threshold)
            .add(LABELS_NAME, labels)
            .build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        SemanticClassifierOptionsConfigBuilder<SemanticClassifierOptionsConfig> options =
            SemanticClassifierOptionsConfig.builder();

        if (object.containsKey(THRESHOLD_NAME))
        {
            options.threshold(object.getJsonNumber(THRESHOLD_NAME).doubleValue());
        }

        if (object.containsKey(LABELS_NAME))
        {
            JsonObject labels = object.getJsonObject(LABELS_NAME);
            labels.forEach((alias, phrases) -> options.label(alias, asPhrases(phrases.asJsonArray())));
        }

        return options.build();
    }

    private static List<String> asPhrases(
        JsonArray array)
    {
        return array.getValuesAs(JsonString.class).stream()
            .map(JsonString::getString)
            .toList();
    }
}
