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
package io.aklivity.zilla.runtime.engine.internal.config;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.Condition;
import io.aklivity.zilla.runtime.engine.config.Route;
import io.aklivity.zilla.runtime.engine.config.With;

public class RouteAdapter implements JsonbAdapter<Route, JsonObject>
{
    private static final String EXIT_NAME = "exit";
    private static final String WHEN_NAME = "when";
    private static final String WITH_NAME = "with";

    private static final List<Condition> WHEN_DEFAULT = emptyList();

    private int index;
    private final ConditionAdapter condition;
    private final WithAdapter with;

    public RouteAdapter()
    {
        condition = new ConditionAdapter();
        with = new WithAdapter();
    }

    public RouteAdapter adaptType(
        String type)
    {
        condition.adaptType(type);
        with.adaptType(type);
        return this;
    }

    public void adaptFromJsonIndex(
        int index)
    {
        this.index = index;
    }

    @Override
    public JsonObject adaptToJson(
        Route route)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(EXIT_NAME, route.exit);

        if (!WHEN_DEFAULT.equals(route.when))
        {
            JsonArrayBuilder when = Json.createArrayBuilder();
            route.when.forEach(r -> when.add(condition.adaptToJson(r)));
            object.add(WHEN_NAME, when);
        }

        if (route.with != null)
        {
            object.add(WITH_NAME, with.adaptToJson(route.with));
        }

        return object.build();
    }

    @Override
    public Route adaptFromJson(
        JsonObject object)
    {
        String exit = object.getString(EXIT_NAME);
        List<Condition> when = object.containsKey(WHEN_NAME)
                ? object.getJsonArray(WHEN_NAME)
                    .stream().map(JsonValue::asJsonObject)
                    .map(condition::adaptFromJson)
                    .collect(Collectors.toList())
                : WHEN_DEFAULT;
        With wth = object.containsKey(WITH_NAME)
                ? with.adaptFromJson(object.getJsonObject(WITH_NAME))
                : null;

        return new Route(index, exit, when, wth);
    }
}
