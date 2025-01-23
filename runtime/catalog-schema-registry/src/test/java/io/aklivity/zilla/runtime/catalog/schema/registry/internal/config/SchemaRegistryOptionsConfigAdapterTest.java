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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.catalog.schema.registry.config.SchemaRegistryOptionsConfig;

public class SchemaRegistryOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new SchemaRegistryOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text = """
            {
              "url": "http://localhost:8081",
              "context": "default",
              "tls":
              {
                "keys":
                [
                  "client1"
                ],
                "trust":
                [
                  "serverca"
                ]
              },
              "credentials":
              {
                "headers":
                {
                  "authorization": "Basic dXNlcjpzZWNyZXQ="
                }
              }
            }
            """;

        SchemaRegistryOptionsConfig catalog = jsonb.fromJson(text, SchemaRegistryOptionsConfig.class);

        assertThat(catalog, not(nullValue()));
        assertThat(catalog.url, equalTo("http://localhost:8081"));
        assertThat(catalog.context, equalTo("default"));
        assertThat(catalog.maxAge.toSeconds(), equalTo(300L));
        assertThat(catalog.trust, equalTo(List.of("serverca")));
        assertThat(catalog.keys, equalTo(List.of("client1")));
        assertThat(catalog.authorization, equalTo("Basic dXNlcjpzZWNyZXQ="));
    }

    @Test
    public void shouldWriteOptions()
    {
        SchemaRegistryOptionsConfig catalog = SchemaRegistryOptionsConfig.builder()
            .url("http://localhost:8081")
            .context("default")
            .maxAge(Duration.ofSeconds(300))
            .keys(List.of("client1"))
            .trust(List.of("serverca"))
            .authorization("Basic dXNlcjpzZWNyZXQ=")
            .build();

        String text = jsonb.toJson(catalog);

        String expected = """
            {
              "url":"http://localhost:8081",
              "context":"default",
              "max-age":300,
              "tls":
              {
                "keys":
                [
                  "client1"
                ],
                "trust":
                [
                  "serverca"
                ]
              },
              "credentials":
              {
                "headers":
                {
                  "authorization":"Basic dXNlcjpzZWNyZXQ="
                }
              }
            }
            """.replaceAll("\\s*\\n\\s*", "");

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(expected));
    }
}
