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
package io.aklivity.zilla.runtime.engine.test.internal.guard.config;

import static io.aklivity.zilla.runtime.engine.test.internal.guard.TestGuardConfig.DEFAULT_CHALLENGE_NEVER;
import static io.aklivity.zilla.runtime.engine.test.internal.guard.TestGuardConfig.DEFAULT_LIFETIME_FOREVER;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class TestGuardOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    private static final String CREDENTIALS_NAME = "credentials";
    private static final String LIFETIME_NAME = "lifetime";
    private static final String CHALLENGE_NAME = "challenge";
    private static final String ROLES_NAME = "roles";

    @Override
    public Kind kind()
    {
        return Kind.GUARD;
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
        TestGuardOptionsConfig testOptions = (TestGuardOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(CREDENTIALS_NAME, testOptions.credentials);

        if (testOptions.lifetime != DEFAULT_LIFETIME_FOREVER)
        {
            object.add(LIFETIME_NAME, testOptions.lifetime.toString());
        }

        if (testOptions.challenge != DEFAULT_CHALLENGE_NEVER)
        {
            object.add(CHALLENGE_NAME, testOptions.challenge.toString());
        }

        if (testOptions.roles != null &&
            !testOptions.roles.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            testOptions.roles.forEach(entries::add);

            object.add(ROLES_NAME, entries);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        String newCredentials = object.containsKey(CREDENTIALS_NAME)
                ? object.getString(CREDENTIALS_NAME)
                : null;

        Duration newLifetime = object.containsKey(LIFETIME_NAME)
                ? Duration.parse(object.getString(LIFETIME_NAME))
                : DEFAULT_LIFETIME_FOREVER;

        Duration newChallenge = object.containsKey(CHALLENGE_NAME)
                ? Duration.parse(object.getString(CHALLENGE_NAME))
                : DEFAULT_CHALLENGE_NEVER;

        JsonArray roles = object.containsKey(ROLES_NAME)
                ? object.getJsonArray(ROLES_NAME)
                : null;

        List<String> newRoles = null;

        if (roles != null)
        {
            List<String> newRoles0 = new ArrayList<>();
            roles.forEach(v ->
                newRoles0.add(JsonString.class.cast(v).getString()));
            newRoles = newRoles0;
        }

        return new TestGuardOptionsConfig(newCredentials, newLifetime, newChallenge, newRoles);
    }
}
