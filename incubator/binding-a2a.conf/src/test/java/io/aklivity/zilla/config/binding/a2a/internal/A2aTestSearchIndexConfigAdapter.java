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
package io.aklivity.zilla.config.binding.a2a.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.a2a.A2aTestSearchIndexConfig;

public final class A2aTestSearchIndexConfigAdapter implements JsonbAdapter<A2aTestSearchIndexConfig, JsonObject>
{
    private static final String TYPE_NAME = "type";

    @Override
    public JsonObject adaptToJson(
        A2aTestSearchIndexConfig config)
    {
        return Json.createObjectBuilder()
            .add(TYPE_NAME, A2aTestSearchIndexConfig.NAME)
            .build();
    }

    @Override
    public A2aTestSearchIndexConfig adaptFromJson(
        JsonObject object)
    {
        return new A2aTestSearchIndexConfig();
    }
}
