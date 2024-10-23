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
package io.aklivity.zilla.runtime.engine.test.internal.vault.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public final class TestVaultEntryConfigAdapter implements JsonbAdapter<TestVaultEntryConfig, JsonObject>
{
    private static final String ALIAS_NAME = "alias";
    private static final String ENTRY_NAME = "entry";

    @Override
    public JsonObject adaptToJson(
        TestVaultEntryConfig config)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        object.add(ALIAS_NAME, config.alias);
        object.add(ENTRY_NAME, config.entry);
        return object.build();
    }

    @Override
    public TestVaultEntryConfig adaptFromJson(
        JsonObject object)
    {
        TestVaultEntryConfig config = null;

        if (object != null)
        {
            String alias = object.getString(ALIAS_NAME);
            String entry = object.getString(ENTRY_NAME);

            config = new TestVaultEntryConfig(alias, entry);
        }

        return config;
    }
}
