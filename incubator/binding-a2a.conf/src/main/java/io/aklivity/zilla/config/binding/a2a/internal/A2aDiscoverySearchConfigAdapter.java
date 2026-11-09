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
package io.aklivity.zilla.config.binding.a2a.internal;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.a2a.A2aDiscoverySearchConfig;
import io.aklivity.zilla.config.binding.a2a.A2aDiscoverySearchConfigBuilder;
import io.aklivity.zilla.config.binding.a2a.A2aKeywordSearchIndexConfig;
import io.aklivity.zilla.config.binding.a2a.A2aSearchIndexConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ConfigExtAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class A2aDiscoverySearchConfigAdapter extends ConfigAdapter<A2aDiscoverySearchConfig, JsonObject>
{
    private static final String LIMIT_NAME = "limit";
    private static final int LIMIT_DEFAULT = 5;
    private static final String FIELDS_NAME = "fields";
    private static final List<String> FIELDS_DEFAULT = List.of("name", "description", "skills");
    private static final String WEIGHTS_NAME = "weights";
    private static final String TYPE_NAME = "type";
    private static final String INDEX_NAME = "index";

    private final A2aSearchIndexConfigAdapter index;

    public A2aDiscoverySearchConfigAdapter(
        List<ConfigExtAdapter<OptionsConfig>> extensions)
    {
        this.index = new A2aSearchIndexConfigAdapter(extensions);
    }

    @Override
    public JsonObject adaptToJson(
        A2aDiscoverySearchConfig search)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (search.limit > 0)
        {
            object.add(LIMIT_NAME, search.limit);
        }

        if (search.fields != null)
        {
            JsonArrayBuilder fields = Json.createArrayBuilder();
            search.fields.forEach(fields::add);
            object.add(FIELDS_NAME, fields);
        }

        if (search.weights != null && !search.weights.isEmpty())
        {
            JsonObjectBuilder weights = Json.createObjectBuilder();
            search.weights.forEach(weights::add);
            object.add(WEIGHTS_NAME, weights);
        }

        if (search.indexes != null)
        {
            if (search.indexes.size() == 1 && A2aKeywordSearchIndexConfig.NAME.equals(search.indexes.get(0).type))
            {
                object.add(TYPE_NAME, search.indexes.get(0).type);
            }
            else
            {
                JsonArrayBuilder indexes = Json.createArrayBuilder();
                search.indexes.forEach(each -> indexes.add(index.adaptToJson(each)));
                object.add(INDEX_NAME, indexes);
            }
        }

        return object.build();
    }

    @Override
    public A2aDiscoverySearchConfig adaptFromJson(
        JsonObject object)
    {
        A2aDiscoverySearchConfigBuilder<A2aDiscoverySearchConfig> builder = A2aDiscoverySearchConfig.builder()
            .limit(object.getInt(LIMIT_NAME, LIMIT_DEFAULT))
            .fields(object.containsKey(FIELDS_NAME)
                ? object.getJsonArray(FIELDS_NAME).getValuesAs(JsonString.class).stream()
                    .map(JsonString::getString)
                    .toList()
                : FIELDS_DEFAULT);

        if (object.containsKey(WEIGHTS_NAME))
        {
            object.getJsonObject(WEIGHTS_NAME)
                .forEach((field, value) -> builder.weight(field, ((JsonNumber) value).doubleValue()));
        }

        if (object.containsKey(INDEX_NAME))
        {
            object.getJsonArray(INDEX_NAME).stream()
                .map(JsonValue::asJsonObject)
                .map(index::adaptFromJson)
                .forEach(builder::index);
        }
        else
        {
            A2aSearchIndexConfig keyword = index.adaptFromJson(Json.createObjectBuilder()
                .add(TYPE_NAME, object.getString(TYPE_NAME, A2aKeywordSearchIndexConfig.NAME))
                .build());
            builder.index(keyword);
        }

        return builder.build();
    }
}
