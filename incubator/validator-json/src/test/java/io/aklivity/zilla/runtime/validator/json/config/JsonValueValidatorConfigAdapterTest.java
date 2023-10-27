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
package io.aklivity.zilla.runtime.validator.json.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class JsonValueValidatorConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new JsonValidatorConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadJsonValidator()
    {
        // GIVEN
        String json =
            "{" +
                "\"type\": \"json\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"subject\": \"subject1\"," +
                            "\"version\": \"latest\"" +
                        "}," +
                        "{" +
                            "\"strategy\": \"topic\"," +
                            "\"version\": \"latest\"" +
                        "}," +
                        "{" +
                            "\"id\": 42" +
                        "}" +
                    "]" +
                "}" +
            "}";

        // WHEN
        JsonValidatorConfig validator = jsonb.fromJson(json, JsonValidatorConfig.class);

        // THEN
        assertThat(validator, not(nullValue()));
        assertThat(validator.type, equalTo("json"));
        assertThat(validator.catalogs.size(), equalTo(1));
        assertThat(validator.catalogs.get(0).name, equalTo("test0"));
        assertThat(validator.catalogs.get(0).schemas.get(0).subject, equalTo("subject1"));
        assertThat(validator.catalogs.get(0).schemas.get(0).version, equalTo("latest"));
        assertThat(validator.catalogs.get(0).schemas.get(0).id, equalTo(0));
        assertThat(validator.catalogs.get(0).schemas.get(1).strategy, equalTo("topic"));
        assertThat(validator.catalogs.get(0).schemas.get(1).version, equalTo("latest"));
        assertThat(validator.catalogs.get(0).schemas.get(1).id, equalTo(0));
        assertThat(validator.catalogs.get(0).schemas.get(2).strategy, nullValue());
        assertThat(validator.catalogs.get(0).schemas.get(2).version, nullValue());
        assertThat(validator.catalogs.get(0).schemas.get(2).id, equalTo(42));
    }

    @Test
    public void shouldWriteJsonValidator()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"type\":\"json\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"subject\":\"subject1\"," +
                            "\"version\":\"latest\"" +
                        "}," +
                        "{" +
                            "\"strategy\":\"topic\"," +
                            "\"version\":\"latest\"" +
                        "}," +
                        "{" +
                            "\"id\":42" +
                        "}" +
                    "]" +
                "}" +
            "}";
        JsonValidatorConfig validator = JsonValidatorConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .subject("subject1")
                        .version("latest")
                        .build()
                    .schema()
                        .strategy("topic")
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
