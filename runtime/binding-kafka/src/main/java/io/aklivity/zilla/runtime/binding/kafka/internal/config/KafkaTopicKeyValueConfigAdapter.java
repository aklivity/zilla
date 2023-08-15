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

public class KafkaTopicKeyValueConfigAdapter implements JsonbAdapter<KafkaTopicKeyValueConfig, JsonObject>
{
    private static final String CATALOG_TYPE = "type";
    private static final String CATALOG_NAME = "catalog";
    private static final String ENCODING = "encoding";

    private final KafkaCatalogConfigAdapter catalog = new KafkaCatalogConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        KafkaTopicKeyValueConfig config)
    {
        JsonObjectBuilder catalog = Json.createObjectBuilder();

        if (config.type != null &&
            !config.type.isEmpty())
        {
            catalog.add(CATALOG_TYPE, config.type);
        }

        if (config.encoding != null &&
            !config.encoding.isEmpty())
        {
            catalog.add(ENCODING, config.encoding);
        }

        if (config.catalog != null &&
            !config.catalog.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            config.catalog.forEach(c -> entries.add(this.catalog.adaptToJson(c)));

            catalog.add(CATALOG_NAME, entries);
        }

        return catalog.build();
    }

    @Override
    public KafkaTopicKeyValueConfig adaptFromJson(
        JsonObject object)
    {
        String type = object.containsKey(CATALOG_TYPE)
                ? object.getString(CATALOG_TYPE)
                : null;

        String encoding = object.containsKey(ENCODING)
                ? object.getString(ENCODING)
                : null;

        JsonArray catalogArray = object.containsKey(CATALOG_NAME)
                ? object.getJsonArray(CATALOG_NAME)
                : null;

        List<KafkaCatalogConfig> catalogs = null;

        if (catalogArray != null)
        {
            List<KafkaCatalogConfig> catalog0 = new ArrayList<>();
            catalogArray.forEach(v -> catalog0.add(catalog.adaptFromJson(v.asJsonObject())));
            catalogs = catalog0;
        }

        return new KafkaTopicKeyValueConfig(type, encoding, catalogs);
    }
}
