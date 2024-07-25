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

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.util.function.Function;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiChannelsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiMqttKafkaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiServerConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class AsyncapiOptionsConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private ConfigAdapterContext context;
    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
        adapter.adaptType("asyncapi");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptionsMqtt() throws IOException
    {
        String text =
                "{" +
                    "\"specs\":" +
                    "{" +
                        "\"mqtt-api\":" +
                        "{" +
                            "\"catalog\":" +
                            "{" +
                                "\"catalog0\":" +
                                "{" +
                                    "\"subject\": \"smartylighting\"," +
                                    "\"version\": \"latest\"" +
                                "}" +
                            "}," +
                            "\"servers\":" +
                            "[" +
                                "{" +
                                    "\"host\":\"test.mosquitto.org:1883\"" +
                                "}" +
                            "]" +
                        "}" +
                    "}," +
                    "\"tcp\":" +
                    "{" +
                        "\"host\":\"localhost\"," +
                        "\"port\":7183" +
                    "}," +
                    "\"tls\":" +
                    "{" +
                        "\"keys\":" +
                        "[" +
                            "\"localhost\"" +
                        "]," +
                        "\"trust\":" +
                        "[" +
                            "\"serverca\"" +
                        "]," +
                        "\"trustcacerts\":true," +
                        "\"sni\":" +
                        "[" +
                            "\"mqtt.example.net\"" +
                        "]," +
                        "\"alpn\":" +
                        "[" +
                            "\"mqtt\"" +
                        "]" +
                    "}" +
                "}";

        AsyncapiOptionsConfig options = jsonb.fromJson(text, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        AsyncapiSpecificationConfig asyncapi = options.specs.get(0);
        assertThat(asyncapi.servers.size(), equalTo(1));
        AsyncapiServerConfig server = asyncapi.servers.get(0);
        assertThat(server.host, equalTo("test.mosquitto.org:1883"));
        assertThat(options.tcp.host, equalTo("localhost"));
        assertThat(options.tcp.ports, equalTo(new int[] { 7183 }));
        assertThat(options.tls.keys, equalTo(asList("localhost")));
        assertThat(options.tls.trust, equalTo(asList("serverca")));
        assertThat(options.tls.trustcacerts, equalTo(true));
        assertThat(options.tls.sni, equalTo(asList("mqtt.example.net")));
        assertThat(options.tls.alpn, equalTo(asList("mqtt")));
    }

    @Test
    public void shouldWriteOptionsMqtt() throws IOException
    {
        AsyncapiOptionsConfig options = AsyncapiOptionsConfig.builder()
            .inject(Function.identity())
            .spec()
                .label("mqtt-api")
                .catalog()
                    .name("catalog0")
                    .subject("smartylighting")
                    .version("latest")
                    .build()
                .server()
                    .host("test.mosquitto.org:1883")
                    .build()
                .build()
            .tcp(TcpOptionsConfig.builder()
                .host("localhost")
                .ports(new int[] { 7183 })
                .build())
            .tls(TlsOptionsConfig.builder()
                .keys(asList("localhost"))
                .trust(asList("serverca"))
                .sni(asList("mqtt.example.net"))
                .alpn(asList("mqtt"))
                .trustcacerts(true)
                .build())
            .kafka(KafkaOptionsConfig.builder()
                .sasl(KafkaSaslConfig.builder()
                    .mechanism("plain")
                    .username("username")
                    .password("password")
                    .build())
                .build())
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                "\"specs\":" +
                "{" +
                    "\"mqtt-api\":" +
                    "{" +
                        "\"catalog\":" +
                        "{" +
                            "\"catalog0\":" +
                            "{" +
                                "\"subject\":\"smartylighting\"," +
                                "\"version\":\"latest\"" +
                            "}" +
                        "}," +
                        "\"servers\":" +
                        "[" +
                            "{" +
                                "\"host\":\"test.mosquitto.org:1883\"" +
                            "}" +
                        "]" +
                    "}" +
                "}," +
                "\"tcp\":" +
                "{" +
                    "\"host\":\"localhost\"," +
                    "\"port\":7183" +
                "}," +
                "\"tls\":" +
                "{" +
                    "\"keys\":" +
                    "[" +
                        "\"localhost\"" +
                    "]," +
                    "\"trust\":" +
                    "[" +
                        "\"serverca\"" +
                    "]," +
                    "\"trustcacerts\":true," +
                    "\"sni\":" +
                    "[" +
                        "\"mqtt.example.net\"" +
                    "]," +
                    "\"alpn\":" +
                    "[" +
                        "\"mqtt\"" +
                    "]" +
                "}," +
                 "\"kafka\":" +
                 "{" +
                     "\"sasl\":" +
                     "{" +
                         "\"mechanism\":\"plain\"," +
                         "\"username\":\"username\"," +
                         "\"password\":\"password\"" +
                     "}" +
                 "}," +
                "\"mqtt-kafka\":" +
                "{" +
                    "\"channels\":" +
                    "{" +
                        "\"sessions\":\"mqttSessions\"," +
                        "\"messages\":\"mqttMessages\"," +
                        "\"retained\":\"mqttRetained\"" +
                    "}" +
                "}" +
            "}"));
    }

    @Test
    public void shouldReadOptionsKafka() throws IOException
    {
        String text =
                "{" +
                    "\"specs\": {" +
                    "  \"kafka_api\": {" +
                    "    \"catalog\": {" +
                    "      \"catalog0\": {" +
                    "        \"subject\": \"smartylighting\"," +
                    "        \"version\": \"latest\"" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "}," +
                    "\"tcp\":" +
                    "{" +
                        "\"host\":\"localhost\"," +
                        "\"port\":9092" +
                    "}," +
                    "\"tls\":" +
                    "{" +
                        "\"keys\":" +
                        "[" +
                            "\"localhost\"" +
                        "]," +
                        "\"trust\":" +
                        "[" +
                            "\"serverca\"" +
                        "]," +
                        "\"trustcacerts\":true," +
                        "\"sni\":" +
                        "[" +
                            "\"kafka.example.net\"" +
                        "]," +
                        "\"alpn\":" +
                        "[" +
                            "\"kafka\"" +
                        "]" +
                    "}," +
                    "\"kafka\":" +
                    "{" +
                        "\"sasl\":" +
                        "{" +
                            "\"mechanism\":\"plain\"," +
                            "\"username\":\"username\"," +
                            "\"password\":\"password\"" +
                        "}" +
                    "}" +
                "}";

        AsyncapiOptionsConfig options = jsonb.fromJson(text, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.tcp.host, equalTo("localhost"));
        assertThat(options.tcp.ports, equalTo(new int[] { 9092 }));
        assertThat(options.tls.keys, equalTo(asList("localhost")));
        assertThat(options.tls.trust, equalTo(asList("serverca")));
        assertThat(options.tls.trustcacerts, equalTo(true));
        assertThat(options.tls.sni, equalTo(asList("kafka.example.net")));
        assertThat(options.tls.alpn, equalTo(asList("kafka")));
        assertThat(options.kafka.sasl.mechanism, equalTo("plain"));
        assertThat(options.kafka.sasl.username, equalTo("username"));
        assertThat(options.kafka.sasl.password, equalTo("password"));
    }

    @Test
    public void shouldWriteOptionsHttp() throws IOException
    {
        AsyncapiOptionsConfig options = AsyncapiOptionsConfig.builder()
            .inject(Function.identity())
            .tcp(TcpOptionsConfig.builder()
                .host("localhost")
                .ports(new int[] { 7080 })
                .build())
            .tls(TlsOptionsConfig.builder()
                .keys(asList("localhost"))
                .trust(asList("serverca"))
                .sni(asList("http.example.net"))
                .alpn(asList("http"))
                .trustcacerts(true)
                .build())
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                "\"tcp\":" +
                "{" +
                    "\"host\":\"localhost\"," +
                    "\"port\":7080" +
                "}," +
                "\"tls\":" +
                "{" +
                    "\"keys\":" +
                    "[" +
                        "\"localhost\"" +
                    "]," +
                    "\"trust\":" +
                    "[" +
                        "\"serverca\"" +
                    "]," +
                    "\"trustcacerts\":true," +
                    "\"sni\":" +
                    "[" +
                        "\"http.example.net\"" +
                    "]," +
                    "\"alpn\":" +
                    "[" +
                        "\"http\"" +
                    "]" +
                "}," +
                "\"mqtt-kafka\":" +
                "{" +
                    "\"channels\":" +
                    "{" +
                        "\"sessions\":\"mqttSessions\"," +
                        "\"messages\":\"mqttMessages\"," +
                        "\"retained\":\"mqttRetained\"" +
                    "}" +
                "}" +
            "}"));
    }

    @Test
    public void shouldReadOptionsHttp() throws IOException
    {
        String text =
                "{" +
                    "\"tcp\":" +
                    "{" +
                        "\"host\":\"localhost\"," +
                        "\"port\":7080" +
                    "}," +
                    "\"tls\":" +
                    "{" +
                        "\"keys\":" +
                        "[" +
                            "\"localhost\"" +
                        "]," +
                        "\"trust\":" +
                        "[" +
                            "\"serverca\"" +
                        "]," +
                        "\"trustcacerts\":true," +
                        "\"sni\":" +
                        "[" +
                            "\"http.example.net\"" +
                        "]," +
                        "\"alpn\":" +
                        "[" +
                            "\"http\"" +
                        "]" +
                    "}" +
                "}";

        AsyncapiOptionsConfig options = jsonb.fromJson(text, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.tcp.host, equalTo("localhost"));
        assertThat(options.tcp.ports, equalTo(new int[] { 7080 }));
        assertThat(options.tls.keys, equalTo(asList("localhost")));
        assertThat(options.tls.trust, equalTo(asList("serverca")));
        assertThat(options.tls.trustcacerts, equalTo(true));
        assertThat(options.tls.sni, equalTo(asList("http.example.net")));
        assertThat(options.tls.alpn, equalTo(asList("http")));
    }

    @Test
    public void shouldWriteOptionsKafka() throws IOException
    {
        AsyncapiOptionsConfig options = AsyncapiOptionsConfig.builder()
            .inject(Function.identity())
            .tcp(TcpOptionsConfig.builder()
                .host("localhost")
                .ports(new int[] { 9092 })
                .build())
            .tls(TlsOptionsConfig.builder()
                .keys(asList("localhost"))
                .trust(asList("serverca"))
                .sni(asList("kafka.example.net"))
                .alpn(asList("kafka"))
                .trustcacerts(true)
                .build())
            .kafka(KafkaOptionsConfig.builder()
                .sasl(KafkaSaslConfig.builder()
                    .mechanism("plain")
                    .username("username")
                    .password("password")
                    .build())
                .build())
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                "\"tcp\":" +
                "{" +
                    "\"host\":\"localhost\"," +
                    "\"port\":9092" +
                "}," +
                "\"tls\":" +
                "{" +
                    "\"keys\":" +
                    "[" +
                        "\"localhost\"" +
                    "]," +
                    "\"trust\":" +
                    "[" +
                        "\"serverca\"" +
                    "]," +
                    "\"trustcacerts\":true," +
                    "\"sni\":" +
                    "[" +
                        "\"kafka.example.net\"" +
                    "]," +
                    "\"alpn\":" +
                    "[" +
                        "\"kafka\"" +
                    "]" +
                "}," +
                 "\"kafka\":" +
                 "{" +
                     "\"sasl\":" +
                     "{" +
                         "\"mechanism\":\"plain\"," +
                         "\"username\":\"username\"," +
                         "\"password\":\"password\"" +
                     "}" +
                 "}," +
                "\"mqtt-kafka\":" +
                "{" +
                    "\"channels\":" +
                    "{" +
                        "\"sessions\":\"mqttSessions\"," +
                        "\"messages\":\"mqttMessages\"," +
                        "\"retained\":\"mqttRetained\"" +
                    "}" +
                "}" +
            "}"));
    }

    @Test
    public void shouldReadOptionsMqttKafka() throws IOException
    {
        String text =
                "{" +
                    "\"mqtt-kafka\":" +
                    "{" +
                        "\"channels\":" +
                        "{" +
                            "\"sessions\":\"sessionsChannel\"," +
                            "\"messages\":\"messagesChannel\"," +
                            "\"retained\":\"retainedChannel\"" +
                        "}" +
                    "}" +
                "}";

        AsyncapiOptionsConfig options = jsonb.fromJson(text, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.mqttKafka.channels.sessions, equalTo("sessionsChannel"));
        assertThat(options.mqttKafka.channels.messages, equalTo("messagesChannel"));
        assertThat(options.mqttKafka.channels.retained, equalTo("retainedChannel"));
    }

    @Test
    public void shouldWriteOptionsMqttKafka() throws IOException
    {
        AsyncapiOptionsConfig options = AsyncapiOptionsConfig.builder()
            .inject(Function.identity())
            .mqttKafka(AsyncapiMqttKafkaConfig.builder().channels(AsyncapiChannelsConfig.builder()
                    .sessions("sessionsChannel")
                    .messages("messagesChannel")
                    .retained("retainedChannel")
                    .build())
                .build())
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                    "\"mqtt-kafka\":" +
                    "{" +
                        "\"channels\":" +
                        "{" +
                            "\"sessions\":\"sessionsChannel\"," +
                            "\"messages\":\"messagesChannel\"," +
                            "\"retained\":\"retainedChannel\"" +
                        "}" +
                    "}" +
                "}"));
    }
}
