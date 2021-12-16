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
package io.aklivity.zilla.runtime.cog.http.internal.config;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.cog.http.internal.HttpCog;
import io.aklivity.zilla.runtime.engine.config.Condition;
import io.aklivity.zilla.runtime.engine.config.ConditionAdapterSpi;

public final class HttpConditionAdapter implements ConditionAdapterSpi, JsonbAdapter<Condition, JsonObject>
{
    private static final String HEADERS_NAME = "headers";

    @Override
    public String type()
    {
        return HttpCog.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        Condition condition)
    {
        HttpCondition httpCondition = (HttpCondition) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (httpCondition.headers != null &&
            !httpCondition.headers.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            httpCondition.headers.forEach(entries::add);

            object.add(HEADERS_NAME, entries);
        }

        return object.build();
    }

    @Override
    public Condition adaptFromJson(
        JsonObject object)
    {
        JsonObject headers = object.containsKey(HEADERS_NAME)
                ? object.getJsonObject(HEADERS_NAME)
                : null;

        Map<String, String> newHeaders = null;

        if (headers != null)
        {
            Map<String, String> newHeaders0 = new LinkedHashMap<>();
            headers.forEach((k, v) -> newHeaders0.put(k, JsonString.class.cast(v).getString()));
            newHeaders = newHeaders0;
        }

        return new HttpCondition(newHeaders);
    }
}
