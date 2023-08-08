/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public class KafkaCatalogConfigAdapter implements JsonbAdapter<KafkaCatalogConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String CATALOG_STRATEGY = "strategy";
    private static final String CATALOG_VERSION = "version";
    private static final String CATALOG_ID = "id";
    private static final String TOPICS_NAME = "topics";

    private final KafkaTopicCatalogConfigHandler topicConfigHandler = new KafkaTopicCatalogConfigHandler();

    @Override
    public JsonObject adaptToJson(
        KafkaCatalogConfig kafkaCatalogConfig)
    {
        JsonObjectBuilder catalogs = Json.createObjectBuilder();
        JsonObjectBuilder catalog = Json.createObjectBuilder();

        if (kafkaCatalogConfig.strategy != null &&
                !kafkaCatalogConfig.strategy.isEmpty())
        {
            catalog.add(CATALOG_STRATEGY, kafkaCatalogConfig.strategy);
        }

        if (kafkaCatalogConfig.version != null &&
                !kafkaCatalogConfig.version.isEmpty())
        {
            catalog.add(CATALOG_VERSION, kafkaCatalogConfig.version);
        }

        if (kafkaCatalogConfig.id > 0)
        {
            catalog.add(CATALOG_ID, kafkaCatalogConfig.id);
        }

        if (kafkaCatalogConfig.topics != null &&
                !kafkaCatalogConfig.topics.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            kafkaCatalogConfig.topics.forEach(t -> entries.add(topicConfigHandler.adaptToJson(t)));

            catalog.add(TOPICS_NAME, entries);
        }

        if (kafkaCatalogConfig.name != null &&
                !kafkaCatalogConfig.name.isEmpty())
        {
            catalogs.add(kafkaCatalogConfig.name, catalog);
        }

        return catalogs.build();
    }

    @Override
    public KafkaCatalogConfig adaptFromJson(
        JsonObject catalog)
    {
        String name = catalog.containsKey(NAME_NAME)
                ? catalog.getString(NAME_NAME)
                : null;

        JsonArray topicsArray = catalog.containsKey(TOPICS_NAME)
                ? catalog.getJsonArray(TOPICS_NAME)
                : null;

        String strategy = catalog.containsKey(CATALOG_STRATEGY)
                ? catalog.getString(CATALOG_STRATEGY)
                : null;

        String version = catalog.containsKey(CATALOG_VERSION)
                ? catalog.getString(CATALOG_VERSION)
                : null;

        int id = catalog.containsKey(CATALOG_ID)
                ? catalog.getInt(CATALOG_ID)
                : null;

        List<KafkaTopicCatalogConfig> topics = null;

        if (topicsArray != null)
        {
            List<KafkaTopicCatalogConfig> topics0 = new ArrayList<>();
            topicsArray.forEach(t -> topics0.add(topicConfigHandler.adaptFromJson(t.asJsonObject())));
            topics = topics0;
        }

        return new KafkaCatalogConfig(name, strategy, version, id, topics);
    }

}
