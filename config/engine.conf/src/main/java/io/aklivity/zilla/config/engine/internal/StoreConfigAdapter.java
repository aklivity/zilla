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
package io.aklivity.zilla.config.engine.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.GenericStoreConfig;
import io.aklivity.zilla.config.engine.GenericStoreConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.config.engine.StoreInfo;

public class StoreConfigAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String OPTIONS_NAME = "options";

    private final String type;
    private final JsonbAdapter<OptionsConfig, JsonObject> options;

    public StoreConfigAdapter(
        StoreInfo info)
    {
        this.type = info.type();
        this.options = info.options();
    }

    public JsonObject adaptToJson(
        StoreConfig store) throws Exception
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TYPE_NAME, store.type);

        if (store.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(store.options));
        }

        return object.build();
    }

    public StoreConfig adaptFromJson(
        String namespace,
        String name,
        JsonObject object) throws Exception
    {
        GenericStoreConfigBuilder<GenericStoreConfig> store = GenericStoreConfig.builder()
            .namespace(namespace)
            .name(name)
            .type(type);

        if (object.containsKey(OPTIONS_NAME))
        {
            store.options(options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)));
        }

        return store.build();
    }
}
