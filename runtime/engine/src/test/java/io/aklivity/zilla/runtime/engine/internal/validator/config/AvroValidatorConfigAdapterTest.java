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
package io.aklivity.zilla.runtime.engine.internal.validator.config;

import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;

public class AvroValidatorConfigAdapterTest
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
                            "\"schema\": \"cat\"," +
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
        assertThat(validator.type, equalTo("avro"));
        assertThat(validator.catalogs.size(), equalTo(1));
        assertThat(validator.catalogs.get(0).name, equalTo("test0"));
        assertThat(validator.catalogs.get(0).schemas.get(0).schema, nullValue());
        assertThat(validator.catalogs.get(0).schemas.get(0).strategy, equalTo("topic"));
        assertThat(validator.catalogs.get(0).schemas.get(0).version, equalTo("latest"));
        assertThat(validator.catalogs.get(0).schemas.get(0).id, equalTo(0));
        assertThat(validator.catalogs.get(0).schemas.get(1).schema, equalTo("cat"));
        assertThat(validator.catalogs.get(0).schemas.get(1).strategy, nullValue());
        assertThat(validator.catalogs.get(0).schemas.get(1).version, equalTo("latest"));
        assertThat(validator.catalogs.get(0).schemas.get(1).id, equalTo(0));
        assertThat(validator.catalogs.get(0).schemas.get(2).schema, nullValue());
        assertThat(validator.catalogs.get(0).schemas.get(2).strategy, nullValue());
        assertThat(validator.catalogs.get(0).schemas.get(2).version, nullValue());
        assertThat(validator.catalogs.get(0).schemas.get(2).id, equalTo(42));
    }

    @Test
    public void shouldWriteAvroValidator()
    {
        // GIVEN
        String expectedJson =
            "{" +
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
                            "\"schema\":\"cat\"," +
                            "\"version\":\"latest\"" +
                        "}," +
                        "{" +
                            "\"id\":42" +
                        "}" +
                    "]" +
                "}" +
            "}";
        AvroValidatorConfig validator = AvroValidatorConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .build()
                    .schema()
                        .schema("cat")
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
