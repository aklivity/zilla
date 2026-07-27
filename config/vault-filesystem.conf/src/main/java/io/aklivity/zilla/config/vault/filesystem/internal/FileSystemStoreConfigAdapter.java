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
package io.aklivity.zilla.config.vault.filesystem.internal;

import static java.util.stream.Collectors.toList;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.vault.filesystem.FileSystemStoreConfig;
import io.aklivity.zilla.config.vault.filesystem.FileSystemStoreConfigBuilder;

public final class FileSystemStoreConfigAdapter implements JsonbAdapter<FileSystemStoreConfig, JsonObject>
{
    private static final String STORE_NAME = "store";
    private static final String TYPE_NAME = "type";
    private static final String PASSWORD_NAME = "password";
    private static final String ENTRIES_NAME = "entries";

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

        if (store.entries != null)
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            store.entries.forEach(entries::add);
            object.add(ENTRIES_NAME, entries);
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

        if (object.containsKey(ENTRIES_NAME))
        {
            fsStore.entries(asListString(object.getJsonArray(ENTRIES_NAME)));
        }

        return fsStore.build();
    }

    private static List<String> asListString(
        JsonArray array)
    {
        return array.stream()
            .map(FileSystemStoreConfigAdapter::asString)
            .collect(toList());
    }

    private static String asString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
