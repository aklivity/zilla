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
package io.aklivity.zilla.runtime.engine.test.internal.binding.config;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class TestBindingOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    private static final String MODE_NAME = "mode";
    private static final String CATALOGS_NAME = "catalogs";
    private static final String GUARDS_NAME = "guards";
    private static final String GUARD_NAME = "guard";
    private static final String TOKEN_NAME = "token";
    private static final String EVENTS_NAME = "events";
    private static final String TIMESTAMP_NAME = "timestamp";
    private static final String MESSAGE_NAME = "message";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        TestBindingOptionsConfig testOptions = (TestBindingOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (testOptions.mode != null)
        {
            object.add(MODE_NAME, testOptions.mode);
        }
        if (testOptions.catalogs != null)
        {
            JsonArrayBuilder catalogs = Json.createArrayBuilder();
            for (String catalog : testOptions.catalogs)
            {
                catalogs.add(catalog);
            }
            object.add(CATALOGS_NAME, catalogs);
        }
        if (testOptions.guards != null)
        {
            JsonArrayBuilder guards = Json.createArrayBuilder();
            for (TestBindingOptionsConfig.Guard g : testOptions.guards)
            {
                JsonObjectBuilder event = Json.createObjectBuilder();
                event.add(GUARD_NAME, g.guard);
                event.add(TOKEN_NAME, g.token);
                guards.add(event);
            }
            object.add(GUARDS_NAME, guards);
        }
        if (testOptions.events != null)
        {
            JsonArrayBuilder events = Json.createArrayBuilder();
            for (TestBindingOptionsConfig.Event e : testOptions.events)
            {
                JsonObjectBuilder event = Json.createObjectBuilder();
                event.add(TIMESTAMP_NAME, e.timestamp);
                event.add(MESSAGE_NAME, e.message);
                events.add(event);
            }
            object.add(EVENTS_NAME, events);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        TestBindingOptionsConfigBuilder<TestBindingOptionsConfig> testOptions = TestBindingOptionsConfig.builder();

        if (object != null)
        {
            if (object.containsKey(MODE_NAME))
            {
                testOptions.mode(object.getString(MODE_NAME));
            }
            if (object.containsKey(CATALOGS_NAME))
            {
                JsonArray catalogs = object.getJsonArray(CATALOGS_NAME);
                for (JsonValue catalog : catalogs)
                {
                    testOptions.catalog(((JsonString) catalog).getString());
                }
            }
            if (object.containsKey(GUARDS_NAME))
            {
                JsonArray guards = object.getJsonArray(GUARDS_NAME);
                for (JsonValue g : guards)
                {
                    JsonObject g0 = g.asJsonObject();
                    if (g0.containsKey(GUARD_NAME) && g0.containsKey(TOKEN_NAME))
                    {
                        testOptions.guard(g0.getString(GUARD_NAME), g0.getString(TOKEN_NAME));
                    }
                }
            }
            if (object.containsKey(EVENTS_NAME))
            {
                JsonArray events = object.getJsonArray(EVENTS_NAME);
                for (JsonValue e : events)
                {
                    JsonObject e0 = e.asJsonObject();
                    if (e0.containsKey(TIMESTAMP_NAME) && e0.containsKey(MESSAGE_NAME))
                    {
                        testOptions.event(e0.getInt(TIMESTAMP_NAME), e0.getString(MESSAGE_NAME));
                    }
                }
            }
        }

        return testOptions.build();
    }
}
