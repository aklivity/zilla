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
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.a2a.A2aServerPushConfig;
import io.aklivity.zilla.config.binding.a2a.A2aServerPushConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class A2aServerPushConfigAdapter extends ConfigAdapter<A2aServerPushConfig, JsonObject>
{
    private static final String URL_NAME = "url";
    private static final String PATH_NAME = "path";

    @Override
    public JsonObject adaptToJson(
        A2aServerPushConfig push)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (push.url != null)
        {
            object.add(URL_NAME, push.url);
        }

        if (push.path != null)
        {
            object.add(PATH_NAME, push.path);
        }

        return object.build();
    }

    @Override
    public A2aServerPushConfig adaptFromJson(
        JsonObject object)
    {
        A2aServerPushConfigBuilder<A2aServerPushConfig> builder = A2aServerPushConfig.builder();

        if (object.containsKey(URL_NAME))
        {
            builder.url(object.getString(URL_NAME));
        }

        if (object.containsKey(PATH_NAME))
        {
            builder.path(object.getString(PATH_NAME));
        }

        return builder.build();
    }
}
