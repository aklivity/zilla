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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class McpKafkaOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpKafkaOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String yaml =
                """
                servers:
                  - localhost:9092
                  - localhost:9093
                """;

        McpKafkaOptionsConfig options = jsonb.fromJson(yaml, McpKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.servers.size(), equalTo(2));
        assertThat(options.servers.get(0).host, equalTo("localhost"));
        assertThat(options.servers.get(0).port, equalTo(9092));
        assertThat(options.servers.get(1).host, equalTo("localhost"));
        assertThat(options.servers.get(1).port, equalTo(9093));
    }

    @Test
    public void shouldWriteOptions()
    {
        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                servers:
                  - "localhost:9092"
                """));
    }

    @Test
    public void shouldReadAuthorizationOptions()
    {
        String yaml = """
                servers:
                  - localhost:9092
                authorization:
                  guard0:
                    credentials:
                      mechanism: scram-sha-512
                      username: "{identity}"
                      password: "{credentials}"
                """;

        McpKafkaOptionsConfig options = jsonb.fromJson(yaml, McpKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("guard0"));
        assertThat(options.authorization.credentials.mechanism, equalTo("scram-sha-512"));
        assertThat(options.authorization.credentials.username, equalTo("{identity}"));
        assertThat(options.authorization.credentials.password, equalTo("{credentials}"));
    }

    @Test
    public void shouldWriteAuthorizationOptions()
    {
        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .authorization()
                .name("guard0")
                .credentials()
                    .mechanism("scram-sha-512")
                    .username("{identity}")
                    .password("{credentials}")
                    .build()
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                servers:
                  - "localhost:9092"
                authorization:
                  guard0:
                    credentials:
                      mechanism: scram-sha-512
                      username: "{identity}"
                      password: "{credentials}"
                """));
    }

    @Test
    public void shouldReadTopics()
    {
        String yaml = """
                servers:
                  - localhost:9092
                topics:
                  - name: orders
                    value:
                      model: test
                """;

        McpKafkaOptionsConfig options = jsonb.fromJson(yaml, McpKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.topics.size(), equalTo(1));
        assertThat(options.topics.get(0).name, equalTo("orders"));
        assertThat(options.topics.get(0).value.model, equalTo("test"));
    }

    @Test
    public void shouldWriteTopics()
    {
        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .topic()
                .name("orders")
                .value(TestModelConfig.builder().build())
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                servers:
                  - "localhost:9092"
                topics:
                  - name: orders
                    value: test
                """));
    }
}
