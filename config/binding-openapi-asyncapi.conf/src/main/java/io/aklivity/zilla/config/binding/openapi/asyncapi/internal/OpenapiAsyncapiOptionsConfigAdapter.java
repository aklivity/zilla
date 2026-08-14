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
package io.aklivity.zilla.config.binding.openapi.asyncapi.internal;

import static java.util.Collections.unmodifiableSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.openapi.asyncapi.AsyncapiCatalogConfig;
import io.aklivity.zilla.config.binding.openapi.asyncapi.AsyncapiCatalogConfigBuilder;
import io.aklivity.zilla.config.binding.openapi.asyncapi.AsyncapiSpecificationConfig;
import io.aklivity.zilla.config.binding.openapi.asyncapi.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiCatalogConfig;
import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiCatalogConfigBuilder;
import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiSpecificationConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class OpenapiAsyncapiOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String OPENAPI_NAME = "openapi";
    private static final String ASYNCAPI_NAME = "asyncapi";
    private static final String SPECS_NAME = "specs";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String SECURITY_NAME = "security";
    private static final String OVERLAY_NAME = "overlay";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        OpenapiAsyncapiOptionsConfig proxyOptions = (OpenapiAsyncapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();
        JsonObjectBuilder spec = Json.createObjectBuilder();

        JsonObjectBuilder openapi = Json.createObjectBuilder();
        for (OpenapiSpecificationConfig openapiConfig : proxyOptions.specs.openapi)
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

                if (catalog.overlay != null)
                {
                    schemaObject.add(OVERLAY_NAME, openapiOverlayObject(catalog.overlay));
                }

                subjectObject.add(catalog.name, schemaObject);
            }
            catalogObject.add(CATALOG_NAME, subjectObject);

            if (openapiConfig.security != null && !openapiConfig.security.isEmpty())
            {
                final JsonObjectBuilder security = Json.createObjectBuilder();
                openapiConfig.security.forEach(security::add);
                catalogObject.add(SECURITY_NAME, security);
            }

            openapi.add(openapiConfig.label, catalogObject);
        }
        spec.add(OPENAPI_NAME, openapi);

        JsonObjectBuilder asyncapi = Json.createObjectBuilder();
        for (AsyncapiSpecificationConfig asyncapiConfig : proxyOptions.specs.asyncapi)
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

                if (catalog.overlay != null)
                {
                    schemaObject.add(OVERLAY_NAME, asyncapiOverlayObject(catalog.overlay));
                }

                subjectObject.add(catalog.name, schemaObject);
            }
            catalogObject.add(CATALOG_NAME, subjectObject);

            asyncapi.add(asyncapiConfig.label, catalogObject);
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
        Set<OpenapiSpecificationConfig> openapis = new LinkedHashSet<>();
        for (Map.Entry<String, JsonValue> entry : openapi.entrySet())
        {
            final String specLabel = entry.getKey();
            final JsonObject specObject = entry.getValue().asJsonObject();

            if (specObject.containsKey(CATALOG_NAME))
            {
                final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);

                OpenapiCatalogConfig deprecatedOverlay = null;
                if (specObject.containsKey(OVERLAY_NAME))
                {
                    deprecatedOverlay = asOpenapiOverlay(specObject.getJsonObject(OVERLAY_NAME));
                }

                List<OpenapiCatalogConfig> catalogs = new ArrayList<>();
                for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                {
                    final JsonObject catalogObject = catalogEntry.getValue().asJsonObject();

                    OpenapiCatalogConfigBuilder<OpenapiCatalogConfig> catalogBuilder = OpenapiCatalogConfig.builder();
                    catalogBuilder.name(catalogEntry.getKey());

                    if (catalogObject.containsKey(SUBJECT_NAME))
                    {
                        catalogBuilder.subject(catalogObject.getString(SUBJECT_NAME));
                    }

                    if (catalogObject.containsKey(VERSION_NAME))
                    {
                        catalogBuilder.version(catalogObject.getString(VERSION_NAME));
                    }

                    final OpenapiCatalogConfig catalogOverlay = catalogObject.containsKey(OVERLAY_NAME)
                        ? asOpenapiOverlay(catalogObject.getJsonObject(OVERLAY_NAME))
                        : deprecatedOverlay;

                    if (catalogOverlay != null)
                    {
                        catalogBuilder.overlay(catalogOverlay);
                    }

                    catalogs.add(catalogBuilder.build());
                }

                Map<String, String> security = null;
                if (specObject.containsKey(SECURITY_NAME))
                {
                    security = new LinkedHashMap<>();
                    final JsonObject securityObject = specObject.getJsonObject(SECURITY_NAME);
                    for (String scheme : securityObject.keySet())
                    {
                        security.put(scheme, securityObject.getString(scheme));
                    }
                }

                openapis.add(new OpenapiSpecificationConfig(specLabel, null, catalogs, security));
            }
        }

        JsonObject asyncapiObject = specs.getJsonObject(ASYNCAPI_NAME);
        Set<AsyncapiSpecificationConfig> asyncapis = new LinkedHashSet<>();
        for (Map.Entry<String, JsonValue> entry : asyncapiObject.entrySet())
        {
            final String specLabel = entry.getKey();
            final JsonObject specObject = entry.getValue().asJsonObject();

            AsyncapiSpecificationConfigBuilder<AsyncapiSpecificationConfig> asyncapi = AsyncapiSpecificationConfig.builder()
                .label(specLabel);

            if (specObject.containsKey(CATALOG_NAME))
            {
                final JsonObject catalogObject = specObject.getJsonObject(CATALOG_NAME);

                AsyncapiCatalogConfig deprecatedOverlay = null;
                if (specObject.containsKey(OVERLAY_NAME))
                {
                    deprecatedOverlay = asAsyncapiOverlay(specObject.getJsonObject(OVERLAY_NAME));
                }

                for (Map.Entry<String, JsonValue> catalogEntry : catalogObject.entrySet())
                {
                    final String catalogName = catalogEntry.getKey();
                    final JsonObject catalogValue = catalogEntry.getValue().asJsonObject();

                    final AsyncapiCatalogConfigBuilder<AsyncapiCatalogConfig> catalogBuilder = AsyncapiCatalogConfig.builder();
                    catalogBuilder.name(catalogName);

                    if (catalogValue.containsKey(SUBJECT_NAME))
                    {
                        catalogBuilder.subject(catalogValue.getString(SUBJECT_NAME));
                    }

                    if (catalogValue.containsKey(VERSION_NAME))
                    {
                        catalogBuilder.version(catalogValue.getString(VERSION_NAME));
                    }

                    final AsyncapiCatalogConfig catalogOverlay = catalogValue.containsKey(OVERLAY_NAME)
                        ? asAsyncapiOverlay(catalogValue.getJsonObject(OVERLAY_NAME))
                        : deprecatedOverlay;

                    if (catalogOverlay != null)
                    {
                        catalogBuilder.overlay(catalogOverlay);
                    }

                    asyncapi.catalog(catalogBuilder.build());
                }

                asyncapis.add(asyncapi.build());
            }
        }

        OpenapiAsyncapiSpecConfig specConfig = OpenapiAsyncapiSpecConfig.builder()
            .openapi(unmodifiableSet(openapis))
            .asyncapi(unmodifiableSet(asyncapis))
            .build();

        return OpenapiAsyncapiOptionsConfig.builder()
            .specs(specConfig)
            .build();
    }

    private static OpenapiCatalogConfig asOpenapiOverlay(
        JsonObject overlayObject)
    {
        final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
        final JsonObject overlaySchemaObject = overlayEntry.getValue().asJsonObject();

        OpenapiCatalogConfigBuilder<OpenapiCatalogConfig> overlayBuilder = OpenapiCatalogConfig.builder()
            .name(overlayEntry.getKey());

        if (overlaySchemaObject.containsKey(SUBJECT_NAME))
        {
            overlayBuilder.subject(overlaySchemaObject.getString(SUBJECT_NAME));
        }

        if (overlaySchemaObject.containsKey(VERSION_NAME))
        {
            overlayBuilder.version(overlaySchemaObject.getString(VERSION_NAME));
        }

        return overlayBuilder.build();
    }

    private static JsonObjectBuilder openapiOverlayObject(
        OpenapiCatalogConfig overlay)
    {
        final JsonObjectBuilder overlaySchema = Json.createObjectBuilder();
        overlaySchema.add(SUBJECT_NAME, overlay.subject);
        if (overlay.version != null)
        {
            overlaySchema.add(VERSION_NAME, overlay.version);
        }

        final JsonObjectBuilder overlaySubject = Json.createObjectBuilder();
        overlaySubject.add(overlay.name, overlaySchema);
        return overlaySubject;
    }

    private static AsyncapiCatalogConfig asAsyncapiOverlay(
        JsonObject overlayObject)
    {
        final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
        final JsonObject overlaySchemaObject = overlayEntry.getValue().asJsonObject();

        AsyncapiCatalogConfigBuilder<AsyncapiCatalogConfig> overlayBuilder = AsyncapiCatalogConfig.builder()
            .name(overlayEntry.getKey());

        if (overlaySchemaObject.containsKey(SUBJECT_NAME))
        {
            overlayBuilder.subject(overlaySchemaObject.getString(SUBJECT_NAME));
        }

        if (overlaySchemaObject.containsKey(VERSION_NAME))
        {
            overlayBuilder.version(overlaySchemaObject.getString(VERSION_NAME));
        }

        return overlayBuilder.build();
    }

    private static JsonObjectBuilder asyncapiOverlayObject(
        AsyncapiCatalogConfig overlay)
    {
        final JsonObjectBuilder overlaySchema = Json.createObjectBuilder();
        overlaySchema.add(SUBJECT_NAME, overlay.subject);
        if (overlay.version != null)
        {
            overlaySchema.add(VERSION_NAME, overlay.version);
        }

        final JsonObjectBuilder overlaySubject = Json.createObjectBuilder();
        overlaySubject.add(overlay.name, overlaySchema);
        return overlaySubject;
    }
}
