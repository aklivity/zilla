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
package io.aklivity.zilla.runtime.engine.test.internal.exporter.config;

import static java.util.function.Function.identity;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class TestExporterOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    private static final String MODE_NAME = "mode";
    private static final String EVENTS_NAME = "events";
    private static final String QNAME_NAME = "qname";
    private static final String ID_NAME = "id";
    private static final String NAME_NAME = "name";
    private static final String MESSAGE_NAME = "message";

    @Override
    public Kind kind()
    {
        return Kind.EXPORTER;
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
        TestExporterOptionsConfig testOptions = (TestExporterOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (testOptions.mode != null)
        {
            object.add(MODE_NAME, testOptions.mode);
        }

        if (testOptions.events != null)
        {
            JsonArrayBuilder events = Json.createArrayBuilder();
            for (TestExporterOptionsConfig.Event e : testOptions.events)
            {
                JsonObjectBuilder event = Json.createObjectBuilder();
                event.add(QNAME_NAME, e.qName);
                event.add(ID_NAME, e.id);
                event.add(NAME_NAME, e.name);
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
        TestExporterOptionsConfigBuilder<TestExporterOptionsConfig> testOptions = TestExporterOptionsConfig.builder()
                .inject(identity());

        if (object != null)
        {
            if (object.containsKey(MODE_NAME))
            {
                testOptions.mode(object.getString(MODE_NAME));
            }
            if (object.containsKey(EVENTS_NAME))
            {
                JsonArray events = object.getJsonArray(EVENTS_NAME);
                for (JsonValue e : events)
                {
                    JsonObject e0 = e.asJsonObject();
                    testOptions.event(
                        e0.getString(QNAME_NAME),
                        e0.getString(ID_NAME),
                        e0.getString(NAME_NAME),
                        e0.getString(MESSAGE_NAME));
                }
            }
        }

        return testOptions.build();
    }
}
