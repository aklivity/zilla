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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aCacheConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aCacheConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(new JsonbConfig().withAdapters(new A2aCacheConfigAdapter()))
            .build();
    }

    @Test
    public void shouldReadCacheWithDefaultTtl()
    {
        A2aCacheConfig cache = jsonb.fromJson("store: memory0", A2aCacheConfig.class);

        assertThat(cache.store, equalTo("memory0"));
        assertThat(cache.ttl, equalTo(Duration.ofMinutes(5)));
        assertThat(cache.authorization, nullValue());
    }

    @Test
    public void shouldReadCacheWithAuthorization()
    {
        String text =
            """
            store: memory0
            ttl: PT30S
            authorization:
              test0:
                credentials: "{token}"
            """;

        A2aCacheConfig cache = jsonb.fromJson(text, A2aCacheConfig.class);

        assertThat(cache.ttl, equalTo(Duration.ofSeconds(30)));
        assertThat(cache.authorization.name, equalTo("test0"));
        assertThat(cache.authorization.credentials, equalTo("{token}"));
    }

    @Test
    public void shouldWriteCache()
    {
        A2aCacheConfig cache = A2aCacheConfig.builder()
            .store("memory0")
            .ttl(Duration.ofMinutes(10))
            .authorization()
                .name("test0")
                .credentials("{token}")
                .build()
            .build();

        assertThat(jsonb.toJson(cache), equalTo(
            """
            store: memory0
            ttl: PT10M
            authorization:
              test0:
                credentials: "{token}"
            """));
    }
}
