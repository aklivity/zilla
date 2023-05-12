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

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.vault.filesystem.internal.FileSystemVault;

public final class FileSystemOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String KEYS_NAME = "keys";
    private static final String TRUST_NAME = "trust";
    private static final String SIGNERS_NAME = "signers";

    private final FileSystemStoreAdapter store = new FileSystemStoreAdapter();

    @Override
    public String type()
    {
        return FileSystemVault.NAME;
    }

    @Override
    public Kind kind()
    {
        return Kind.VAULT;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        FileSystemOptionsConfig fsOptions = (FileSystemOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (fsOptions.keys != null)
        {
            object.add(KEYS_NAME, store.adaptToJson(fsOptions.keys));
        }

        if (fsOptions.trust != null)
        {
            object.add(TRUST_NAME, store.adaptToJson(fsOptions.trust));
        }

        if (fsOptions.signers != null)
        {
            object.add(SIGNERS_NAME, store.adaptToJson(fsOptions.signers));
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        FileSystemStore keys = object.containsKey(KEYS_NAME)
                ? store.adaptFromJson(object.getJsonObject(KEYS_NAME))
                : null;
        FileSystemStore trust = object.containsKey(TRUST_NAME)
                ? store.adaptFromJson(object.getJsonObject(TRUST_NAME))
                : null;
        FileSystemStore signers = object.containsKey(SIGNERS_NAME)
                ? store.adaptFromJson(object.getJsonObject(SIGNERS_NAME))
                : null;

        return new FileSystemOptionsConfig(keys, trust, signers);
    }
}
