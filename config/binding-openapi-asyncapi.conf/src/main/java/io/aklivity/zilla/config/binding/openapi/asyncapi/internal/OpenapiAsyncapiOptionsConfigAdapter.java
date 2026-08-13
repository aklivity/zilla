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

import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.OverlayConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.SchemaConfigAdapter;
import io.aklivity.zilla.config.engine.SchemaConfigBuilder;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;

public final class OpenapiAsyncapiOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String OPENAPI_NAME = "openapi";
    private static final String ASYNCAPI_NAME = "asyncapi";
    private static final String SPECS_NAME = "specs";
    private static final String CATALOG_NAME = "catalog";
    private static final String SECURITY_NAME = "security";
    private static final String OVERLAY_NAME = "overlay";

    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();

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
                subjectObject.add(catalog.name, schema.adaptToJson(asOpenapiSchemaConfig(catalog)));
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
                subjectObject.add(catalog.name, schema.adaptToJson(asAsyncapiSchemaConfig(catalog)));
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
                    final JsonObject overlayObject = specObject.getJsonObject(OVERLAY_NAME);
                    final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
                    deprecatedOverlay = asOpenapiCatalogConfig(overlayEntry.getKey(), overlayEntry.getValue().asJsonObject());
                }

                List<OpenapiCatalogConfig> catalogs = new ArrayList<>();
                for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                {
                    final String catalogName = catalogEntry.getKey();
                    final SchemaConfig parsed = schema.adaptFromJson(catalogEntry.getValue().asJsonObject());
                    final OpenapiCatalogConfig overlay = parsed.overlay != null
                        ? asOpenapiCatalogConfig(parsed.overlay.name, parsed.overlay.schema)
                        : deprecatedOverlay;

                    catalogs.add(OpenapiCatalogConfig.builder()
                        .name(catalogName)
                        .subject(parsed.subject)
                        .version(parsed.version)
                        .overlay(overlay)
                        .build());
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
                    final JsonObject overlayObject = specObject.getJsonObject(OVERLAY_NAME);
                    final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
                    deprecatedOverlay = asAsyncapiCatalogConfig(overlayEntry.getKey(), overlayEntry.getValue().asJsonObject());
                }

                for (Map.Entry<String, JsonValue> catalogEntry : catalogObject.entrySet())
                {
                    final String catalogName = catalogEntry.getKey();
                    final SchemaConfig parsed = schema.adaptFromJson(catalogEntry.getValue().asJsonObject());
                    final AsyncapiCatalogConfig overlay = parsed.overlay != null
                        ? asAsyncapiCatalogConfig(parsed.overlay.name, parsed.overlay.schema)
                        : deprecatedOverlay;

                    asyncapi.catalog(AsyncapiCatalogConfig.builder()
                        .name(catalogName)
                        .subject(parsed.subject)
                        .version(parsed.version)
                        .overlay(overlay)
                        .build());
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

    private OpenapiCatalogConfig asOpenapiCatalogConfig(
        String name,
        JsonObject catalogObject)
    {
        return asOpenapiCatalogConfig(name, schema.adaptFromJson(catalogObject));
    }

    private OpenapiCatalogConfig asOpenapiCatalogConfig(
        String name,
        SchemaConfig parsed)
    {
        OpenapiCatalogConfigBuilder<OpenapiCatalogConfig> builder = OpenapiCatalogConfig.builder()
            .name(name)
            .subject(parsed.subject)
            .version(parsed.version);

        if (parsed.overlay != null)
        {
            builder.overlay(asOpenapiCatalogConfig(parsed.overlay.name, parsed.overlay.schema));
        }

        return builder.build();
    }

    private SchemaConfig asOpenapiSchemaConfig(
        OpenapiCatalogConfig catalog)
    {
        SchemaConfigBuilder<SchemaConfig> builder = SchemaConfig.builder()
            .subject(catalog.subject)
            .version(catalog.version);

        if (catalog.overlay != null)
        {
            builder.overlay(OverlayConfig.builder()
                .name(catalog.overlay.name)
                .schema(asOpenapiSchemaConfig(catalog.overlay))
                .build());
        }

        return builder.build();
    }

    private AsyncapiCatalogConfig asAsyncapiCatalogConfig(
        String name,
        JsonObject catalogObject)
    {
        return asAsyncapiCatalogConfig(name, schema.adaptFromJson(catalogObject));
    }

    private AsyncapiCatalogConfig asAsyncapiCatalogConfig(
        String name,
        SchemaConfig parsed)
    {
        AsyncapiCatalogConfigBuilder<AsyncapiCatalogConfig> builder = AsyncapiCatalogConfig.builder()
            .name(name)
            .subject(parsed.subject)
            .version(parsed.version);

        if (parsed.overlay != null)
        {
            builder.overlay(asAsyncapiCatalogConfig(parsed.overlay.name, parsed.overlay.schema));
        }

        return builder.build();
    }

    private SchemaConfig asAsyncapiSchemaConfig(
        AsyncapiCatalogConfig catalog)
    {
        SchemaConfigBuilder<SchemaConfig> builder = SchemaConfig.builder()
            .subject(catalog.subject)
            .version(catalog.version);

        if (catalog.overlay != null)
        {
            builder.overlay(OverlayConfig.builder()
                .name(catalog.overlay.name)
                .schema(asAsyncapiSchemaConfig(catalog.overlay))
                .build());
        }

        return builder.build();
    }
}
