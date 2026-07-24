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
package io.aklivity.zilla.config.engine.internal;

import static io.aklivity.zilla.config.engine.RouteConfigBuilder.GUARDED_DEFAULT;
import static io.aklivity.zilla.config.engine.RouteConfigBuilder.WHEN_DEFAULT;
import static org.agrona.LangUtil.rethrowUnchecked;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.BindingInfoRegistry;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.GenericRouteConfigBuilder;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.GuardedConfigBuilder;
import io.aklivity.zilla.config.engine.RouteConfig;

public class RouteAdapter implements JsonbAdapter<RouteConfig, JsonObject>
{
    private static final String EXIT_NAME = "exit";
    private static final String WHEN_NAME = "when";
    private static final String WITH_NAME = "with";
    private static final String GUARDED_NAME = "guarded";

    private final ConditionAdapter condition;
    private final WithAdapter with;

    private int index;

    public RouteAdapter()
    {
        this(null);
    }

    public RouteAdapter(
        BindingInfoRegistry bindingInfos)
    {
        condition = new ConditionAdapter(bindingInfos);
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
        RouteConfig route)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (route.exit != null)
        {
            object.add(EXIT_NAME, route.exit);
        }

        if (!WHEN_DEFAULT.equals(route.when))
        {
            JsonArrayBuilder when = Json.createArrayBuilder();
            route.when.forEach(r -> when.add(adaptConditionToJson(r)));
            object.add(WHEN_NAME, when);
        }

        if (route.with != null)
        {
            final JsonObject withObject = with.adaptToJson(route.with);
            if (withObject != null)
            {
                object.add(WITH_NAME, withObject);
            }
        }

        if (!GUARDED_DEFAULT.equals(route.guarded))
        {
            JsonObjectBuilder newGuarded = Json.createObjectBuilder();

            for (GuardedConfig guarded : route.guarded)
            {
                JsonArrayBuilder newRoles = Json.createArrayBuilder();
                guarded.roles.forEach(newRoles::add);
                newGuarded.add(guarded.name, newRoles);
            }

            object.add(GUARDED_NAME, newGuarded);
        }

        return object.build();
    }

    @Override
    public RouteConfig adaptFromJson(
        JsonObject object)
    {
        GenericRouteConfigBuilder<RouteConfig> route = RouteConfig.builder()
            .order(index);

        if (object.containsKey(EXIT_NAME))
        {
            route.exit(object.getString(EXIT_NAME));
        }

        if (object.containsKey(WHEN_NAME))
        {
            object.getJsonArray(WHEN_NAME)
                .stream()
                .map(JsonValue::asJsonObject)
                .map(this::adaptConditionFromJson)
                .forEach(route::when);
        }

        if (object.containsKey(WITH_NAME))
        {
            route.with(with.adaptFromJson(object.getJsonObject(WITH_NAME)));
        }

        if (object.containsKey(GUARDED_NAME))
        {
            JsonObject guarded = object.getJsonObject(GUARDED_NAME);
            for (String name : guarded.keySet())
            {
                GuardedConfigBuilder<?> guardedBy = route.guarded()
                    .name(name);

                guarded.getJsonArray(name)
                    .stream()
                    .map(JsonString.class::cast)
                    .map(JsonString::getString)
                    .forEach(guardedBy::role);

                guardedBy.build();
            }
        }

        return route.build();
    }

    private JsonObject adaptConditionToJson(
        ConditionConfig condition0)
    {
        JsonObject object = null;
        try
        {
            object = condition.adaptToJson(condition0);
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return object;
    }

    private ConditionConfig adaptConditionFromJson(
        JsonObject object)
    {
        ConditionConfig condition0 = null;
        try
        {
            condition0 = condition.adaptFromJson(object);
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return condition0;
    }
}
