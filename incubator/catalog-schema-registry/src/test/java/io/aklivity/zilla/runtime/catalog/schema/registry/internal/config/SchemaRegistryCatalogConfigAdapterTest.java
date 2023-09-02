/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class SchemaRegistryCatalogConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new SchemaRegistryCatalogConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"url\": \"http://localhost:8081\"," +
                    "\"context\": \"default\"," +
                    "}";

        SchemaRegistryCatalogConfig catalog = jsonb.fromJson(text, SchemaRegistryCatalogConfig.class);

        assertThat(catalog, not(nullValue()));
        assertThat(catalog.url, equalTo("http://localhost:8081"));
        assertThat(catalog.context, equalTo("default"));
    }

    @Test
    public void shouldWriteCondition()
    {
        SchemaRegistryCatalogConfig catalog = new SchemaRegistryCatalogConfig("http://localhost:8081", "default");

        String text = jsonb.toJson(catalog);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"url\":\"http://localhost:8081\",\"context\":\"default\"}"));
    }
}
