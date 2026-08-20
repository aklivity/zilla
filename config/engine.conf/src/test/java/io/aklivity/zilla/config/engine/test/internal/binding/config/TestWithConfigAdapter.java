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
package io.aklivity.zilla.config.engine.test.internal.binding.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.WithConfig;

public final class TestWithConfigAdapter extends ConfigAdapter<WithConfig, JsonObject>
{
    private static final String NAME_NAME = "name";

    @Override
    public JsonObject adaptToJson(
        WithConfig condition)
    {
        TestWithConfig testWith = (TestWithConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(NAME_NAME, testWith.name);

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        String name = object.getString(NAME_NAME);

        return new TestWithConfig(name);
    }
}
