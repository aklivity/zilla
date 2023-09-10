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
package io.aklivity.zilla.runtime.binding.kafka.internal.validator.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaCatalogConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaCatalogConfigAdapter;

public final class AvroValidatorConfigAdapter implements ValidatorConfigAdapterSpi, JsonbAdapter<ValidatorConfig, JsonObject>
{
    private static final String CATALOG_NAME = "catalog";

    private final KafkaCatalogConfigAdapter catalog = new KafkaCatalogConfigAdapter();

    @Override
    public String type()
    {
        return "avro";
    }

    @Override
    public JsonObject adaptToJson(
        ValidatorConfig config)
    {
        AvroValidatorConfig validatorConfig = (AvroValidatorConfig) config;
        JsonObjectBuilder validator = Json.createObjectBuilder();

        if (validatorConfig.catalogs != null &&
                !validatorConfig.catalogs.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            validatorConfig.catalogs.forEach(c -> entries.add(this.catalog.adaptToJson(c)));

            validator.add(CATALOG_NAME, entries);
        }

        return validator.build();
    }

    @Override
    public ValidatorConfig adaptFromJson(
        JsonObject object)
    {
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
        return new AvroValidatorConfig(catalogs);
    }
}
