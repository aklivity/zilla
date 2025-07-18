/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.jwt.internal.config;

import static io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfigBuilder.ROLES_DEFAULT;
import static java.util.Collections.emptyList;

import java.time.Duration;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtKeyConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfigBuilder;
import io.aklivity.zilla.runtime.guard.jwt.internal.JwtGuard;

public final class JwtOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String ISSUER_NAME = "issuer";
    private static final String AUDIENCE_NAME = "audience";
    private static final String ROLES = "roles";
    private static final String KEYS_NAME = "keys";
    private static final String CHALLENGE_NAME = "challenge";
    private static final String IDENTITY_NAME = "identity";

    private static final List<JwtKeyConfig> KEYS_DEFAULT = emptyList();

    private final JwtKeyConfigAdapter key = new JwtKeyConfigAdapter();

    @Override
    public String type()
    {
        return JwtGuard.NAME;
    }

    @Override
    public Kind kind()
    {
        return Kind.GUARD;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        JwtOptionsConfig jwtOptions = (JwtOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (jwtOptions.issuer != null)
        {
            object.add(ISSUER_NAME, jwtOptions.issuer);
        }

        if (jwtOptions.audience != null)
        {
            object.add(AUDIENCE_NAME, jwtOptions.audience);
        }

        if (jwtOptions.roles != null && !ROLES_DEFAULT.equals(jwtOptions.roles))
        {
            object.add(ROLES, jwtOptions.roles);
        }

        if (jwtOptions.keys != null)
        {
            JsonArrayBuilder newKeys = Json.createArrayBuilder();

            for (JwtKeyConfig newKey : jwtOptions.keys)
            {
                newKeys.add(key.adaptToJson(newKey));
            }

            object.add(KEYS_NAME, newKeys);
        }

        if (jwtOptions.challenge.isPresent())
        {
            object.add(CHALLENGE_NAME, jwtOptions.challenge.get().getSeconds());
        }

        if (jwtOptions.identity != null)
        {
            object.add(IDENTITY_NAME, jwtOptions.identity);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        JwtOptionsConfigBuilder<JwtOptionsConfig> jwtOptions = JwtOptionsConfig.builder();

        String issuer = object.containsKey(ISSUER_NAME) ? object.getString(ISSUER_NAME) : null;
        if (issuer != null)
        {
            jwtOptions.issuer(issuer);
        }

        if (object.containsKey(AUDIENCE_NAME))
        {
            jwtOptions.audience(object.getString(AUDIENCE_NAME));
        }

        if (object.containsKey(ROLES))
        {
            jwtOptions.roles(object.getString(ROLES));
        }

        if (object.containsKey(KEYS_NAME))
        {
            JsonValue keysValue = object.getValue(String.format("/%s", KEYS_NAME));
            switch (keysValue.getValueType())
            {
            case ARRAY:
                keysValue.asJsonArray()
                    .stream()
                    .map(JsonValue::asJsonObject)
                    .map(key::adaptFromJson)
                    .forEach(jwtOptions::key);
                break;
            case STRING:
                jwtOptions.keys(KEYS_DEFAULT)
                          .keysURL(((JsonString) keysValue).getString());
                break;
            default:
                break;
            }
        }
        else
        {
            jwtOptions.keys(KEYS_DEFAULT);

            if (issuer != null)
            {
                jwtOptions.keysURL(issuer.endsWith("/")
                    ? String.format("%s.well-known/jwks.json", issuer)
                    : String.format("%s/.well-known/jwks.json", issuer));
            }
        }

        if (object.containsKey(CHALLENGE_NAME))
        {
            jwtOptions.challenge(Duration.ofSeconds(object.getInt(CHALLENGE_NAME)));
        }

        if (object.containsKey(IDENTITY_NAME))
        {
            jwtOptions.identity(object.getString(IDENTITY_NAME));
        }

        return jwtOptions.build();
    }
}
