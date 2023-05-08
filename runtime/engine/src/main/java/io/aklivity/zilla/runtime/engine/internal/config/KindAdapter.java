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
package io.aklivity.zilla.runtime.engine.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public class KindAdapter implements JsonbAdapter<KindConfig, JsonString>
{
    private final ConfigAdapterContext context;

    public KindAdapter(
        ConfigAdapterContext context)
    {
        this.context = context;
    }

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
