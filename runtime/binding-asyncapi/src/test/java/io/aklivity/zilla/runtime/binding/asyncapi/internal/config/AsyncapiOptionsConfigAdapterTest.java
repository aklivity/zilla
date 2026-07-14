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
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class AsyncapiOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING);
        adapter.adaptType("asyncapi");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptionsMqtt() throws IOException
    {
        String yaml =
                """
                specs:
                  mqtt-api:
                    catalog:
                      catalog0:
                        subject: smartylighting
                        version: latest
                    servers:
                      - test.mosquitto.org:1883
                tls:
                  keys:
                    - localhost
                  trust:
                    - serverca
                  trustcacerts: true
                  sni:
                    - mqtt.example.net
                  alpn:
                    - mqtt
                """;

        AsyncapiOptionsConfig options = jsonb.fromJson(yaml, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        AsyncapiSpecificationConfig asyncapi = options.specs.get(0);
        assertThat(asyncapi.servers, equalTo(asList("test.mosquitto.org:1883")));
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
                .serverOverride("test.mosquitto.org:1883")
                .build()
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

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
            """
            specs:
              mqtt-api:
                servers:
                  - "test.mosquitto.org:1883"
                catalog:
                  catalog0:
                    subject: smartylighting
                    version: latest
            tls:
              keys:
                - localhost
              trust:
                - serverca
              trustcacerts: true
              sni:
                - mqtt.example.net
              alpn:
                - mqtt
            kafka:
              sasl:
                mechanism: plain
                username: username
                password: password
            """));
    }

    @Test
    public void shouldReadOptionsWithOverlay() throws IOException
    {
        String yaml =
                """
                specs:
                  mqtt-api:
                    catalog:
                      catalog0:
                        subject: smartylighting
                        version: latest
                    overlay:
                      catalog0:
                        subject: smartylighting-overlay
                        version: latest
                """;

        AsyncapiOptionsConfig options = jsonb.fromJson(yaml, AsyncapiOptionsConfig.class);

        AsyncapiSpecificationConfig spec = options.specs.get(0);
        assertThat(spec.overlay, not(nullValue()));
        assertThat(spec.overlay.name, equalTo("catalog0"));
        assertThat(spec.overlay.subject, equalTo("smartylighting-overlay"));
        assertThat(spec.overlay.version, equalTo("latest"));
    }

    @Test
    public void shouldWriteOptionsWithOverlay() throws IOException
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
                .overlay()
                    .name("catalog0")
                    .subject("smartylighting-overlay")
                    .version("latest")
                    .build()
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
            """
            specs:
              mqtt-api:
                catalog:
                  catalog0:
                    subject: smartylighting
                    version: latest
                overlay:
                  catalog0:
                    subject: smartylighting-overlay
                    version: latest
            """));
    }

    @Test
    public void shouldReadOptionsKafka() throws IOException
    {
        String yaml =
                """
                specs:
                  kafka_api:
                    catalog:
                      catalog0:
                        subject: smartylighting
                        version: latest
                tls:
                  keys:
                    - localhost
                  trust:
                    - serverca
                  trustcacerts: true
                  sni:
                    - kafka.example.net
                  alpn:
                    - kafka
                kafka:
                  sasl:
                    mechanism: plain
                    username: username
                    password: password
                """;

        AsyncapiOptionsConfig options = jsonb.fromJson(yaml, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
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
            .tls(TlsOptionsConfig.builder()
                .keys(asList("localhost"))
                .trust(asList("serverca"))
                .sni(asList("http.example.net"))
                .alpn(asList("http"))
                .trustcacerts(true)
                .build())
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
            """
            tls:
              keys:
                - localhost
              trust:
                - serverca
              trustcacerts: true
              sni:
                - http.example.net
              alpn:
                - http
            """));
    }

    @Test
    public void shouldReadOptionsHttp() throws IOException
    {
        String yaml =
                """
                tls:
                  keys:
                    - localhost
                  trust:
                    - serverca
                  trustcacerts: true
                  sni:
                    - http.example.net
                  alpn:
                    - http
                """;

        AsyncapiOptionsConfig options = jsonb.fromJson(yaml, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
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

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
            """
            tls:
              keys:
                - localhost
              trust:
                - serverca
              trustcacerts: true
              sni:
                - kafka.example.net
              alpn:
                - kafka
            kafka:
              sasl:
                mechanism: plain
                username: username
                password: password
            """));
    }
}
