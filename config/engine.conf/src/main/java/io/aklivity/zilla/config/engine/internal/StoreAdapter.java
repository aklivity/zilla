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

import io.aklivity.zilla.config.engine.OptionsConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfigAdapterSpi;
import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.config.engine.StoreConfigBuilder;

public class StoreAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String OPTIONS_NAME = "options";

    private final OptionsConfigAdapter options;

    private String namespace;

    public StoreAdapter()
    {
        this.options = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.STORE);
    }

    public void adaptNamespace(
        String namespace)
    {
        this.namespace = namespace;
    }

    public JsonObject adaptToJson(
        StoreConfig store) throws Exception
    {
        options.adaptType(store.type);

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TYPE_NAME, store.type);

        if (store.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(store.options));
        }

        return object.build();
    }

    public StoreConfig adaptFromJson(
        String name,
        JsonObject object) throws Exception
    {
        String type = object.getString(TYPE_NAME);

        options.adaptType(type);

        StoreConfigBuilder<StoreConfig> store = StoreConfig.builder()
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
