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

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
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
        }

        return options.build();
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
