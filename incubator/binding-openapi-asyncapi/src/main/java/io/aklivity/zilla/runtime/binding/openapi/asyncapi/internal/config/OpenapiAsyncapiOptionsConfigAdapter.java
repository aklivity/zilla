/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config;

import static java.util.Collections.unmodifiableSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.OpenapiAsyncapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class OpenapiAsyncapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String OPENAPI_NAME = "openapi";
    private static final String ASYNCAPI_NAME = "asyncapi";
    private static final String SPECS_NAME = "specs";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return OpenapiAsyncapiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        OpenapiAsyncapiOptionsConfig proxyOptions = (OpenapiAsyncapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();
        JsonObjectBuilder spec = Json.createObjectBuilder();

        JsonObjectBuilder openapi = Json.createObjectBuilder();
        for (OpenapiConfig openapiConfig : proxyOptions.specs.openapi)
        {
            final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
            final JsonObjectBuilder subjectObject = Json.createObjectBuilder();
            for (OpenapiCatalogConfig catalog : openapiConfig.catalogs)
            {
                JsonObjectBuilder schemaObject = Json.createObjectBuilder();
                schemaObject.add(SUBJECT_NAME, catalog.subject);

                if (catalog.version != null)
                {
                    schemaObject.add(VERSION_NAME, catalog.version);
                }

                subjectObject.add(catalog.name, schemaObject);
            }
            catalogObject.add(CATALOG_NAME, subjectObject);
            openapi.add(openapiConfig.apiLabel, catalogObject);
        }
        spec.add(OPENAPI_NAME, openapi);

        JsonObjectBuilder asyncapi = Json.createObjectBuilder();
        for (AsyncapiConfig asyncapiConfig : proxyOptions.specs.asyncapi)
        {
            final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
            final JsonObjectBuilder subjectObject = Json.createObjectBuilder();
            for (AsyncapiCatalogConfig catalog : asyncapiConfig.catalogs)
            {
                JsonObjectBuilder schemaObject = Json.createObjectBuilder();
                schemaObject.add(SUBJECT_NAME, catalog.subject);

                if (catalog.version != null)
                {
                    schemaObject.add(VERSION_NAME, catalog.version);
                }

                subjectObject.add(catalog.name, schemaObject);
            }
            catalogObject.add(CATALOG_NAME, subjectObject);

            asyncapi.add(asyncapiConfig.apiLabel, catalogObject);
        }
        spec.add(ASYNCAPI_NAME, asyncapi);

        object.add(SPECS_NAME, spec);

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        JsonObject specs = object.getJsonObject(SPECS_NAME);

        JsonObject openapi = specs.getJsonObject(OPENAPI_NAME);
        Set<OpenapiConfig> openapis = new LinkedHashSet<>();
        for (Map.Entry<String, JsonValue> entry : openapi.entrySet())
        {
            final String apiLabel = entry.getKey();
            final JsonObject specObject = entry.getValue().asJsonObject();

            if (specObject.containsKey(CATALOG_NAME))
            {
                final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);

                List<OpenapiCatalogConfig> catalogs = new ArrayList<>();
                for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                {
                    OpenapiCatalogConfigBuilder<OpenapiCatalogConfig> catalogBuilder = OpenapiCatalogConfig.builder();
                    JsonObject catalogObject = catalogEntry.getValue().asJsonObject();

                    catalogBuilder.name(catalogEntry.getKey());

                    if (catalogObject.containsKey(SUBJECT_NAME))
                    {
                        catalogBuilder.subject(catalogObject.getString(SUBJECT_NAME));
                    }

                    if (catalogObject.containsKey(VERSION_NAME))
                    {
                        catalogBuilder.version(catalogObject.getString(VERSION_NAME));
                    }
                    catalogs.add(catalogBuilder.build());
                }
                openapis.add(new OpenapiConfig(apiLabel, catalogs));
            }
        }

        JsonObject asyncapi = specs.getJsonObject(ASYNCAPI_NAME);
        Set<AsyncapiConfig> asyncapis = new LinkedHashSet<>();
        for (Map.Entry<String, JsonValue> entry : asyncapi.entrySet())
        {
            final String apiLabel = entry.getKey();
            final JsonObject specObject = entry.getValue().asJsonObject();

            if (specObject.containsKey(CATALOG_NAME))
            {
                final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);

                List<AsyncapiCatalogConfig> catalogs = new ArrayList<>();
                for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                {
                    AsyncapiCatalogConfigBuilder<AsyncapiCatalogConfig> catalogBuilder = AsyncapiCatalogConfig.builder();
                    JsonObject catalogObject = catalogEntry.getValue().asJsonObject();

                    catalogBuilder.name(catalogEntry.getKey());

                    if (catalogObject.containsKey(SUBJECT_NAME))
                    {
                        catalogBuilder.subject(catalogObject.getString(SUBJECT_NAME));
                    }

                    if (catalogObject.containsKey(VERSION_NAME))
                    {
                        catalogBuilder.version(catalogObject.getString(VERSION_NAME));
                    }
                    catalogs.add(catalogBuilder.build());
                }
                asyncapis.add(new AsyncapiConfig(apiLabel, Collections.emptyList(), catalogs));
            }
        }

        OpenapiAsyncapiSpecConfig specConfig = new OpenapiAsyncapiSpecConfig(
            unmodifiableSet(openapis), unmodifiableSet(asyncapis));

        return new OpenapiAsyncapiOptionsConfig(specConfig);
    }
}
