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
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiChannelsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiChannelsConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiMqttKafkaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiMqttKafkaConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiServerConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class AsyncapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String SERVERS_NAME = "servers";
    private static final String SERVER_HOST_NAME = "host";
    private static final String SERVER_URL_NAME = "url";
    private static final String SERVER_PATHNAME_NAME = "pathname";
    private static final String TCP_NAME = "tcp";
    private static final String TLS_NAME = "tls";
    private static final String HTTP_NAME = "http";
    private static final String MQTT_NAME = "mqtt";
    private static final String KAFKA_NAME = "kafka";
    private static final String MQTT_KAFKA_NAME = "mqtt-kafka";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String CHANNELS_NAME = "channels";
    private static final String SESSIONS_NAME = "sessions";
    private static final String MESSAGES_NAME = "messages";
    private static final String RETAINED_NAME = "retained";

    private OptionsConfigAdapter tcpOptions;
    private OptionsConfigAdapter tlsOptions;
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
        AsyncapiOptionsConfig asyncapiOptions = (AsyncapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (asyncapiOptions.specs != null)
        {
            final JsonObjectBuilder specs = Json.createObjectBuilder();
            for (AsyncapiSpecificationConfig asyncapiConfig : asyncapiOptions.specs)
            {
                final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
                final JsonArrayBuilder servers = Json.createArrayBuilder();
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

                if (asyncapiConfig.servers != null)
                {
                    asyncapiConfig.servers.forEach(s ->
                    {
                        JsonObjectBuilder server = Json.createObjectBuilder();
                        if (!s.host.isEmpty())
                        {
                            server.add(SERVER_HOST_NAME, s.host);
                        }
                        if (!s.url.isEmpty())
                        {
                            server.add(SERVER_URL_NAME, s.url);
                        }
                        if (!s.pathname.isEmpty())
                        {
                            server.add(SERVER_PATHNAME_NAME, s.pathname);
                        }
                        servers.add(server);
                    });
                }
                catalogObject.add(SERVERS_NAME, servers);

                specs.add(asyncapiConfig.label, catalogObject);
            }
            object.add(SPECS_NAME, specs);
        }

        if (asyncapiOptions.tcp != null)
        {
            final TcpOptionsConfig tcp = asyncapiOptions.tcp;
            object.add(TCP_NAME, tcpOptions.adaptToJson(tcp));
        }

        if (asyncapiOptions.tls != null)
        {
            final TlsOptionsConfig tls = asyncapiOptions.tls;
            object.add(TLS_NAME, tlsOptions.adaptToJson(tls));
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

        if (asyncapiOptions.mqttKafka != null)
        {
            AsyncapiMqttKafkaConfig mqttKafka = asyncapiOptions.mqttKafka;
            JsonObjectBuilder newMqttKafka = Json.createObjectBuilder();
            AsyncapiChannelsConfig channels = mqttKafka.channels;
            if (channels != null)
            {
                JsonObjectBuilder newChannels = Json.createObjectBuilder();
                String sessions = channels.sessions;
                if (sessions != null)
                {
                    newChannels.add(SESSIONS_NAME, sessions);
                }

                String messages = channels.messages;
                if (messages != null)
                {
                    newChannels.add(MESSAGES_NAME, messages);
                }

                String retained = channels.retained;
                if (retained != null)
                {
                    newChannels.add(RETAINED_NAME, retained);
                }
                newMqttKafka.add(CHANNELS_NAME, newChannels);

                object.add(MQTT_KAFKA_NAME, newMqttKafka);
            }
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
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

                final JsonArray servers = spec.getJsonArray(SERVERS_NAME);
                if (servers != null)
                {
                    for (JsonValue server : servers)
                    {
                        final AsyncapiServerConfigBuilder<?> serverBuilder = specBuilder.server();
                        final JsonObject serverObject = server.asJsonObject();

                        if (serverObject.containsKey(SERVER_HOST_NAME))
                        {
                            serverBuilder.host(serverObject.getString(SERVER_HOST_NAME));
                        }

                        if (serverObject.containsKey(SERVER_URL_NAME))
                        {
                            serverBuilder.url(serverObject.getString(SERVER_URL_NAME));
                        }

                        if (serverObject.containsKey(SERVER_PATHNAME_NAME))
                        {
                            serverBuilder.pathname(serverObject.getString(SERVER_PATHNAME_NAME));
                        }

                        serverBuilder.build();
                    }
                }

                specBuilder.build();
            }
        }

        if (object.containsKey(TCP_NAME))
        {
            final JsonObject tcp = object.getJsonObject(TCP_NAME);
            builder.tcp(TcpOptionsConfig.class.cast(tcpOptions.adaptFromJson(tcp)));
        }

        if (object.containsKey(TLS_NAME))
        {
            final JsonObject tls = object.getJsonObject(TLS_NAME);
            builder.tls(TlsOptionsConfig.class.cast(tlsOptions.adaptFromJson(tls)));
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

        if (object.containsKey(MQTT_KAFKA_NAME))
        {
            final AsyncapiMqttKafkaConfigBuilder<?> mqttKafkaBuilder = builder.mqttKafka();
            final JsonObject mqttKafka = object.getJsonObject(MQTT_KAFKA_NAME);

            if (mqttKafka.containsKey(CHANNELS_NAME))
            {
                AsyncapiChannelsConfigBuilder<?> channelsBuilder = mqttKafkaBuilder.channels();
                JsonObject channels = mqttKafka.getJsonObject(CHANNELS_NAME);

                if (channels.containsKey(SESSIONS_NAME))
                {
                    channelsBuilder.sessions(channels.getString(SESSIONS_NAME));
                }
                if (channels.containsKey(MESSAGES_NAME))
                {
                    channelsBuilder.messages(channels.getString(MESSAGES_NAME));
                }
                if (channels.containsKey(RETAINED_NAME))
                {
                    channelsBuilder.retained(channels.getString(RETAINED_NAME));
                }

                channelsBuilder.build();
            }

            mqttKafkaBuilder.build();
        }

        return builder.build();
    }

    @Override
    public void adaptContext(
        ConfigAdapterContext context)
    {
        this.tcpOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tcpOptions.adaptType("tcp");
        this.tlsOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tlsOptions.adaptType("tls");
        this.httpOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.httpOptions.adaptType("http");
        this.mqttOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.mqttOptions.adaptType("mqtt");
        this.kafkaOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.kafkaOptions.adaptType("kafka");
    }
}
