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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpBodyConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpBinding;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class McpHttpWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String HEADERS_NAME = "headers";
    private static final String COOKIES_NAME = "cookies";
    private static final String QUERY_NAME = "query";
    private static final String BODY_NAME = "body";
    private static final String TEMPLATE_NAME = "template";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public String type()
    {
        return McpHttpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        McpHttpWithConfig mcpHttpWith = (McpHttpWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (mcpHttpWith.headers != null)
        {
            JsonObjectBuilder headers = Json.createObjectBuilder();
            mcpHttpWith.headers.forEach(headers::add);
            object.add(HEADERS_NAME, headers);
        }

        if (mcpHttpWith.cookies != null && !mcpHttpWith.cookies.isEmpty())
        {
            JsonObjectBuilder cookies = Json.createObjectBuilder();
            mcpHttpWith.cookies.forEach(cookies::add);
            object.add(COOKIES_NAME, cookies);
        }

        if (mcpHttpWith.query != null)
        {
            object.add(QUERY_NAME, model.adaptToJson(mcpHttpWith.query));
        }

        McpHttpBodyConfig body = mcpHttpWith.body;
        if (body != null && body.template != null)
        {
            JsonObjectBuilder template = Json.createObjectBuilder();
            body.template.forEach(template::add);
            object.add(BODY_NAME, Json.createObjectBuilder().add(TEMPLATE_NAME, template));
        }
        else if (body != null && body.model != null)
        {
            object.add(BODY_NAME, model.adaptToJson(body.model));
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        Map<String, String> headers = null;
        if (object.containsKey(HEADERS_NAME))
        {
            JsonObject headersObject = object.getJsonObject(HEADERS_NAME);
            headers = new LinkedHashMap<>();
            for (String name : headersObject.keySet())
            {
                headers.put(name, headersObject.getString(name));
            }
        }

        Map<String, String> cookies = null;
        if (object.containsKey(COOKIES_NAME))
        {
            JsonObject cookiesObject = object.getJsonObject(COOKIES_NAME);
            cookies = new LinkedHashMap<>();
            for (String name : cookiesObject.keySet())
            {
                cookies.put(name, cookiesObject.getString(name));
            }
        }

        ModelConfig query = object.containsKey(QUERY_NAME)
            ? model.adaptFromJson(object.get(QUERY_NAME))
            : null;

        McpHttpBodyConfig body = null;
        if (object.containsKey(BODY_NAME))
        {
            JsonObject bodyObject = object.getJsonObject(BODY_NAME);
            if (bodyObject.containsKey(TEMPLATE_NAME))
            {
                JsonObject templateObject = bodyObject.getJsonObject(TEMPLATE_NAME);
                Map<String, String> template = new LinkedHashMap<>();
                for (String name : templateObject.keySet())
                {
                    template.put(name, templateObject.getString(name));
                }
                body = McpHttpBodyConfig.builder().template(template).build();
            }
            else
            {
                body = McpHttpBodyConfig.builder().model(model.adaptFromJson(object.get(BODY_NAME))).build();
            }
        }

        return McpHttpWithConfig.builder()
            .headers(headers)
            .cookies(cookies)
            .query(query)
            .body(body)
            .build();
    }
}
