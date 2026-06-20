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
package io.aklivity.zilla.runtime.catalog.inline.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class InlineOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new InlineOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadCondition()
    {
        String yaml =
                """
                subjects:
                  subject1:
                    version: latest
                    schema: "{\\"type\\":\\"object\\",\\"properties\\":\
                {\\"id\\":{\\"type\\":\\"string\\"},\\"status\\":{\\"type\\":\\"string\\"}},\
                \\"required\\":[\\"id\\",\\"status\\"]}"
                """;

        InlineOptionsConfig catalog = jsonb.fromJson(yaml, InlineOptionsConfig.class);

        assertThat(catalog, not(nullValue()));
        InlineSchemaConfig schema = catalog.subjects.get(0);
        assertThat(schema.subject, equalTo("subject1"));
        assertThat(schema.version, equalTo("latest"));
        assertThat(schema.schema, equalTo("{\"type\":\"object\",\"properties\":" +
                "{\"id\":{\"type\":\"string\"},\"status\":{\"type\":\"string\"}}," +
                "\"required\":[\"id\",\"status\"]}"));
    }

    @Test
    public void shouldWriteCondition()
    {
        String expectedYaml =
                """
                subjects:
                  subject1:
                    schema: "{\\"type\\":\\"object\\",\\"properties\\":\
                {\\"id\\":{\\"type\\":\\"string\\"},\\"status\\":{\\"type\\":\\"string\\"}},\
                \\"required\\":[\\"id\\",\\"status\\"]}"
                    version: latest
                """;

        InlineOptionsConfig catalog = InlineOptionsConfig.builder()
            .schema()
                .subject("subject1")
                .version("latest")
                .schema("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}," +
                        "\"status\":{\"type\":\"string\"}}," +
                        "\"required\":[\"id\",\"status\"]}")
                .build()
            .build();

        String yaml = jsonb.toJson(catalog);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(expectedYaml));
    }
}
