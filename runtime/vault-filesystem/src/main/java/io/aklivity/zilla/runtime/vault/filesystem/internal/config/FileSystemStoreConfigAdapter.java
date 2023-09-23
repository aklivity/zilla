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
package io.aklivity.zilla.runtime.vault.filesystem.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemStoreConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemStoreConfigBuilder;

public final class FileSystemStoreConfigAdapter implements JsonbAdapter<FileSystemStoreConfig, JsonObject>
{
    private static final String STORE_NAME = "store";
    private static final String TYPE_NAME = "type";
    private static final String PASSWORD_NAME = "password";

    @Override
    public JsonObject adaptToJson(
        FileSystemStoreConfig store)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(STORE_NAME, store.store);

        if (store.type != null)
        {
            object.add(TYPE_NAME, store.type);
        }

        if (store.password != null)
        {
            object.add(PASSWORD_NAME, store.password);
        }

        return object.build();
    }

    @Override
    public FileSystemStoreConfig adaptFromJson(
        JsonObject object)
    {
        FileSystemStoreConfigBuilder<FileSystemStoreConfig> fsStore = FileSystemStoreConfig.builder()
            .store(object.getString(STORE_NAME));

        if (object.containsKey(TYPE_NAME))
        {
            fsStore.type(object.getString(TYPE_NAME));
        }

        if (object.containsKey(PASSWORD_NAME))
        {
            fsStore.password(object.getString(PASSWORD_NAME));
        }

        return fsStore.build();
    }
}
