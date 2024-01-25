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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.config.HttpParamConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;

public class HttpRequestConfigAdapter implements JsonbAdapter<HttpRequestConfig, JsonObject>
{
    private static final String PATH_NAME = "path";
    private static final String METHOD_NAME = "method";
    private static final String CONTENT_TYPE_NAME = "content-type";
    private static final String HEADERS_NAME = "headers";
    private static final String PARAMS_NAME = "params";
    private static final String PATH_PARAMS_NAME = "path";
    private static final String QUERY_PARAMS_NAME = "query";
    private static final String CONTENT_NAME = "content";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

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
        if (request.headers != null)
        {
            JsonObjectBuilder headers = Json.createObjectBuilder();
            for (HttpParamConfig header : request.headers)
            {
                model.adaptType(header.model.model);
                headers.add(header.name, model.adaptToJson(header.model));
            }
            object.add(HEADERS_NAME, headers);
        }
        if (request.pathParams != null || request.queryParams != null)
        {
            JsonObjectBuilder params = Json.createObjectBuilder();
            if (request.pathParams != null)
            {
                JsonObjectBuilder pathParams = Json.createObjectBuilder();
                for (HttpParamConfig pathParam : request.pathParams)
                {
                    model.adaptType(pathParam.model.model);
                    pathParams.add(pathParam.name, model.adaptToJson(pathParam.model));
                }
                params.add(PATH_PARAMS_NAME, pathParams);
            }
            if (request.queryParams != null)
            {
                JsonObjectBuilder queryParams = Json.createObjectBuilder();
                for (HttpParamConfig queryParam : request.queryParams)
                {
                    model.adaptType(queryParam.model.model);
                    queryParams.add(queryParam.name, model.adaptToJson(queryParam.model));
                }
                params.add(QUERY_PARAMS_NAME, queryParams);
            }
            object.add(PARAMS_NAME, params);
        }
        if (request.content != null)
        {
            model.adaptType(request.content.model);
            JsonValue content = model.adaptToJson(request.content);
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
        ModelConfig content = null;
        if (object.containsKey(CONTENT_NAME))
        {
            JsonValue contentJson = object.get(CONTENT_NAME);
            content = model.adaptFromJson(contentJson);
        }
        List<HttpParamConfig> headers = null;
        if (object.containsKey(HEADERS_NAME))
        {
            JsonObject headersJson = object.getJsonObject(HEADERS_NAME);
            headers = new LinkedList<>();
            for (Map.Entry<String, JsonValue> entry : headersJson.entrySet())
            {
                HttpParamConfig header = HttpParamConfig.builder()
                    .name(entry.getKey())
                    .model(model.adaptFromJson(entry.getValue()))
                    .build();
                headers.add(header);
            }
        }
        List<HttpParamConfig> pathParams = null;
        List<HttpParamConfig> queryParams = null;
        if (object.containsKey(PARAMS_NAME))
        {
            JsonObject paramsJson = object.getJsonObject(PARAMS_NAME);
            if (paramsJson.containsKey(PATH_PARAMS_NAME))
            {
                pathParams = new LinkedList<>();
                JsonObject pathParamsJson = paramsJson.getJsonObject(PATH_PARAMS_NAME);
                for (Map.Entry<String, JsonValue> entry : pathParamsJson.entrySet())
                {
                    HttpParamConfig pathParam = HttpParamConfig.builder()
                        .name(entry.getKey())
                        .model(model.adaptFromJson(entry.getValue()))
                        .build();
                    pathParams.add(pathParam);
                }
            }
            if (paramsJson.containsKey(QUERY_PARAMS_NAME))
            {
                queryParams = new LinkedList<>();
                JsonObject queryParamsJson = paramsJson.getJsonObject(QUERY_PARAMS_NAME);
                for (Map.Entry<String, JsonValue> entry : queryParamsJson.entrySet())
                {
                    HttpParamConfig queryParam = HttpParamConfig.builder()
                        .name(entry.getKey())
                        .model(model.adaptFromJson(entry.getValue()))
                        .build();
                    queryParams.add(queryParam);
                }
            }
        }
        return new HttpRequestConfig(path, method, contentType, headers, pathParams, queryParams, content);
    }
}
