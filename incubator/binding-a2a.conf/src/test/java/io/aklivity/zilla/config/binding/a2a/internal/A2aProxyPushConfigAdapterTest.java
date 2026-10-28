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
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aProxyPushConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aProxyPushConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(new JsonbConfig().withAdapters(new A2aProxyPushConfigAdapter()))
            .build();
    }

    @Test
    public void shouldReadPushWithDefaults()
    {
        String text =
            """
            vault: secrets0
            key: a2a-push
            exit: app2
            """;

        A2aProxyPushConfig push = jsonb.fromJson(text, A2aProxyPushConfig.class);

        assertThat(push.vault, equalTo("secrets0"));
        assertThat(push.key, equalTo("a2a-push"));
        assertThat(push.ttl, equalTo(Duration.ofHours(24)));
        assertThat(push.maxLength, equalTo(2048));
        assertThat(push.allow, nullValue());
        assertThat(push.exit, equalTo("app2"));
        assertThat(push.authorization, nullValue());
    }

    @Test
    public void shouldReadPushWithAllowAndAuthorization()
    {
        String text =
            """
            vault: secrets0
            key: a2a-push
            ttl: PT1H
            maxLength: 512
            allow:
              - https://*.example.com/hooks/*
              - https://hooks.example.org/*
            exit: app2
            authorization:
              test0:
                credentials: push-token
            """;

        A2aProxyPushConfig push = jsonb.fromJson(text, A2aProxyPushConfig.class);

        assertThat(push.ttl, equalTo(Duration.ofHours(1)));
        assertThat(push.maxLength, equalTo(512));
        assertThat(push.allow, contains("https://*.example.com/hooks/*", "https://hooks.example.org/*"));
        assertThat(push.authorization.name, equalTo("test0"));
        assertThat(push.authorization.credentials, equalTo("push-token"));
    }

    @Test
    public void shouldWritePush()
    {
        A2aProxyPushConfig push = A2aProxyPushConfig.builder()
            .vault("secrets0")
            .key("a2a-push")
            .ttl(Duration.ofHours(24))
            .maxLength(2048)
            .allow(List.of("https://*.example.com/hooks/*"))
            .exit("app2")
            .build();

        assertThat(jsonb.toJson(push), equalTo(
            """
            vault: secrets0
            key: a2a-push
            ttl: PT24H
            maxLength: 2048
            allow:
              - "https://*.example.com/hooks/*"
            exit: app2
            """));
    }
}
