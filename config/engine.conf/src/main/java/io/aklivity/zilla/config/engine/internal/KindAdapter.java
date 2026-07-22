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
package io.aklivity.zilla.config.engine.internal;

import jakarta.json.Json;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.KindConfig;

public class KindAdapter implements JsonbAdapter<KindConfig, JsonString>
{
    @Override
    public JsonString adaptToJson(
        KindConfig role)
    {
        return Json.createValue(role.name().toLowerCase());
    }

    @Override
    public KindConfig adaptFromJson(
        JsonString object)
    {
        return KindConfig.valueOf(object.getString().toUpperCase());
    }
}
