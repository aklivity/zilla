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
package io.aklivity.zilla.config.engine.test.internal.vault.config;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class TestVaultOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String KEY_NAME = "key";
    private static final String SIGNER_NAME = "signer";
    private static final String TRUST_NAME = "trust";
    private static final String WRAP_NAME = "wrap";

    private final TestVaultEntryConfigAdapter entry = new TestVaultEntryConfigAdapter();
    private final TestVaultWrapConfigAdapter wrap = new TestVaultWrapConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        OptionsConfig adaptable)
    {
        TestVaultOptionsConfig options = (TestVaultOptionsConfig) adaptable;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (options.keys != null)
        {
            if (options.keys.size() == 1)
            {
                object.add(KEY_NAME, entry.adaptToJson(options.keys.get(0)));
            }
            else
            {
                JsonArrayBuilder keyArray = Json.createArrayBuilder();
                options.keys.forEach(k -> keyArray.add(entry.adaptToJson(k)));
                object.add(KEY_NAME, keyArray);
            }
        }

        if (options.signer != null)
        {
            object.add(SIGNER_NAME, entry.adaptToJson(options.signer));
        }

        if (options.trust != null)
        {
            if (options.trust.size() == 1)
            {
                object.add(TRUST_NAME, entry.adaptToJson(options.trust.get(0)));
            }
            else
            {
                JsonArrayBuilder trustArray = Json.createArrayBuilder();
                options.trust.forEach(t -> trustArray.add(entry.adaptToJson(t)));
                object.add(TRUST_NAME, trustArray);
            }
        }

        if (options.wrap != null)
        {
            if (options.wrap.size() == 1)
            {
                object.add(WRAP_NAME, wrap.adaptToJson(options.wrap.get(0)));
            }
            else
            {
                JsonArrayBuilder wrapArray = Json.createArrayBuilder();
                options.wrap.forEach(w -> wrapArray.add(wrap.adaptToJson(w)));
                object.add(WRAP_NAME, wrapArray);
            }
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
                JsonValue keyValue = object.get(KEY_NAME);
                if (keyValue.getValueType() == JsonValue.ValueType.ARRAY)
                {
                    JsonArray keyArray = keyValue.asJsonArray();
                    for (JsonValue value : keyArray)
                    {
                        TestVaultEntryConfig config = entry.adaptFromJson(value.asJsonObject());
                        options.key(config.alias, config.entry);
                    }
                }
                else
                {
                    TestVaultEntryConfig config = entry.adaptFromJson(keyValue.asJsonObject());
                    options.key(config.alias, config.entry);
                }
            }

            if (object.containsKey(SIGNER_NAME))
            {
                JsonObject signer = object.getJsonObject(SIGNER_NAME);
                TestVaultEntryConfig config = entry.adaptFromJson(signer);
                options.signer(config.alias, config.entry);
            }

            if (object.containsKey(TRUST_NAME))
            {
                JsonValue trustValue = object.get(TRUST_NAME);
                if (trustValue.getValueType() == JsonValue.ValueType.ARRAY)
                {
                    JsonArray trustArray = trustValue.asJsonArray();
                    for (JsonValue value : trustArray)
                    {
                        TestVaultEntryConfig config = entry.adaptFromJson(value.asJsonObject());
                        options.trust(config.alias, config.entry);
                    }
                }
                else
                {
                    TestVaultEntryConfig config = entry.adaptFromJson(trustValue.asJsonObject());
                    options.trust(config.alias, config.entry);
                }
            }

            if (object.containsKey(WRAP_NAME))
            {
                JsonValue wrapValue = object.get(WRAP_NAME);
                if (wrapValue.getValueType() == JsonValue.ValueType.ARRAY)
                {
                    JsonArray wrapArray = wrapValue.asJsonArray();
                    for (JsonValue value : wrapArray)
                    {
                        TestVaultWrapConfig config = wrap.adaptFromJson(value.asJsonObject());
                        options.wrap(config.alias, config.secret);
                    }
                }
                else
                {
                    TestVaultWrapConfig config = wrap.adaptFromJson(wrapValue.asJsonObject());
                    options.wrap(config.alias, config.secret);
                }
            }
        }

        return options.build();
    }
}
