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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class TestVaultWrapConfigAdapter extends ConfigAdapter<TestVaultWrapConfig, JsonObject>
{
    private static final String ALIAS_NAME = "alias";
    private static final String SECRET_NAME = "secret";

    @Override
    public JsonObject adaptToJson(
        TestVaultWrapConfig config)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        object.add(ALIAS_NAME, config.alias);
        object.add(SECRET_NAME, config.secret);
        return object.build();
    }

    @Override
    public TestVaultWrapConfig adaptFromJson(
        JsonObject object)
    {
        TestVaultWrapConfig config = null;

        if (object != null)
        {
            String alias = object.getString(ALIAS_NAME);
            String secret = object.getString(SECRET_NAME);

            config = new TestVaultWrapConfig(alias, secret);
        }

        return config;
    }
}
