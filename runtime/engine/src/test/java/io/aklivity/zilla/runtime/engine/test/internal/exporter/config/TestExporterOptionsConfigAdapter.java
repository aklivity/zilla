/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.exporter.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class TestExporterOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    private static final String MODE_NAME = "mode";

    @Override
    public Kind kind()
    {
        return Kind.EXPORTER;
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
        TestExporterOptionsConfig testOptions = (TestExporterOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(MODE_NAME, testOptions.mode);

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        String mode = object != null && object.containsKey(MODE_NAME)
                ? object.getString(MODE_NAME)
                : null;
        return new TestExporterOptionsConfig(mode);
    }
}
