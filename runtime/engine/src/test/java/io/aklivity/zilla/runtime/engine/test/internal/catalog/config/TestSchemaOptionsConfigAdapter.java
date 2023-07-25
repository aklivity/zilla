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
package io.aklivity.zilla.runtime.engine.test.internal.catalog.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class TestSchemaOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    private static final String HOST = "schema.com";
    private static final String PORT = "8081";
    private static final String CONTEXT = "default";

    @Override
    public Kind kind()
    {
        return Kind.SCHEMA;
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
        TestSchemaOptionsConfig testOptions = (TestSchemaOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(HOST, testOptions.host);

        object.add(PORT, testOptions.port);

        object.add(CONTEXT, testOptions.context);

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        String newHost = object.containsKey(HOST)
                ? object.getString(HOST)
                : null;

        int newPort = object.containsKey(PORT)
                ? object.getInt(PORT)
                : null;

        String newContext = object.containsKey(CONTEXT)
                ? object.getString(CONTEXT)
                : null;

        return new TestSchemaOptionsConfig(newHost, newPort, newContext);
    }
}
