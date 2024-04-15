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
package io.aklivity.zilla.runtime.catalog.filesystem.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class ApicurioOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new ApicurioOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"url\": \"http://localhost:8081\"," +
                    "\"group-id\": \"default\"," +
                "}";

        ApicurioOptionsConfig catalog = jsonb.fromJson(text, ApicurioOptionsConfig.class);

        assertThat(catalog, not(nullValue()));
        assertThat(catalog.url, equalTo("http://localhost:8081"));
        assertThat(catalog.groupId, equalTo("default"));
        assertThat(catalog.maxAge.toSeconds(), equalTo(300L));
    }

    @Test
    public void shouldWriteCondition()
    {
        ApicurioOptionsConfig catalog = ApicurioOptionsConfig.builder()
            .url("http://localhost:8081")
            .groupId("default")
            .maxAge(Duration.ofSeconds(300))
            .build();

        String text = jsonb.toJson(catalog);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"url\":\"http://localhost:8081\",\"group-id\":\"default\",\"max-age\":300}"));
    }
}
