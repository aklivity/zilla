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
package io.aklivity.zilla.runtime.binding.sse.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.sse.config.SsePathConfig;
import io.aklivity.zilla.runtime.binding.sse.config.SsePathConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;
public class SsePathConfigAdapter implements JsonbAdapter<SsePathConfig, JsonObject>
{
    private static final String PATH_NAME = "path";
    private static final String CONTENT_NAME = "content";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    public JsonObject adaptToJson(
        SsePathConfig path) // TODO: rename to something better?
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (path.path != null)
        {
            object.add(PATH_NAME, path.path);
        }
        if (path.content != null)
        {
            model.adaptType(path.content.model);
            JsonValue content = model.adaptToJson(path.content);
            object.add(CONTENT_NAME, content);
        }
        return object.build();
    }

    @Override
    public SsePathConfig adaptFromJson(
        JsonObject object)
    {
        SsePathConfigBuilder<SsePathConfig> builder = SsePathConfig.builder();
        if (object.containsKey(PATH_NAME))
        {
            builder.path(object.getString(PATH_NAME));
        }
        if (object.containsKey(CONTENT_NAME))
        {
            JsonValue contentJson = object.get(CONTENT_NAME);
            builder.content(model.adaptFromJson(contentJson));
        }
        return builder.build();
    }
}
