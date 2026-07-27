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

import io.aklivity.zilla.config.engine.GenericVaultConfig;
import io.aklivity.zilla.config.engine.GenericVaultConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.VaultInfo;

public class VaultConfigAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String OPTIONS_NAME = "options";

    private final String type;
    private final JsonbAdapter<OptionsConfig, JsonObject> options;

    public VaultConfigAdapter(
        VaultInfo info)
    {
        this.type = info.type();
        this.options = info.options();
    }

    public JsonObject adaptToJson(
        VaultConfig vault) throws Exception
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TYPE_NAME, vault.type);

        if (vault.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(vault.options));
        }

        return object.build();
    }

    public VaultConfig adaptFromJson(
        String namespace,
        String name,
        JsonObject object) throws Exception
    {
        GenericVaultConfigBuilder<GenericVaultConfig> vault = GenericVaultConfig.builder()
            .namespace(namespace)
            .name(name)
            .type(type);

        if (object.containsKey(OPTIONS_NAME))
        {
            vault.options(options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)));
        }

        return vault.build();
    }
}
