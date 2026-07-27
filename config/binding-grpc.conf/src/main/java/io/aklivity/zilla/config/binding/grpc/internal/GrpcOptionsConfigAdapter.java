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
package io.aklivity.zilla.config.binding.grpc.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class GrpcOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        return Json.createObjectBuilder().build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        return new OptionsConfig();
    }
}
