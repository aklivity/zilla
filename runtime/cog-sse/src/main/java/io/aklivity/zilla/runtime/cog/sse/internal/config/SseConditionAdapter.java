/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.sse.internal.config;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.cog.sse.internal.SseCog;
import io.aklivity.zilla.runtime.engine.config.Condition;
import io.aklivity.zilla.runtime.engine.config.ConditionAdapterSpi;

public final class SseConditionAdapter implements ConditionAdapterSpi, JsonbAdapter<Condition, JsonObject>
{
    private static final String PATH_NAME = "path";

    @Override
    public String type()
    {
        return SseCog.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        Condition condition)
    {
        SseCondition sseCondition = (SseCondition) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (sseCondition.path != null)
        {
            object.add(PATH_NAME, sseCondition.path);
        }

        return object.build();
    }

    @Override
    public Condition adaptFromJson(
        JsonObject object)
    {
        String path = object.containsKey(PATH_NAME)
                ? object.getString(PATH_NAME)
                : null;

        return new SseCondition(path);
    }
}
