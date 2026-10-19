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
package io.aklivity.zilla.config.binding.mcp.internal;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.mcp.McpCacheToolsConfig;
import io.aklivity.zilla.config.binding.mcp.McpCacheToolsConfigBuilder;
import io.aklivity.zilla.config.binding.mcp.McpCacheToolsSearchConfig;
import io.aklivity.zilla.config.binding.mcp.McpCacheToolsSearchConfigBuilder;
import io.aklivity.zilla.config.binding.mcp.McpKeywordToolSearchIndexConfig;
import io.aklivity.zilla.config.binding.mcp.McpToolSearchIndexConfig;
import io.aklivity.zilla.config.engine.ConfigExtAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpCacheToolsConfigAdapter
{
    private static final String SEARCH_NAME = "search";
    private static final String SEARCH_TOOLKIT_NAME = "toolkit";
    private static final String SEARCH_LIMIT_NAME = "limit";
    private static final int SEARCH_LIMIT_DEFAULT = 5;
    private static final String SEARCH_FIELDS_NAME = "fields";
    private static final List<String> SEARCH_FIELDS_DEFAULT = List.of("name", "description");
    private static final String SEARCH_WEIGHTS_NAME = "weights";
    private static final String SEARCH_TYPE_NAME = "type";
    private static final String SEARCH_INDEX_NAME = "index";
    private static final String SEARCH_TYPE_DEFAULT = McpKeywordToolSearchIndexConfig.NAME;
    private static final String EAGER_NAME = "eager";
    private static final String EAGER_TYPE_NAME = "type";
    private static final String EAGER_POLICY_NAME = "policy";

    private final McpToolSearchIndexConfigAdapter index;
    private final McpToolsEagerConfigAdapter eager;

    public McpCacheToolsConfigAdapter(
        List<ConfigExtAdapter<OptionsConfig>> extensions)
    {
        this.index = new McpToolSearchIndexConfigAdapter(extensions);
        this.eager = new McpToolsEagerConfigAdapter(extensions);
    }

    public JsonObject adaptToJson(
        McpCacheToolsConfig tools)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (tools.search != null)
        {
            McpCacheToolsSearchConfig search = tools.search;
            JsonObjectBuilder searchObject = Json.createObjectBuilder();

            if (search.toolkit != null)
            {
                searchObject.add(SEARCH_TOOLKIT_NAME, search.toolkit);
            }

            searchObject.add(SEARCH_LIMIT_NAME, search.limit);

            if (search.fields != null)
            {
                JsonArrayBuilder fields = Json.createArrayBuilder();
                search.fields.forEach(fields::add);
                searchObject.add(SEARCH_FIELDS_NAME, fields);
            }

            if (search.weights != null && !search.weights.isEmpty())
            {
                JsonObjectBuilder weights = Json.createObjectBuilder();
                search.weights.forEach(weights::add);
                searchObject.add(SEARCH_WEIGHTS_NAME, weights);
            }

            if (search.indexes != null)
            {
                JsonArrayBuilder indexes = Json.createArrayBuilder();
                search.indexes.forEach(each -> indexes.add(index.adaptToJson(each)));
                searchObject.add(SEARCH_INDEX_NAME, indexes);
            }

            object.add(SEARCH_NAME, searchObject);
        }

        if (tools.eager != null)
        {
            JsonArrayBuilder eagerArray = Json.createArrayBuilder();
            tools.eager.forEach(each -> eagerArray.add(eager.adaptToJson(each)));
            object.add(EAGER_NAME, eagerArray);
        }

        return object.build();
    }

    public McpCacheToolsConfig adaptFromJson(
        JsonObject object)
    {
        McpCacheToolsConfigBuilder<McpCacheToolsConfig> builder = McpCacheToolsConfig.builder();

        if (object.containsKey(SEARCH_NAME))
        {
            JsonObject search = object.getJsonObject(SEARCH_NAME);

            McpCacheToolsSearchConfigBuilder<McpCacheToolsConfigBuilder<McpCacheToolsConfig>> searchBuilder = builder.search()
                .limit(search.containsKey(SEARCH_LIMIT_NAME) ? search.getInt(SEARCH_LIMIT_NAME) : SEARCH_LIMIT_DEFAULT);

            if (search.containsKey(SEARCH_TOOLKIT_NAME))
            {
                searchBuilder.toolkit(search.getString(SEARCH_TOOLKIT_NAME));
            }

            List<String> fields = search.containsKey(SEARCH_FIELDS_NAME)
                ? search.getJsonArray(SEARCH_FIELDS_NAME).getValuesAs(JsonString.class).stream()
                    .map(JsonString::getString)
                    .collect(Collectors.toList())
                : SEARCH_FIELDS_DEFAULT;
            searchBuilder.fields(fields);

            if (search.containsKey(SEARCH_WEIGHTS_NAME))
            {
                JsonObject weights = search.getJsonObject(SEARCH_WEIGHTS_NAME);
                weights.forEach((field, value) -> searchBuilder.weight(field, ((JsonNumber) value).doubleValue()));
            }

            if (search.containsKey(SEARCH_INDEX_NAME))
            {
                search.getJsonArray(SEARCH_INDEX_NAME).stream()
                    .map(JsonValue::asJsonObject)
                    .map(index::adaptFromJson)
                    .forEach(searchBuilder::index);
            }
            else
            {
                String type = search.containsKey(SEARCH_TYPE_NAME)
                    ? search.getString(SEARCH_TYPE_NAME)
                    : SEARCH_TYPE_DEFAULT;
                JsonObject indexObject = Json.createObjectBuilder()
                    .add(SEARCH_TYPE_NAME, type)
                    .build();
                McpToolSearchIndexConfig indexConfig = index.adaptFromJson(indexObject);
                searchBuilder.index(indexConfig);
            }

            searchBuilder.build();
        }

        if (object.containsKey(EAGER_NAME))
        {
            JsonValue value = object.get(EAGER_NAME);
            if (value.getValueType() == JsonValue.ValueType.ARRAY)
            {
                value.asJsonArray().stream()
                    .map(JsonValue::asJsonObject)
                    .map(eager::adaptFromJson)
                    .forEach(builder::eager);
            }
            else
            {
                builder.eager(eager.adaptFromJson(normalizeEagerLegacyShape(value.asJsonObject())));
            }
        }

        return builder.build();
    }

    // pre-#887 configs discriminated the single eager policy object via "policy", not "type" -- normalize
    // it here so both spellings keep parsing into the same list-shaped representation, with no second
    // internal representation and no change needed to the dispatch adapter itself
    private static JsonObject normalizeEagerLegacyShape(
        JsonObject object)
    {
        return object.containsKey(EAGER_POLICY_NAME) && !object.containsKey(EAGER_TYPE_NAME)
            ? Json.createObjectBuilder(object)
                .remove(EAGER_POLICY_NAME)
                .add(EAGER_TYPE_NAME, object.getString(EAGER_POLICY_NAME))
                .build()
            : object;
    }
}
