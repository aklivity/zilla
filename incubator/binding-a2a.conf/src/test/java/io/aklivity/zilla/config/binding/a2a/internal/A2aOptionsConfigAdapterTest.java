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
package io.aklivity.zilla.config.binding.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aKeywordSearchIndexConfig;
import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new A2aOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadServerOptions()
    {
        String text =
            """
            url: "https://gateway.example.com/agents"
            versions:
              - "1.0"
              - "0.3"
            push:
              url: "https://gateway.example.com/agents/push"
              path: /agents/push
            authorization:
              test0:
                credentials: "Bearer {credentials}"
            """;

        A2aOptionsConfig options = (A2aOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.url, equalTo("https://gateway.example.com/agents"));
        assertThat(options.versions, contains("1.0", "0.3"));
        assertThat(options.serverPush.url, equalTo("https://gateway.example.com/agents/push"));
        assertThat(options.serverPush.path, equalTo("/agents/push"));
        assertThat(options.authorization.name, equalTo("test0"));
        assertThat(options.authorization.credentials, equalTo("Bearer {credentials}"));
        assertThat(options.server, nullValue());
        assertThat(options.cache, nullValue());
        assertThat(options.discovery, nullValue());
        assertThat(options.proxyPush, nullValue());
    }

    @Test
    public void shouldWriteServerOptions()
    {
        A2aOptionsConfig options = A2aOptionsConfig.builder()
            .url("https://gateway.example.com/agents")
            .versions(List.of("1.0", "0.3"))
            .serverPush()
                .url("https://gateway.example.com/agents/push")
                .path("/agents/push")
                .build()
            .authorization()
                .name("test0")
                .credentials("Bearer {credentials}")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo(
            """
            url: "https://gateway.example.com/agents"
            versions:
              - "1.0"
              - "0.3"
            push:
              url: "https://gateway.example.com/agents/push"
              path: /agents/push
            authorization:
              test0:
                credentials: "Bearer {credentials}"
            """));
    }

    @Test
    public void shouldRoundTripServerOptions()
    {
        String text =
            """
            url: "http://localhost:8080/a2a"
            versions:
              - "1.0"
            authorization:
              test0:
                credentials: "Bearer {credentials}"
            """;

        OptionsConfig options = jsonb.fromJson(text, OptionsConfig.class);

        assertThat(jsonb.toJson(options), equalTo(text));
    }

    @Test
    public void shouldReadServerAuthorizationDefaultCredentials()
    {
        String text =
            """
            authorization:
              test0: {}
            """;

        A2aOptionsConfig options = (A2aOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options.authorization.name, equalTo("test0"));
        assertThat(options.authorization.credentials, equalTo("Bearer {credentials}"));
    }

    @Test
    public void shouldReadEmptyOptions()
    {
        A2aOptionsConfig options = (A2aOptionsConfig) jsonb.fromJson("{}", OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.url, nullValue());
        assertThat(options.versions, nullValue());
        assertThat(options.serverPush, nullValue());
        assertThat(options.authorization, nullValue());
        assertThat(options.server, nullValue());
        assertThat(options.cache, nullValue());
        assertThat(options.discovery, nullValue());
        assertThat(options.proxyPush, nullValue());
    }

    @Test
    public void shouldReadClientOptions()
    {
        String text =
            """
            server: "https://weather.example.com"
            versions:
              - "0.3"
            authorization:
              test0:
                credentials: "Bearer {credentials}"
            """;

        A2aOptionsConfig options = (A2aOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options.server, equalTo("https://weather.example.com"));
        assertThat(options.versions, contains("0.3"));
        assertThat(options.authorization.name, equalTo("test0"));
        assertThat(options.url, nullValue());
    }

    @Test
    public void shouldWriteClientOptions()
    {
        A2aOptionsConfig options = A2aOptionsConfig.builder()
            .server("https://weather.example.com")
            .authorization()
                .name("test0")
                .credentials("Bearer {credentials}")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo(
            """
            server: "https://weather.example.com"
            authorization:
              test0:
                credentials: "Bearer {credentials}"
            """));
    }

    @Test
    public void shouldRoundTripClientOptions()
    {
        String text =
            """
            server: "http://localhost:8080/a2a"
            versions:
              - "1.0"
              - "0.3"
            """;

        OptionsConfig options = jsonb.fromJson(text, OptionsConfig.class);

        assertThat(jsonb.toJson(options), equalTo(text));
    }

    @Test
    public void shouldReadProxyOptions()
    {
        String text =
            """
            cache:
              store: memory0
              ttl: PT10M
              authorization:
                test0:
                  credentials: test-token
            discovery:
              agent: discovery
              search:
                limit: 5
                fields:
                  - name
                  - description
                  - skills
                weights:
                  name: 3
                  skills: 2
                  description: 1
                type: keyword
            push:
              vault: secrets0
              key: a2a-push
              ttl: PT24H
              maxLength: 2048
              allow:
                - "https://*.example.com/hooks/*"
              exit: app2
              authorization:
                test1:
                  credentials: push-token
            """;

        A2aOptionsConfig options = (A2aOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options.cache.store, equalTo("memory0"));
        assertThat(options.cache.ttl, equalTo(Duration.ofMinutes(10)));
        assertThat(options.cache.authorization.name, equalTo("test0"));
        assertThat(options.cache.authorization.credentials, equalTo("test-token"));
        assertThat(options.discovery.agent, equalTo("discovery"));
        assertThat(options.discovery.search.limit, equalTo(5));
        assertThat(options.discovery.search.fields, contains("name", "description", "skills"));
        assertThat(options.discovery.search.weights.get("name"), equalTo(3.0));
        assertThat(options.discovery.search.weights.get("skills"), equalTo(2.0));
        assertThat(options.discovery.search.weights.get("description"), equalTo(1.0));
        assertThat(options.discovery.search.indexes, hasSize(1));
        assertThat(options.discovery.search.indexes.get(0), instanceOf(A2aKeywordSearchIndexConfig.class));
        assertThat(options.proxyPush.vault, equalTo("secrets0"));
        assertThat(options.proxyPush.key, equalTo("a2a-push"));
        assertThat(options.proxyPush.ttl, equalTo(Duration.ofHours(24)));
        assertThat(options.proxyPush.maxLength, equalTo(2048));
        assertThat(options.proxyPush.allow, contains("https://*.example.com/hooks/*"));
        assertThat(options.proxyPush.exit, equalTo("app2"));
        assertThat(options.proxyPush.authorization.name, equalTo("test1"));
        assertThat(options.proxyPush.authorization.credentials, equalTo("push-token"));
        assertThat(options.serverPush, nullValue());
    }

    @Test
    public void shouldRoundTripProxyOptions()
    {
        String text =
            """
            cache:
              store: memory0
              ttl: PT10M
              authorization:
                test0:
                  credentials: test-token
            discovery:
              agent: discovery
              search:
                limit: 5
                fields:
                  - name
                  - description
                  - skills
                weights:
                  name: 3
                  skills: 2
                  description: 1
                type: keyword
            push:
              vault: secrets0
              key: a2a-push
              ttl: PT24H
              maxLength: 2048
              allow:
                - "https://*.example.com/hooks/*"
              exit: app2
              authorization:
                test1:
                  credentials: push-token
            """;

        OptionsConfig options = jsonb.fromJson(text, OptionsConfig.class);

        assertThat(jsonb.toJson(options), equalTo(text));
    }

    @Test
    public void shouldReadProxyOptionsWithDefaults()
    {
        String text =
            """
            cache:
              store: memory0
            discovery:
              search: {}
            push:
              vault: secrets0
              key: a2a-push
              exit: app2
            """;

        A2aOptionsConfig options = (A2aOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options.cache.ttl, equalTo(Duration.ofMinutes(5)));
        assertThat(options.cache.authorization, nullValue());
        assertThat(options.discovery.agent, equalTo("discovery"));
        assertThat(options.discovery.search.limit, equalTo(5));
        assertThat(options.discovery.search.fields, contains("name", "description", "skills"));
        assertThat(options.discovery.search.weights, nullValue());
        assertThat(options.discovery.search.indexes, hasSize(1));
        assertThat(options.proxyPush.ttl, equalTo(Duration.ofHours(24)));
        assertThat(options.proxyPush.maxLength, equalTo(2048));
        assertThat(options.proxyPush.allow, nullValue());
        assertThat(options.proxyPush.authorization, nullValue());
    }

    @Test
    public void shouldReadProxyOptionsWithDiscoveryWithoutSearch()
    {
        String text =
            """
            discovery:
              agent: registry
            """;

        A2aOptionsConfig options = (A2aOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options.discovery.agent, equalTo("registry"));
        assertThat(options.discovery.search, nullValue());
    }
}
