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
package io.aklivity.zilla.runtime.model.avro.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.ValidateConfig;
import io.aklivity.zilla.runtime.engine.config.ValidateMode;
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
        String json = """
            {
                "view": "json",
                "model": "avro",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "strategy": "topic",
                            "version": "latest"
                        },
                        {
                            "subject": "cat",
                            "version": "latest"
                        },
                        {
                            "id": 42
                        }
                    ]
                }
            }""";

        // WHEN
        AvroModelConfig model = jsonb.fromJson(json, AvroModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.view, equalTo("json"));
        assertThat(model.model, equalTo("avro"));
        assertThat(model.cataloged.size(), equalTo(1));
        assertThat(model.cataloged.get(0).name, equalTo("test0"));
        assertThat(model.cataloged.get(0).schemas.get(0).strategy, equalTo("topic"));
        assertThat(model.cataloged.get(0).schemas.get(0).version, equalTo("latest"));
        assertThat(model.cataloged.get(0).schemas.get(0).id, equalTo(0));
        assertThat(model.cataloged.get(0).schemas.get(1).subject, equalTo("cat"));
        assertThat(model.cataloged.get(0).schemas.get(1).strategy, nullValue());
        assertThat(model.cataloged.get(0).schemas.get(1).version, equalTo("latest"));
        assertThat(model.cataloged.get(0).schemas.get(1).id, equalTo(0));
        assertThat(model.cataloged.get(0).schemas.get(2).strategy, nullValue());
        assertThat(model.cataloged.get(0).schemas.get(2).version, nullValue());
        assertThat(model.cataloged.get(0).schemas.get(2).id, equalTo(42));
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
        AvroModelConfig model = AvroModelConfig.builder()
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
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldDefaultValidateStrictWhenAbsent()
    {
        // GIVEN
        String json = """
            {
                "model": "avro",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "cat",
                            "version": "latest"
                        }
                    ]
                }
            }""";

        // WHEN
        AvroModelConfig model = jsonb.fromJson(json, AvroModelConfig.class);

        // THEN
        assertThat(model.validate, not(nullValue()));
        assertThat(model.validate.decode, equalTo(ValidateMode.STRICT));
        assertThat(model.validate.encode, equalTo(ValidateMode.STRICT));
    }

    @Test
    public void shouldReadScalarValidate()
    {
        // GIVEN
        String json = """
            {
                "model": "avro",
                "validate": "lenient",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "cat",
                            "version": "latest"
                        }
                    ]
                }
            }""";

        // WHEN
        AvroModelConfig model = jsonb.fromJson(json, AvroModelConfig.class);

        // THEN
        assertThat(model.validate.decode, equalTo(ValidateMode.LENIENT));
        assertThat(model.validate.encode, equalTo(ValidateMode.LENIENT));
    }

    @Test
    public void shouldReadObjectValidate()
    {
        // GIVEN
        String json = """
            {
                "model": "avro",
                "validate":
                {
                    "decode": "lenient",
                    "encode": "strict"
                },
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "cat",
                            "version": "latest"
                        }
                    ]
                }
            }""";

        // WHEN
        AvroModelConfig model = jsonb.fromJson(json, AvroModelConfig.class);

        // THEN
        assertThat(model.validate.decode, equalTo(ValidateMode.LENIENT));
        assertThat(model.validate.encode, equalTo(ValidateMode.STRICT));
    }

    @Test
    public void shouldWriteScalarValidate()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"avro\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"subject\":\"cat\"," +
                            "\"version\":\"latest\"" +
                        "}" +
                    "]" +
                "}," +
                "\"validate\":\"lenient\"" +
            "}";
        AvroModelConfig model = AvroModelConfig.builder()
            .validate(new ValidateConfig(ValidateMode.LENIENT, ValidateMode.LENIENT))
            .catalog()
                .name("test0")
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteObjectValidate()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"avro\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"subject\":\"cat\"," +
                            "\"version\":\"latest\"" +
                        "}" +
                    "]" +
                "}," +
                "\"validate\":" +
                "{" +
                    "\"decode\":\"lenient\"," +
                    "\"encode\":\"strict\"" +
                "}" +
            "}";
        AvroModelConfig model = AvroModelConfig.builder()
            .validate(new ValidateConfig(ValidateMode.LENIENT, ValidateMode.STRICT))
            .catalog()
                .name("test0")
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldOmitValidateWhenStrict()
    {
        // GIVEN
        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(containsString("validate")));
    }
}
