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
package io.aklivity.zilla.config.exporter.stdout.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.exporter.stdout.StdoutOptionsConfig;

public class StdoutOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        return StdoutOptionsConfig.builder().build();
    }
}
