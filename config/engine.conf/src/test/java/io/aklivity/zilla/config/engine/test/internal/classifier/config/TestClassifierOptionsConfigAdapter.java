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
package io.aklivity.zilla.config.engine.test.internal.classifier.config;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class TestClassifierOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String FAILURE_NAME = "failure";
    private static final String FAILURE_ON_NAME = "failure-on";
    private static final String DELAY_NAME = "delay";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        TestClassifierOptionsConfig testOptions = (TestClassifierOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (testOptions.failure)
        {
            object.add(FAILURE_NAME, true);
        }

        if (!testOptions.failureOn.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            testOptions.failureOn.forEach(entries::add);

            object.add(FAILURE_ON_NAME, entries);
        }

        if (testOptions.delay != 0)
        {
            object.add(DELAY_NAME, testOptions.delay);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        TestClassifierOptionsConfigBuilder<TestClassifierOptionsConfig> testOptions = TestClassifierOptionsConfig.builder();

        if (object != null)
        {
            testOptions.failure(object.getBoolean(FAILURE_NAME, false));

            if (object.containsKey(FAILURE_ON_NAME))
            {
                JsonArray entries = object.getJsonArray(FAILURE_ON_NAME);
                entries.stream()
                    .map(JsonString.class::cast)
                    .map(JsonString::getString)
                    .forEach(testOptions::failureOn);
            }

            testOptions.delay(object.getInt(DELAY_NAME, 0));
        }

        return testOptions.build();
    }
}
