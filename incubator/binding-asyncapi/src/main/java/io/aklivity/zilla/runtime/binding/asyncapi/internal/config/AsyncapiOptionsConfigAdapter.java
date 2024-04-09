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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static java.util.stream.Collectors.toList;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
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
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiMqttKafkaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiMqttKafkaConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiParser;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiServerConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiServerConfigBuilder;
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
    private static final String LOCATION_NAME = "location";
    private static final String SERVERS_NAME = "servers";
    private static final String SERVER_NAME_NAME = "name";
    private static final String SERVER_HOST_NAME = "host";
    private static final String SERVER_URL_NAME = "url";
    private static final String SERVER_PATHNAME_NAME = "pathname";
    private static final String TCP_NAME = "tcp";
    private static final String TLS_NAME = "tls";
    private static final String HTTP_NAME = "http";
    private static final String MQTT_NAME = "mqtt";
    private static final String KAFKA_NAME = "kafka";
    private static final String MQTT_KAFKA_NAME = "mqtt-kafka";
    private static final String SPECS_NAME = "specs";
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

    public AsyncapiOptionsConfigAdapter()
    {
    }

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
            JsonObjectBuilder specs = Json.createObjectBuilder();
            asyncapiOptions.specs.forEach(p ->
            {
                JsonObjectBuilder spec = Json.createObjectBuilder();
                spec.add(LOCATION_NAME, p.location);
                if (p.servers != null)
                {
                    JsonArrayBuilder servers = Json.createArrayBuilder();
                    p.servers.forEach(s ->
                    {
                        JsonObjectBuilder server = Json.createObjectBuilder();
                        if (s.name != null)
                        {
                            server.add(SERVER_NAME_NAME, s.name);
                        }
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
                    spec.add(SERVERS_NAME, servers);
                }

                specs.add(p.apiLabel, spec);
            });
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

        if (asyncapiOptions.asyncapis != null)
        {
            final JsonObjectBuilder specs = Json.createObjectBuilder();
            for (AsyncapiConfig asyncapiConfig : asyncapiOptions.asyncapis)
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

                specs.add(asyncapiConfig.apiLabel, catalogObject);
            }
            object.add(SPECS_NAME, specs);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        final AsyncapiOptionsConfigBuilder<AsyncapiOptionsConfig> asyncapiOptions = AsyncapiOptionsConfig.builder();

        if (object.containsKey(TCP_NAME))
        {
            final JsonObject tcp = object.getJsonObject(TCP_NAME);
            final TcpOptionsConfig tcpOptions = (TcpOptionsConfig) this.tcpOptions.adaptFromJson(tcp);
            asyncapiOptions.tcp(tcpOptions);
        }

        if (object.containsKey(TLS_NAME))
        {
            final JsonObject tls = object.getJsonObject(TLS_NAME);
            final TlsOptionsConfig tlsOptions = (TlsOptionsConfig) this.tlsOptions.adaptFromJson(tls);
            asyncapiOptions.tls(tlsOptions);
        }

        if (object.containsKey(HTTP_NAME))
        {
            final JsonObject http = object.getJsonObject(HTTP_NAME);
            final HttpOptionsConfig httpOptions = (HttpOptionsConfig) this.httpOptions.adaptFromJson(http);
            asyncapiOptions.http(httpOptions);
        }

        if (object.containsKey(MQTT_NAME))
        {
            final JsonObject mqtt = object.getJsonObject(MQTT_NAME);
            final MqttOptionsConfig mqttOptions = (MqttOptionsConfig) this.mqttOptions.adaptFromJson(mqtt);
            asyncapiOptions.mqtt(mqttOptions);
        }

        if (object.containsKey(KAFKA_NAME))
        {
            final JsonObject kafka = object.getJsonObject(KAFKA_NAME);
            final KafkaOptionsConfig kafkaOptions = (KafkaOptionsConfig) this.kafkaOptions.adaptFromJson(kafka);
            asyncapiOptions.kafka(kafkaOptions);
        }

        if (object.containsKey(MQTT_KAFKA_NAME))
        {
            AsyncapiMqttKafkaConfigBuilder<AsyncapiMqttKafkaConfig> mqttKafkaBuilder = AsyncapiMqttKafkaConfig.builder();
            final JsonObject mqttKafka = object.getJsonObject(MQTT_KAFKA_NAME);
            if (mqttKafka.containsKey(CHANNELS_NAME))
            {
                AsyncapiChannelsConfigBuilder<AsyncapiChannelsConfig> channelsBuilder = AsyncapiChannelsConfig.builder();
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
                asyncapiOptions.mqttKafka(mqttKafkaBuilder.channels(channelsBuilder.build()).build());
            }
        }

        if (object.containsKey(SPECS_NAME))
        {
            JsonObject asyncapi = object.getJsonObject(SPECS_NAME);
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
                    asyncapiOptions.asyncapi(new AsyncapiConfig(apiLabel, catalogs));
                }
            }
        }
        return asyncapiOptions.build();
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

    private List<AsyncapiConfig> asListAsyncapis(
        JsonObject array)
    {
        return array.entrySet().stream()
            .map(this::asAsyncapi)
            .collect(toList());
    }

    private AsyncapiConfig asAsyncapi(
        Map.Entry<String, JsonValue> entry)
    {
        final String apiLabel = entry.getKey();

        final JsonObject specJson = (JsonObject) entry.getValue();

        final String location = specJson.getString(LOCATION_NAME);
        final JsonArray serversJson = specJson.getJsonArray(SERVERS_NAME);

        final List<AsyncapiServerConfig> servers = new LinkedList<>();

        if (serversJson != null)
        {
            serversJson.forEach(s ->
            {
                JsonObject serverObject = s.asJsonObject();
                AsyncapiServerConfigBuilder<AsyncapiServerConfig> serverBuilder = AsyncapiServerConfig.builder();
                if (serverObject.containsKey(SERVER_NAME_NAME))
                {
                    serverBuilder.name(serverObject.getString(SERVER_NAME_NAME));
                }

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
                servers.add(serverBuilder.build());
            });
        }


        final String specText = readURL.apply(location);
        crc.reset();
        crc.update(specText.getBytes(StandardCharsets.UTF_8));
        final long apiId = crc.getValue();
        Asyncapi asyncapi = parser.parse(specText);

        return new AsyncapiConfig(apiLabel, apiId, location, servers, asyncapi);
    }

}
