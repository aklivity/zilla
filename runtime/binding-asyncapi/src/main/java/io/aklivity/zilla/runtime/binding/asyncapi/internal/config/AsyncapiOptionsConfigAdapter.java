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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class AsyncapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String SERVERS_NAME = "servers";
    private static final String HTTP_NAME = "http";
    private static final String MQTT_NAME = "mqtt";
    private static final String KAFKA_NAME = "kafka";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String SECURITY_NAME = "security";
    private static final String STORE_NAME = "store";
    private static final String OVERLAY_NAME = "overlay";

    private OptionsConfigAdapter httpOptions;
    private OptionsConfigAdapter mqttOptions;
    private OptionsConfigAdapter kafkaOptions;

    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return AsyncapiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        ensureNestedOptions();

        AsyncapiOptionsConfig asyncapiOptions = (AsyncapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (asyncapiOptions.specs != null)
        {
            final JsonObjectBuilder specs = Json.createObjectBuilder();
            for (AsyncapiSpecificationConfig asyncapiConfig : asyncapiOptions.specs)
            {
                final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
                final JsonObjectBuilder subjectObject = Json.createObjectBuilder();

                if (asyncapiConfig.servers != null && !asyncapiConfig.servers.isEmpty())
                {
                    final JsonArrayBuilder servers = Json.createArrayBuilder();
                    asyncapiConfig.servers.forEach(servers::add);
                    catalogObject.add(SERVERS_NAME, servers);
                }

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

                if (asyncapiConfig.overlay != null)
                {
                    final JsonObjectBuilder overlaySchema = Json.createObjectBuilder();
                    overlaySchema.add(SUBJECT_NAME, asyncapiConfig.overlay.subject);
                    if (asyncapiConfig.overlay.version != null)
                    {
                        overlaySchema.add(VERSION_NAME, asyncapiConfig.overlay.version);
                    }

                    final JsonObjectBuilder overlaySubject = Json.createObjectBuilder();
                    overlaySubject.add(asyncapiConfig.overlay.name, overlaySchema);
                    catalogObject.add(OVERLAY_NAME, overlaySubject);
                }

                if (asyncapiConfig.security != null && !asyncapiConfig.security.isEmpty())
                {
                    final JsonObjectBuilder security = Json.createObjectBuilder();
                    asyncapiConfig.security.forEach(security::add);
                    catalogObject.add(SECURITY_NAME, security);
                }

                if (asyncapiConfig.store != null)
                {
                    catalogObject.add(STORE_NAME, asyncapiConfig.store);
                }

                specs.add(asyncapiConfig.label, catalogObject);
            }
            object.add(SPECS_NAME, specs);
        }

        if (asyncapiOptions.http != null)
        {
            final HttpOptionsConfig http = asyncapiOptions.http;
            object.add(HTTP_NAME, httpOptions.adaptToJson(http));
        }

        if (asyncapiOptions.mqtt != null)
        {
            final MqttOptionsConfig mqtt = asyncapiOptions.mqtt;
            object.add(MQTT_NAME, mqttOptions.adaptToJson(mqtt));
        }

        if (asyncapiOptions.kafka != null)
        {
            final KafkaOptionsConfig kafka = asyncapiOptions.kafka;
            object.add(KAFKA_NAME, kafkaOptions.adaptToJson(kafka));
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        ensureNestedOptions();

        final AsyncapiOptionsConfigBuilder<AsyncapiOptionsConfig> builder = AsyncapiOptionsConfig.builder();

        if (object.containsKey(SPECS_NAME))
        {
            final JsonObject specs = object.getJsonObject(SPECS_NAME);

            for (Map.Entry<String, JsonValue> entry : specs.entrySet())
            {
                final AsyncapiSpecificationConfigBuilder<?> specBuilder = builder.spec();

                final String label = entry.getKey();
                specBuilder.label(label);

                final JsonObject spec = entry.getValue().asJsonObject();

                if (spec.containsKey(SERVERS_NAME))
                {
                    for (JsonValue serverValue : spec.getJsonArray(SERVERS_NAME))
                    {
                        specBuilder.serverOverride(((JsonString) serverValue).getString());
                    }
                }

                if (spec.containsKey(CATALOG_NAME))
                {
                    final JsonObject catalogs = spec.getJsonObject(CATALOG_NAME);

                    for (Map.Entry<String, JsonValue> catalogEntry : catalogs.entrySet())
                    {
                        final AsyncapiCatalogConfigBuilder<?> catalogBuilder = specBuilder.catalog();

                        final String catalogName = catalogEntry.getKey();
                        JsonObject catalogObject = catalogEntry.getValue().asJsonObject();

                        catalogBuilder.name(catalogName);

                        if (catalogObject.containsKey(SUBJECT_NAME))
                        {
                            catalogBuilder.subject(catalogObject.getString(SUBJECT_NAME));
                        }

                        if (catalogObject.containsKey(VERSION_NAME))
                        {
                            catalogBuilder.version(catalogObject.getString(VERSION_NAME));
                        }

                        catalogBuilder.build();
                    }
                }

                if (spec.containsKey(SECURITY_NAME))
                {
                    final JsonObject securityObject = spec.getJsonObject(SECURITY_NAME);
                    for (String scheme : securityObject.keySet())
                    {
                        specBuilder.security(scheme, securityObject.getString(scheme));
                    }
                }

                if (spec.containsKey(STORE_NAME))
                {
                    specBuilder.store(spec.getString(STORE_NAME));
                }

                if (spec.containsKey(OVERLAY_NAME))
                {
                    final JsonObject overlayObject = spec.getJsonObject(OVERLAY_NAME);
                    final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
                    final JsonObject overlaySchemaObject = overlayEntry.getValue().asJsonObject();

                    final AsyncapiCatalogConfigBuilder<?> overlayBuilder = specBuilder.overlay();
                    overlayBuilder.name(overlayEntry.getKey());

                    if (overlaySchemaObject.containsKey(SUBJECT_NAME))
                    {
                        overlayBuilder.subject(overlaySchemaObject.getString(SUBJECT_NAME));
                    }

                    if (overlaySchemaObject.containsKey(VERSION_NAME))
                    {
                        overlayBuilder.version(overlaySchemaObject.getString(VERSION_NAME));
                    }

                    overlayBuilder.build();
                }

                specBuilder.build();
            }
        }

        if (object.containsKey(HTTP_NAME))
        {
            final JsonObject http = object.getJsonObject(HTTP_NAME);
            builder.http(HttpOptionsConfig.class.cast(httpOptions.adaptFromJson(http)));
        }

        if (object.containsKey(MQTT_NAME))
        {
            final JsonObject mqtt = object.getJsonObject(MQTT_NAME);
            builder.mqtt(MqttOptionsConfig.class.cast(mqttOptions.adaptFromJson(mqtt)));
        }

        if (object.containsKey(KAFKA_NAME))
        {
            final JsonObject kafka = object.getJsonObject(KAFKA_NAME);
            builder.kafka(KafkaOptionsConfig.class.cast(kafkaOptions.adaptFromJson(kafka)));
        }

        return builder.build();
    }

    private void ensureNestedOptions()
    {
        if (httpOptions == null)
        {
            httpOptions = new OptionsConfigAdapter(Kind.BINDING);
            httpOptions.adaptType("http");
            mqttOptions = new OptionsConfigAdapter(Kind.BINDING);
            mqttOptions.adaptType("mqtt");
            kafkaOptions = new OptionsConfigAdapter(Kind.BINDING);
            kafkaOptions.adaptType("kafka");
        }
    }
}
