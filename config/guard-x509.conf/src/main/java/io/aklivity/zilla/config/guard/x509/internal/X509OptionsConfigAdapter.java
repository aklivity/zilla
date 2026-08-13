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
package io.aklivity.zilla.config.guard.x509.internal;

import static io.aklivity.zilla.config.guard.x509.X509OptionsConfigBuilder.IDENTITY_DEFAULT;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.guard.x509.X509OptionsConfig;
import io.aklivity.zilla.config.guard.x509.X509OptionsConfigBuilder;

public final class X509OptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String IDENTITY_NAME = "identity";
    private static final String ATTRIBUTES_NAME = "attributes";
    private static final String ROLES_NAME = "roles";

    private final X509MatchConfigAdapter match = new X509MatchConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        X509OptionsConfig x509Options = (X509OptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (x509Options.identity != null && !IDENTITY_DEFAULT.equals(x509Options.identity))
        {
            object.add(IDENTITY_NAME, x509Options.identity);
        }

        if (x509Options.attributes != null && !x509Options.attributes.isEmpty())
        {
            JsonObjectBuilder attributes = Json.createObjectBuilder();
            x509Options.attributes.forEach(attributes::add);

            object.add(ATTRIBUTES_NAME, attributes);
        }

        if (x509Options.roles != null && !x509Options.roles.isEmpty())
        {
            JsonObjectBuilder roles = Json.createObjectBuilder();
            x509Options.roles.forEach((role, matches) ->
            {
                JsonArrayBuilder newMatches = Json.createArrayBuilder();
                matches.forEach(newMatch -> newMatches.add(match.adaptToJson(newMatch)));
                roles.add(role, newMatches);
            });

            object.add(ROLES_NAME, roles);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        X509OptionsConfigBuilder<X509OptionsConfig> x509Options = X509OptionsConfig.builder();

        if (object.containsKey(IDENTITY_NAME))
        {
            x509Options.identity(object.getString(IDENTITY_NAME));
        }

        if (object.containsKey(ATTRIBUTES_NAME))
        {
            object.getJsonObject(ATTRIBUTES_NAME)
                .forEach((name, field) -> x509Options.attribute(name, ((JsonString) field).getString()));
        }

        if (object.containsKey(ROLES_NAME))
        {
            object.getJsonObject(ROLES_NAME)
                .forEach((role, matches) -> matches.asJsonArray()
                    .stream()
                    .map(JsonValue::asJsonObject)
                    .map(match::adaptFromJson)
                    .forEach(newMatch -> x509Options.match(role, newMatch)));
        }

        return x509Options.build();
    }
}
