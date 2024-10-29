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

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class TestVaultOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    private static final String KEY_NAME = "key";
    private static final String SIGNER_NAME = "signer";
    private static final String TRUST_NAME = "trust";

    private final TestVaultEntryConfigAdapter entry = new TestVaultEntryConfigAdapter();

    @Override
    public Kind kind()
    {
        return Kind.VAULT;
    }

    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig adaptable)
    {
        TestVaultOptionsConfig options = (TestVaultOptionsConfig) adaptable;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (options.key != null)
        {
            object.add(KEY_NAME, entry.adaptToJson(options.key));
        }

        if (options.signer != null)
        {
            object.add(SIGNER_NAME, entry.adaptToJson(options.signer));
        }

        if (options.trust != null)
        {
            object.add(TRUST_NAME, entry.adaptToJson(options.trust));
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        TestVaultOptionsConfigBuilder<TestVaultOptionsConfig> options = TestVaultOptionsConfig.builder();

        if (object != null)
        {
            if (object.containsKey(KEY_NAME))
            {
                JsonObject key = object.getJsonObject(KEY_NAME);
                TestVaultEntryConfig config = entry.adaptFromJson(key);
                options.key(config.alias, config.entry);
            }

            if (object.containsKey(SIGNER_NAME))
            {
                JsonObject signer = object.getJsonObject(SIGNER_NAME);
                TestVaultEntryConfig config = entry.adaptFromJson(signer);
                options.signer(config.alias, config.entry);
            }

            if (object.containsKey(TRUST_NAME))
            {
                JsonObject trust = object.getJsonObject(TRUST_NAME);
                TestVaultEntryConfig config = entry.adaptFromJson(trust);
                options.trust(config.alias, config.entry);
            }
        }

        return options.build();
    }
}
