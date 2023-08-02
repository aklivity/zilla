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

public class KafkaSchemaConfigHandler implements JsonbAdapter<KafkaSchemaConfig, JsonObject>
{
    private static final String CATALOG_STRATEGY = "strategy";
    private static final String CATALOG_VERSION = "version";
    private static final String CATALOG_ID = "id";

    @Override
    public JsonObject adaptToJson(
        KafkaSchemaConfig kafkaSchemaConfig)
    {
        JsonObjectBuilder schema = Json.createObjectBuilder();

        if (kafkaSchemaConfig.strategy != null &&
                !kafkaSchemaConfig.strategy.isEmpty())
        {
            schema.add(CATALOG_STRATEGY, kafkaSchemaConfig.strategy);
        }

        if (kafkaSchemaConfig.version != null &&
                !kafkaSchemaConfig.version.isEmpty())
        {
            schema.add(CATALOG_VERSION, kafkaSchemaConfig.version);
        }

        if (kafkaSchemaConfig.id != null &&
                !kafkaSchemaConfig.id.isEmpty())
        {
            schema.add(CATALOG_ID, kafkaSchemaConfig.id);
        }

        return schema.build();
    }

    @Override
    public KafkaSchemaConfig adaptFromJson(
        JsonObject object)
    {
        String strategy = object.containsKey(CATALOG_STRATEGY)
                ? object.getString(CATALOG_STRATEGY)
                : null;

        String version = object.containsKey(CATALOG_VERSION)
                ? object.getString(CATALOG_VERSION)
                : null;

        String id = object.containsKey(CATALOG_ID)
                ? object.getString(CATALOG_ID)
                : null;

        return new KafkaSchemaConfig(strategy, version, id);
    }
}
