/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.store.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class TestStoreOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    private static final String ENTRIES_NAME = "entries";

    @Override
    public Kind kind()
    {
        return Kind.STORE;
    }

    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        TestStoreOptionsConfig testOptions = (TestStoreOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (testOptions.entries != null &&
            !testOptions.entries.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            testOptions.entries.forEach(entries::add);
            object.add(ENTRIES_NAME, entries);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        TestStoreOptionsConfigBuilder<TestStoreOptionsConfig> testOptions = TestStoreOptionsConfig.builder();

        if (object != null && object.containsKey(ENTRIES_NAME))
        {
            object.getJsonObject(ENTRIES_NAME)
                .forEach((key, value) -> testOptions.entry(key, ((JsonString) value).getString()));
        }

        return testOptions.build();
    }
}
