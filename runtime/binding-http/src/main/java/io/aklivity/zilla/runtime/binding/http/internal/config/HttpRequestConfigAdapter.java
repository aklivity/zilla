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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfigAdapter;

public class HttpRequestConfigAdapter implements JsonbAdapter<HttpRequestConfig, JsonObject>
{
    private static final String PATH_NAME = "path";
    private static final String METHOD_NAME = "method";
    private static final String CONTENT_TYPE_NAME = "content-type";
    private static final String CONTENT_NAME = "content";

    private final ValidatorConfigAdapter validator  = new ValidatorConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        HttpRequestConfig request)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (request.path != null)
        {
            object.add(PATH_NAME, request.path);
        }
        if (request.method != null)
        {
            object.add(METHOD_NAME, request.method.toString());
        }
        if (request.contentType != null)
        {
            JsonArrayBuilder contentType = Json.createArrayBuilder();
            request.contentType.forEach(contentType::add);
            object.add(CONTENT_TYPE_NAME, contentType);
        }
        if (request.content != null)
        {
            validator.adaptType(request.content.type);
            JsonValue content = validator.adaptToJson(request.content);
            object.add(CONTENT_NAME, content);
        }
        return object.build();
    }

    @Override
    public HttpRequestConfig adaptFromJson(
        JsonObject object)
    {
        String path = null;
        if (object.containsKey(PATH_NAME))
        {
            path = object.getString(PATH_NAME);
        }
        HttpRequestConfig.Method method = null;
        if (object.containsKey(METHOD_NAME))
        {
            method = HttpRequestConfig.Method.valueOf(object.getString(METHOD_NAME));
        }
        List<String> contentType = null;
        if (object.containsKey(CONTENT_TYPE_NAME))
        {
            contentType = object.getJsonArray(CONTENT_TYPE_NAME).stream()
                .map(i -> ((JsonString) i).getString())
                .collect(Collectors.toList());
        }
        ValidatorConfig content = null;
        if (object.containsKey(CONTENT_NAME))
        {
            JsonValue contentJson = object.get(CONTENT_NAME);
            content = validator.adaptFromJson(contentJson);
        }
        return new HttpRequestConfig(path, method, contentType, content);
    }
}
