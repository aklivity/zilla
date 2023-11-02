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
package io.aklivity.zilla.runtime.validator.avro.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class AvroValueValidatorConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new AvroValidatorConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadAvroValidator()
    {
        // GIVEN
        String json =
            "{" +
                "\"format\":\"json\"," +
                "\"type\": \"avro\"," +
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
        AvroValidatorConfig validator = jsonb.fromJson(json, AvroValidatorConfig.class);

        // THEN
        assertThat(validator, not(nullValue()));
        assertThat(validator.format, equalTo("json"));
        assertThat(validator.type, equalTo("avro"));
        assertThat(validator.cataloged.size(), equalTo(1));
        assertThat(validator.cataloged.get(0).name, equalTo("test0"));
        assertThat(validator.cataloged.get(0).schemas.get(0).strategy, equalTo("topic"));
        assertThat(validator.cataloged.get(0).schemas.get(0).version, equalTo("latest"));
        assertThat(validator.cataloged.get(0).schemas.get(0).id, equalTo(0));
        assertThat(validator.cataloged.get(0).schemas.get(1).subject, equalTo("cat"));
        assertThat(validator.cataloged.get(0).schemas.get(1).strategy, nullValue());
        assertThat(validator.cataloged.get(0).schemas.get(1).version, equalTo("latest"));
        assertThat(validator.cataloged.get(0).schemas.get(1).id, equalTo(0));
        assertThat(validator.cataloged.get(0).schemas.get(2).strategy, nullValue());
        assertThat(validator.cataloged.get(0).schemas.get(2).version, nullValue());
        assertThat(validator.cataloged.get(0).schemas.get(2).id, equalTo(42));
    }

    @Test
    public void shouldWriteAvroValidator()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"format\":\"json\"," +
                "\"type\":\"avro\"," +
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
        AvroValidatorConfig validator = AvroValidatorConfig.builder()
            .format("json")
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
        String json = jsonb.toJson(validator);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
