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
package io.aklivity.zilla.config.classifier.patterns.internal;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

import io.aklivity.zilla.config.classifier.patterns.PatternsClassifierOptionsConfig;
import io.aklivity.zilla.config.classifier.patterns.PatternsClassifierOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class PatternsClassifierOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String ENTROPY_NAME = "entropy";
    private static final String LABELS_NAME = "labels";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        PatternsClassifierOptionsConfig patterns = (PatternsClassifierOptionsConfig) options;

        JsonObjectBuilder labels = Json.createObjectBuilder();
        patterns.labels.forEach((alias, expressions) ->
        {
            if (expressions.size() == 1)
            {
                labels.add(alias, expressions.get(0).pattern());
            }
            else
            {
                JsonArrayBuilder array = Json.createArrayBuilder();
                expressions.forEach(expression -> array.add(expression.pattern()));
                labels.add(alias, array);
            }
        });

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (patterns.entropy > 0.0)
        {
            object.add(ENTROPY_NAME, patterns.entropy);
        }

        return object
            .add(LABELS_NAME, labels)
            .build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        PatternsClassifierOptionsConfigBuilder<PatternsClassifierOptionsConfig> options =
            PatternsClassifierOptionsConfig.builder();

        if (object.containsKey(ENTROPY_NAME))
        {
            options.entropy(object.getJsonNumber(ENTROPY_NAME).doubleValue());
        }

        if (object.containsKey(LABELS_NAME))
        {
            JsonObject labels = object.getJsonObject(LABELS_NAME);
            labels.forEach((alias, expressions) -> options.label(alias, asExpressions(expressions)));
        }

        return options.build();
    }

    private static List<String> asExpressions(
        JsonValue value)
    {
        return value.getValueType() == ValueType.ARRAY
            ? ((JsonArray) value).getValuesAs(JsonString.class).stream().map(JsonString::getString).toList()
            : List.of(((JsonString) value).getString());
    }
}
