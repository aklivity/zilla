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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public class KafkaCatalogConfigAdapter implements JsonbAdapter<KafkaCatalogConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String TOPIC_NAME = "topic";
    private static final String CATALOG_KEY = "key";
    private static final String CATALOG_VALUE = "value";

    private final KafkaSchemaConfigHandler schemaConfigHandler = new KafkaSchemaConfigHandler();

    @Override
    public JsonObject adaptToJson(
        KafkaCatalogConfig kafkaCatalogConfig)
    {
        JsonObjectBuilder catalogs = Json.createObjectBuilder();
        JsonObjectBuilder catalog = Json.createObjectBuilder();

        if (kafkaCatalogConfig.topic != null &&
                !kafkaCatalogConfig.topic.isEmpty())
        {
            catalog.add(TOPIC_NAME, kafkaCatalogConfig.topic);
        }

        if (kafkaCatalogConfig.key != null)
        {
            catalog.add(CATALOG_KEY, schemaConfigHandler.adaptToJson(kafkaCatalogConfig.key));
        }

        if (kafkaCatalogConfig.value != null)
        {
            catalog.add(CATALOG_VALUE, schemaConfigHandler.adaptToJson(kafkaCatalogConfig.value));
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
        if (catalog != null)
        {
            String name = catalog.containsKey(NAME_NAME)
                    ? catalog.getString(NAME_NAME)
                    : null;
            String topic = catalog.containsKey(TOPIC_NAME)
                    ? catalog.getString(TOPIC_NAME) :
                    null;

            JsonObject key = catalog.containsKey(CATALOG_KEY)
                    ? catalog.getJsonObject(CATALOG_KEY)
                    : null;

            KafkaSchemaConfig keyConfig = null;
            if (key != null)
            {
                keyConfig = schemaConfigHandler.adaptFromJson(key);
            }

            JsonObject value = catalog.containsKey(CATALOG_VALUE)
                    ? catalog.getJsonObject(CATALOG_VALUE)
                    : null;

            KafkaSchemaConfig valueConfig = null;
            if (value != null)
            {
                valueConfig = schemaConfigHandler.adaptFromJson(key);
            }

            new KafkaCatalogConfig(name, topic, keyConfig, valueConfig);
        }

        return null;
    }

}
