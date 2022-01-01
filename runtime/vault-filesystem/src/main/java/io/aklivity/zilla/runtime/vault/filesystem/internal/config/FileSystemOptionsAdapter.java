/*
 * Copyright 2021-2021 Aklivity Inc.
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

import io.aklivity.zilla.runtime.engine.config.Options;
import io.aklivity.zilla.runtime.engine.config.OptionsAdapterSpi;
import io.aklivity.zilla.runtime.vault.filesystem.internal.FileSystemCog;

public final class FileSystemOptionsAdapter implements OptionsAdapterSpi, JsonbAdapter<Options, JsonObject>
{
    private static final String KEYS_NAME = "keys";
    private static final String TRUST_NAME = "trust";
    private static final String SIGNERS_NAME = "signers";

    private final FileSystemStoreAdapter store = new FileSystemStoreAdapter();

    @Override
    public String type()
    {
        return FileSystemCog.NAME;
    }

    @Override
    public Kind kind()
    {
        return OptionsAdapterSpi.Kind.VAULT;
    }

    @Override
    public JsonObject adaptToJson(
        Options options)
    {
        FileSystemOptions fsOptions = (FileSystemOptions) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (fsOptions.keys != null)
        {
            object.add(KEYS_NAME, store.adaptToJson(fsOptions.keys));
        }

        if (fsOptions.trust != null)
        {
            object.add(TRUST_NAME, store.adaptToJson(fsOptions.trust));
        }

        return object.build();
    }

    @Override
    public Options adaptFromJson(
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

        return new FileSystemOptions(keys, trust, signers);
    }
}
