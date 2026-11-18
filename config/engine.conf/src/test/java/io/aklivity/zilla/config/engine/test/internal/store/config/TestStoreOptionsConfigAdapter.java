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
package io.aklivity.zilla.config.engine.test.internal.store.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig.Assertion;

public final class TestStoreOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String INCREMENT_OP = "increment";
    private static final String ENTRIES_NAME = "entries";
    private static final String ASSERTIONS_NAME = "assertions";
    private static final String OP_NAME = "op";
    private static final String KEY_NAME = "key";
    private static final String KEYS_NAME = "keys";
    private static final String VALUE_NAME = "value";
    private static final String EXPECTED_NAME = "expected";
    private static final String DELTA_NAME = "delta";
    private static final String TTL_NAME = "ttl";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        TestStoreOptionsConfig testOptions = (TestStoreOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (testOptions.entries != null &&
            !testOptions.entries.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            testOptions.entries.forEach(entries::add);
            object.add(ENTRIES_NAME, entries);
        }

        if (testOptions.assertions != null)
        {
            JsonArrayBuilder assertions = Json.createArrayBuilder();
            for (Assertion a : testOptions.assertions)
            {
                JsonObjectBuilder assertion = Json.createObjectBuilder();
                assertion.add(OP_NAME, a.op);
                if (a.key != null)
                {
                    assertion.add(KEY_NAME, a.key);
                }
                if (a.keys != null)
                {
                    JsonArrayBuilder keys = Json.createArrayBuilder();
                    a.keys.forEach(keys::add);
                    assertion.add(KEYS_NAME, keys);
                }
                if (a.expected != null)
                {
                    assertion.add(EXPECTED_NAME, a.expected);
                }
                if (INCREMENT_OP.equals(a.op))
                {
                    assertion.add(DELTA_NAME, a.delta);
                }
                if (a.value != null)
                {
                    assertion.add(VALUE_NAME, a.value);
                }
                if (a.ttl != null)
                {
                    assertion.add(TTL_NAME, a.ttl.toString());
                }
                assertions.add(assertion);
            }
            object.add(ASSERTIONS_NAME, assertions);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        TestStoreOptionsConfigBuilder<TestStoreOptionsConfig> testOptions = TestStoreOptionsConfig.builder();

        if (object != null && object.containsKey(ENTRIES_NAME))
        {
            object.getJsonObject(ENTRIES_NAME)
                .forEach((key, value) -> testOptions.entry(key, ((JsonString) value).getString()));
        }

        if (object != null && object.containsKey(ASSERTIONS_NAME))
        {
            final JsonArray array = object.getJsonArray(ASSERTIONS_NAME);
            final List<Assertion> assertions = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++)
            {
                final JsonObject assertion = array.getJsonObject(i);
                final List<String> keys = assertion.containsKey(KEYS_NAME)
                    ? assertion.getJsonArray(KEYS_NAME).getValuesAs(JsonString.class).stream()
                        .map(JsonString::getString)
                        .toList()
                    : null;
                assertions.add(new Assertion(
                    assertion.getString(OP_NAME),
                    assertion.getString(KEY_NAME, null),
                    keys,
                    assertion.getString(VALUE_NAME, null),
                    assertion.getString(EXPECTED_NAME, null),
                    assertion.containsKey(DELTA_NAME) ? assertion.getJsonNumber(DELTA_NAME).longValue() : 0L,
                    assertion.containsKey(TTL_NAME) ? Duration.parse(assertion.getString(TTL_NAME)) : null));
            }
            testOptions.assertions(assertions);
        }

        return testOptions.build();
    }
}
