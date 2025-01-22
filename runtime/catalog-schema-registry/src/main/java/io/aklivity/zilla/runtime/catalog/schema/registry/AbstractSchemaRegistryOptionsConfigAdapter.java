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
package io.aklivity.zilla.runtime.catalog.schema.registry;

import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.catalog.schema.registry.config.AbstractSchemaRegistryOptionsConfig;
import io.aklivity.zilla.runtime.catalog.schema.registry.config.AbstractSchemaRegistryOptionsConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public abstract class AbstractSchemaRegistryOptionsConfigAdapter<T extends AbstractSchemaRegistryOptionsConfig>
    implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String URL = "url";
    private static final String CONTEXT = "context";
    private static final String MAX_AGE_NAME = "max-age";
    private static final String KEYS_NAME = "keys";
    private static final String TRUST_NAME = "trust";
    private static final String TRUSTCACERTS_NAME = "trustcacerts";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_HEADERS_NAME = "headers";
    private static final String SECURITY_NAME = "security";

    private final String type;
    private final Set<String> aliases;
    private final Class<T> optionsType;
    private final Supplier<AbstractSchemaRegistryOptionsConfigBuilder<T, ?>> supplyBuilder;

    @Override
    public Kind kind()
    {
        return Kind.CATALOG;
    }

    @Override
    public String type()
    {
        return type;
    }

    @Override
    public Set<String> aliases()
    {
        return aliases;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        T config = optionsType.cast(options);
        JsonObjectBuilder catalog = Json.createObjectBuilder();

        if (config.url != null &&
            !config.url.isEmpty())
        {
            catalog.add(URL, config.url);
        }

        if (config.context != null &&
            !config.context.isEmpty())
        {
            catalog.add(CONTEXT, config.context);
        }

        Duration maxAge = config.maxAge;
        if (maxAge != null)
        {
            catalog.add(MAX_AGE_NAME, maxAge.toSeconds());
        }

        JsonObjectBuilder security = Json.createObjectBuilder();

        if (config.keys != null)
        {
            JsonArrayBuilder keys = Json.createArrayBuilder();
            config.keys.forEach(keys::add);
            security.add(KEYS_NAME, keys);
        }

        if (config.trust != null)
        {
            JsonArrayBuilder trust = Json.createArrayBuilder();
            config.trust.forEach(trust::add);
            security.add(TRUST_NAME, trust);
        }

        if (config.trust != null && config.trustcacerts ||
            config.trust == null && !config.trustcacerts)
        {
            security.add(TRUSTCACERTS_NAME, config.trustcacerts);
        }

        JsonObject securityJson = security.build();
        if (!securityJson.isEmpty())
        {
            catalog.add(SECURITY_NAME, securityJson);
        }

        if (config.authorization != null)
        {
            JsonObjectBuilder headers = Json.createObjectBuilder();
            headers.add(AUTHORIZATION_NAME, config.authorization);

            JsonObjectBuilder credentials = Json.createObjectBuilder();
            credentials.add(AUTHORIZATION_CREDENTIALS_HEADERS_NAME, headers);

            catalog.add(AUTHORIZATION_CREDENTIALS_NAME, credentials);
        }

        return catalog.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        AbstractSchemaRegistryOptionsConfigBuilder<T, ?> options = supplyBuilder.get();

        if (object != null)
        {
            if (object.containsKey(URL))
            {
                options.url(object.getString(URL));
            }

            if (object.containsKey(CONTEXT))
            {
                options.context(object.getString(CONTEXT));
            }

            if (object.containsKey(MAX_AGE_NAME))
            {
                options.maxAge(Duration.ofSeconds(object.getJsonNumber(MAX_AGE_NAME).longValue()));
            }

            if (object.containsKey(SECURITY_NAME))
            {
                JsonObject security = object.getJsonObject(SECURITY_NAME);

                if (security.containsKey(KEYS_NAME))
                {
                    options.keys(asListString(security.getJsonArray(KEYS_NAME)));
                }

                if (security.containsKey(TRUST_NAME))
                {
                    options.trust(asListString(security.getJsonArray(TRUST_NAME)));
                }

                if (security.containsKey(TRUSTCACERTS_NAME))
                {
                    options.trustcacerts(security.getBoolean(TRUSTCACERTS_NAME));
                }
            }

            if (object.containsKey(AUTHORIZATION_CREDENTIALS_NAME))
            {
                JsonObject credentials = object.getJsonObject(AUTHORIZATION_CREDENTIALS_NAME);

                JsonObject headers = credentials.getJsonObject(AUTHORIZATION_CREDENTIALS_HEADERS_NAME);

                options.authorization(headers.getString(AUTHORIZATION_NAME));
            }
        }

        return options.build();
    }

    private static List<String> asListString(
        JsonArray array)
    {
        return array.stream()
            .map(AbstractSchemaRegistryOptionsConfigAdapter::asString)
            .collect(toList());
    }

    private static String asString(
        JsonValue value)
    {
        switch (value.getValueType())
        {
        case STRING:
            return ((JsonString) value).getString();
        case NULL:
            return null;
        default:
            throw new IllegalArgumentException("Unexpected type: " + value.getValueType());
        }
    }

    protected AbstractSchemaRegistryOptionsConfigAdapter(
        String type,
        Class<T> optionsType,
        Supplier<AbstractSchemaRegistryOptionsConfigBuilder<T, ?>> supplyOptions)
    {
        this(type, ALIASES_DEFAULT, optionsType, supplyOptions);
    }

    protected AbstractSchemaRegistryOptionsConfigAdapter(
        String type,
        Set<String> aliases,
        Class<T> optionsType,
        Supplier<AbstractSchemaRegistryOptionsConfigBuilder<T, ?>> supplyOptions)
    {
        this.type = type;
        this.aliases = aliases;
        this.optionsType = optionsType;
        this.supplyBuilder = supplyOptions;
    }
}
