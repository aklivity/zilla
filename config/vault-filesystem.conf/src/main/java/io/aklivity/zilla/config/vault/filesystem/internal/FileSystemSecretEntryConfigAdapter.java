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
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.vault.filesystem.FileSystemSecretEntryConfig;
import io.aklivity.zilla.config.vault.filesystem.FileSystemSecretEntryConfigBuilder;

public final class FileSystemSecretEntryConfigAdapter extends ConfigAdapter<FileSystemSecretEntryConfig, JsonValue>
{
    private static final String DEFAULT_VERSION = "1";
    private static final String ACTIVE_NAME = "active";
    private static final String VERSIONS_NAME = "versions";
    private static final String ALGORITHM_NAME = "algorithm";

    @Override
    public JsonValue adaptToJson(
        FileSystemSecretEntryConfig entry)
    {
        JsonValue value;

        if (entry.algorithm == null &&
            DEFAULT_VERSION.equals(entry.active) &&
            entry.versions.size() == 1 &&
            entry.versions.containsKey(DEFAULT_VERSION))
        {
            value = Json.createValue(entry.versions.get(DEFAULT_VERSION));
        }
        else
        {
            JsonObjectBuilder object = Json.createObjectBuilder();
            object.add(ACTIVE_NAME, entry.active);

            JsonObjectBuilder versions = Json.createObjectBuilder();
            entry.versions.forEach(versions::add);
            object.add(VERSIONS_NAME, versions);

            if (entry.algorithm != null)
            {
                object.add(ALGORITHM_NAME, entry.algorithm);
            }

            value = object.build();
        }

        return value;
    }

    @Override
    public FileSystemSecretEntryConfig adaptFromJson(
        JsonValue value)
    {
        FileSystemSecretEntryConfigBuilder<FileSystemSecretEntryConfig> entry = FileSystemSecretEntryConfig.builder();

        if (value.getValueType() == JsonValue.ValueType.STRING)
        {
            entry.alias(((JsonString) value).getString());
        }
        else
        {
            JsonObject object = value.asJsonObject();
            entry.active(object.getString(ACTIVE_NAME));

            JsonObject versions = object.getJsonObject(VERSIONS_NAME);
            versions.forEach((version, alias) -> entry.version(version, ((JsonString) alias).getString()));

            if (object.containsKey(ALGORITHM_NAME))
            {
                entry.algorithm(object.getString(ALGORITHM_NAME));
            }
        }

        return entry.build();
    }
}
