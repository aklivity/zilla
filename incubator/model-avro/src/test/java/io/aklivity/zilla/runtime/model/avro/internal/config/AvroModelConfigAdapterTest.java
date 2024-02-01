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
package io.aklivity.zilla.runtime.model.avro.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new AvroModelConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadAvroconverter()
    {
        // GIVEN
        String json =
            "{" +
                "\"view\":\"json\"," +
                "\"model\": \"avro\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"strategy\": \"topic\"," +
                            "\"version\": \"latest\"" +
                        "}," +
                        "{" +
                            "\"subject\": \"cat\"," +
                            "\"version\": \"latest\"" +
                        "}," +
                        "{" +
                            "\"id\": 42" +
                        "}" +
                    "]" +
                "}" +
            "}";

        // WHEN
        AvroModelConfig converter = jsonb.fromJson(json, AvroModelConfig.class);

        // THEN
        assertThat(converter, not(nullValue()));
        assertThat(converter.view, equalTo("json"));
        assertThat(converter.model, equalTo("avro"));
        assertThat(converter.cataloged.size(), equalTo(1));
        assertThat(converter.cataloged.get(0).name, equalTo("test0"));
        List<SchemaConfig> schemas = new ArrayList<>(converter.cataloged.get(0).schemas);
        assertThat(schemas.get(0).strategy, equalTo("topic"));
        assertThat(schemas.get(0).version, equalTo("latest"));
        assertThat(schemas.get(0).id, equalTo(0));
        assertThat(schemas.get(1).subject, equalTo("cat"));
        assertThat(schemas.get(1).strategy, nullValue());
        assertThat(schemas.get(1).version, equalTo("latest"));
        assertThat(schemas.get(1).id, equalTo(0));
        assertThat(schemas.get(2).strategy, nullValue());
        assertThat(schemas.get(2).version, nullValue());
        assertThat(schemas.get(2).id, equalTo(42));
    }

    @Test
    public void shouldWriteAvroconverter()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"view\":\"json\"," +
                "\"model\":\"avro\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"strategy\":\"topic\"," +
                            "\"version\":\"latest\"" +
                        "}," +
                        "{" +
                            "\"subject\":\"cat\"," +
                            "\"version\":\"latest\"" +
                        "}," +
                        "{" +
                            "\"id\":42" +
                        "}" +
                    "]" +
                "}" +
            "}";
        AvroModelConfig converter = AvroModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .build()
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .schema()
                        .id(42)
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(converter);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
