/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.StoreConfig;
import io.aklivity.zilla.runtime.engine.config.StoreConfigBuilder;

public class StoreAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String VAULT_NAME = "vault";
    private static final String OPTIONS_NAME = "options";

    private final OptionsConfigAdapter options;

    private String namespace;

    public StoreAdapter(
        ConfigAdapterContext context)
    {
        this.options = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.STORE, context);
    }

    public void adaptNamespace(
        String namespace)
    {
        this.namespace = namespace;
    }

    public JsonObject adaptToJson(
        StoreConfig store)
    {
        options.adaptType(store.type);

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TYPE_NAME, store.type);

        if (store.vault != null)
        {
            object.add(VAULT_NAME, store.vault);
        }

        if (store.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(store.options));
        }

        return object.build();
    }

    public StoreConfig adaptFromJson(
        String name,
        JsonObject object)
    {
        String type = object.getString(TYPE_NAME);

        options.adaptType(type);

        StoreConfigBuilder<StoreConfig> store = StoreConfig.builder()
            .namespace(namespace)
            .name(name)
            .type(type);

        if (object.containsKey(VAULT_NAME))
        {
            store.vault(object.getString(VAULT_NAME));
        }

        if (object.containsKey(OPTIONS_NAME))
        {
            store.options(options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)));
        }

        return store.build();
    }
}
