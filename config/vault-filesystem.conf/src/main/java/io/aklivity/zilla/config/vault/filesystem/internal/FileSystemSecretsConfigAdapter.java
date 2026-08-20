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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.vault.filesystem.FileSystemSecretsConfig;
import io.aklivity.zilla.config.vault.filesystem.FileSystemSecretsConfigBuilder;

public final class FileSystemSecretsConfigAdapter extends ConfigAdapter<FileSystemSecretsConfig, JsonObject>
{
    private static final String STORE_NAME = "store";
    private static final String TYPE_NAME = "type";
    private static final String PASSWORD_NAME = "password";
    private static final String ENTRIES_NAME = "entries";

    private final FileSystemSecretEntryConfigAdapter entry = new FileSystemSecretEntryConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        FileSystemSecretsConfig secrets)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(STORE_NAME, secrets.store);

        if (secrets.type != null)
        {
            object.add(TYPE_NAME, secrets.type);
        }

        if (secrets.password != null)
        {
            object.add(PASSWORD_NAME, secrets.password);
        }

        if (secrets.entries != null)
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            secrets.entries.forEach((name, value) -> entries.add(name, entry.adaptToJson(value)));
            object.add(ENTRIES_NAME, entries);
        }

        return object.build();
    }

    @Override
    public FileSystemSecretsConfig adaptFromJson(
        JsonObject object)
    {
        FileSystemSecretsConfigBuilder<FileSystemSecretsConfig> secrets = FileSystemSecretsConfig.builder()
            .store(object.getString(STORE_NAME));

        if (object.containsKey(TYPE_NAME))
        {
            secrets.type(object.getString(TYPE_NAME));
        }

        if (object.containsKey(PASSWORD_NAME))
        {
            secrets.password(object.getString(PASSWORD_NAME));
        }

        if (object.containsKey(ENTRIES_NAME))
        {
            JsonObject entries = object.getJsonObject(ENTRIES_NAME);
            entries.forEach((name, value) -> secrets.entry(name, entry.adaptFromJson(value)));
        }

        return secrets.build();
    }
}
