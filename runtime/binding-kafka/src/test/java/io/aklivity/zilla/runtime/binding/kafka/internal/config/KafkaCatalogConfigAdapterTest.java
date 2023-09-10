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
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class KafkaCatalogConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new KafkaCatalogConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"name\": \"test0\"," +
                    "\"strategy\": \"topic\"," +
                    "\"version\": \"latest\"" +
                "}";

        KafkaCatalogConfig catalog = jsonb.fromJson(text, KafkaCatalogConfig.class);

        assertThat(catalog, not(nullValue()));
        assertThat(catalog.name, equalTo("test0"));
        assertThat(catalog.strategy, equalTo("topic"));
        assertThat(catalog.version, equalTo("latest"));
    }

    @Test
    public void shouldWriteCondition()
    {
        KafkaCatalogConfig catalog = new KafkaCatalogConfig("test0", "topic", "latest", 0);

        String text = jsonb.toJson(catalog);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test0\",\"strategy\":\"topic\",\"version\":\"latest\"}"));
    }
}
