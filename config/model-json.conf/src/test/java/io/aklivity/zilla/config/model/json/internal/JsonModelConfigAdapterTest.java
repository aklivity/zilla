/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.model.json.internal;

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

import io.aklivity.zilla.config.engine.ValidateConfig;
import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.config.model.json.JsonModelConfig;

public class JsonModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new JsonModelConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadJsonConverter()
    {
        // GIVEN
        String json = """
            {
                "model": "json",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "subject1",
                            "version": "latest"
                        },
                        {
                            "strategy": "topic",
                            "version": "latest"
                        },
                        {
                            "id": 42
                        }
                    ]
                }
            }""";

        // WHEN
        JsonModelConfig config = jsonb.fromJson(json, JsonModelConfig.class);

        // THEN
        assertThat(config, not(nullValue()));
        assertThat(config.model, equalTo("json"));
        assertThat(config.cataloged.size(), equalTo(1));
        assertThat(config.cataloged.get(0).name, equalTo("test0"));
        assertThat(config.cataloged.get(0).schemas.get(0).subject, equalTo("subject1"));
        assertThat(config.cataloged.get(0).schemas.get(0).version, equalTo("latest"));
        assertThat(config.cataloged.get(0).schemas.get(0).id, equalTo(0));
        assertThat(config.cataloged.get(0).schemas.get(1).strategy, equalTo("topic"));
        assertThat(config.cataloged.get(0).schemas.get(1).version, equalTo("latest"));
        assertThat(config.cataloged.get(0).schemas.get(1).id, equalTo(0));
        assertThat(config.cataloged.get(0).schemas.get(2).strategy, nullValue());
        assertThat(config.cataloged.get(0).schemas.get(2).version, nullValue());
        assertThat(config.cataloged.get(0).schemas.get(2).id, equalTo(42));
    }

    @Test
    public void shouldWriteJsonConverter()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"json\"," +
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
        JsonModelConfig config = JsonModelConfig.builder()
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
        String json = jsonb.toJson(config);

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
                "model": "json",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "subject1",
                            "version": "latest"
                        }
                    ]
                }
            }""";

        // WHEN
        JsonModelConfig config = jsonb.fromJson(json, JsonModelConfig.class);

        // THEN
        assertThat(config.validate, not(nullValue()));
        assertThat(config.validate.decode, equalTo(ValidateMode.STRICT));
        assertThat(config.validate.encode, equalTo(ValidateMode.STRICT));
    }

    @Test
    public void shouldReadScalarValidate()
    {
        // GIVEN
        String json = """
            {
                "model": "json",
                "validate": "lenient",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "subject1",
                            "version": "latest"
                        }
                    ]
                }
            }""";

        // WHEN
        JsonModelConfig config = jsonb.fromJson(json, JsonModelConfig.class);

        // THEN
        assertThat(config.validate.decode, equalTo(ValidateMode.LENIENT));
        assertThat(config.validate.encode, equalTo(ValidateMode.LENIENT));
    }

    @Test
    public void shouldReadObjectValidate()
    {
        // GIVEN
        String json = """
            {
                "model": "json",
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
                            "subject": "subject1",
                            "version": "latest"
                        }
                    ]
                }
            }""";

        // WHEN
        JsonModelConfig config = jsonb.fromJson(json, JsonModelConfig.class);

        // THEN
        assertThat(config.validate.decode, equalTo(ValidateMode.LENIENT));
        assertThat(config.validate.encode, equalTo(ValidateMode.STRICT));
    }

    @Test
    public void shouldWriteScalarValidate()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"json\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"subject\":\"subject1\"," +
                            "\"version\":\"latest\"" +
                        "}" +
                    "]" +
                "}," +
                "\"validate\":\"lenient\"" +
            "}";
        JsonModelConfig config = JsonModelConfig.builder()
            .validate(new ValidateConfig(ValidateMode.LENIENT, ValidateMode.LENIENT))
            .catalog()
                .name("test0")
                    .schema()
                        .subject("subject1")
                        .version("latest")
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(config);

        // THEN
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldOmitValidateWhenStrict()
    {
        // GIVEN
        JsonModelConfig config = JsonModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .subject("subject1")
                        .version("latest")
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(config);

        // THEN
        assertThat(json, not(containsString("validate")));
    }
}
