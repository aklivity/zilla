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
package io.aklivity.zilla.runtime.engine.internal.config;

import static io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder.GUARDED_DEFAULT;
import static io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder.WHEN_DEFAULT;

import java.util.Optional;
import java.util.regex.Matcher;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;

public class RouteAdapter implements JsonbAdapter<RouteConfig, JsonObject>
{
    private static final String EXIT_NAME = "exit";
    private static final String WHEN_NAME = "when";
    private static final String WITH_NAME = "with";
    private static final String GUARDED_NAME = "guarded";

    private final ConditionAdapter condition;
    private final WithAdapter with;

    private String namespace;
    private int index;

    public RouteAdapter(
        ConfigAdapterContext context)
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

    public RouteAdapter adaptNamespace(
        String namespace)
    {
        this.namespace = namespace;
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
            route.when.forEach(r -> when.add(condition.adaptToJson(r)));
            object.add(WHEN_NAME, when);
        }

        if (route.with != null)
        {
            object.add(WITH_NAME, with.adaptToJson(route.with));
        }

        if (!GUARDED_DEFAULT.equals(route.guarded))
        {
            JsonObjectBuilder newGuarded = Json.createObjectBuilder();

            for (GuardedConfig guarded : route.guarded)
            {
                JsonArrayBuilder newRoles = Json.createArrayBuilder();
                guarded.roles.forEach(newRoles::add);

                String name = namespace.equals(guarded.namespace) ? guarded.name : guarded.qname;
                newGuarded.add(name, newRoles);
            }

            object.add(GUARDED_NAME, newGuarded);
        }

        return object.build();
    }

    @Override
    public RouteConfig adaptFromJson(
        JsonObject object)
    {
        RouteConfigBuilder<RouteConfig> route = RouteConfig.builder()
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
                .map(condition::adaptFromJson)
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
                Matcher matcher = NamespaceAdapter.PATTERN_NAME.matcher(name);
                assert matcher.matches();

                GuardedConfigBuilder<?> guardedBy = route.guarded()
                    .namespace(Optional.ofNullable(matcher.group("namespace")).orElse(namespace))
                    .name(matcher.group("name"));

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
}
