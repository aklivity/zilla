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

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.binding.a2a.A2aClientPushConfig;
import io.aklivity.zilla.config.binding.a2a.A2aClientPushConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class A2aClientPushConfigAdapter extends ConfigAdapter<A2aClientPushConfig, JsonObject>
{
    static final String ALLOW_NAME = "allow";
    static final String MAX_LENGTH_NAME = "maxLength";

    @Override
    public JsonObject adaptToJson(
        A2aClientPushConfig push)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (push.allow != null)
        {
            JsonArrayBuilder allow = Json.createArrayBuilder();
            push.allow.forEach(allow::add);
            object.add(ALLOW_NAME, allow);
        }

        if (push.maxLength > 0)
        {
            object.add(MAX_LENGTH_NAME, push.maxLength);
        }

        return object.build();
    }

    @Override
    public A2aClientPushConfig adaptFromJson(
        JsonObject object)
    {
        A2aClientPushConfigBuilder<A2aClientPushConfig> builder = A2aClientPushConfig.builder();

        if (object.containsKey(ALLOW_NAME))
        {
            List<String> allow = object.getJsonArray(ALLOW_NAME).getValuesAs(JsonString.class).stream()
                .map(JsonString::getString)
                .toList();
            builder.allow(allow);
        }

        if (object.containsKey(MAX_LENGTH_NAME))
        {
            builder.maxLength(object.getInt(MAX_LENGTH_NAME));
        }

        return builder.build();
    }
}
