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
package io.aklivity.zilla.config.model.protobuf.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ValidateConfig;
import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.config.model.protobuf.ProtobufModelConfig;

public class ProtobufModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new ProtobufModelConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadAvroConverter()
    {
        // GIVEN
        String json = """
                {
                    "model": "protobuf",
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
                }
                """;

        // WHEN
        ProtobufModelConfig model = jsonb.fromJson(json, ProtobufModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.model, equalTo("protobuf"));
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
    public void shouldWriteAvroConverter()
    {
        // GIVEN
        String expectedJson = """
                {"model":"protobuf","catalog":{"test0":[{"strategy":"topic","version":"latest"},\
                {"subject":"cat","version":"latest"},{"id":42}]}}""";
        ProtobufModelConfig model = ProtobufModelConfig.builder()
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

        String json = jsonb.toJson(model);

        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteProtobufConfig()
    {
        String expectedJson = """
                {"model":"protobuf","catalog":{"test0":[{"subject":"user","version":"latest"}]}}""";
        ProtobufModelConfig model = ProtobufModelConfig.builder()
                .catalog()
                    .name("test0")
                    .schema()
                        .subject("user")
                        .version("latest")
                        .build()
                    .build()
                .build();

        String json = jsonb.toJson(model);

        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldReadProtobufConfig()
    {
        String json = """
                {
                    "model":"protobuf",
                    "catalog":
                    {
                        "test0":
                        [
                            {
                                "subject":"user",
                                "version":"latest"
                            }
                        ]
                    }
                }
                """;

        ProtobufModelConfig model = (ProtobufModelConfig) jsonb.fromJson(json, ModelConfig.class);

        assertThat(model.model, equalTo("protobuf"));
        assertThat(model.cataloged, hasSize(1));
        assertThat(model.cataloged.get(0).name, equalTo("test0"));
    }

    @Test
    public void shouldDefaultValidateStrictWhenAbsent()
    {
        String json = """
                {
                    "model":"protobuf",
                    "catalog":
                    {
                        "test0":
                        [
                            {
                                "subject":"user",
                                "version":"latest"
                            }
                        ]
                    }
                }
                """;

        ProtobufModelConfig model = jsonb.fromJson(json, ProtobufModelConfig.class);

        assertThat(model.validate, not(nullValue()));
        assertThat(model.validate.decode, equalTo(ValidateMode.STRICT));
        assertThat(model.validate.encode, equalTo(ValidateMode.STRICT));
    }

    @Test
    public void shouldReadScalarValidate()
    {
        String json = """
                {
                    "model":"protobuf",
                    "validate":"lenient",
                    "catalog":
                    {
                        "test0":
                        [
                            {
                                "subject":"user",
                                "version":"latest"
                            }
                        ]
                    }
                }
                """;

        ProtobufModelConfig model = jsonb.fromJson(json, ProtobufModelConfig.class);

        assertThat(model.validate.decode, equalTo(ValidateMode.LENIENT));
        assertThat(model.validate.encode, equalTo(ValidateMode.LENIENT));
    }

    @Test
    public void shouldReadObjectValidate()
    {
        String json = """
                {
                    "model":"protobuf",
                    "validate":
                    {
                        "decode":"lenient",
                        "encode":"strict"
                    },
                    "catalog":
                    {
                        "test0":
                        [
                            {
                                "subject":"user",
                                "version":"latest"
                            }
                        ]
                    }
                }
                """;

        ProtobufModelConfig model = jsonb.fromJson(json, ProtobufModelConfig.class);

        assertThat(model.validate.decode, equalTo(ValidateMode.LENIENT));
        assertThat(model.validate.encode, equalTo(ValidateMode.STRICT));
    }

    @Test
    public void shouldWriteScalarValidate()
    {
        String expectedJson = """
                {"model":"protobuf","catalog":{"test0":[{"subject":"user","version":"latest"}]},"validate":"lenient"}""";
        ProtobufModelConfig model = ProtobufModelConfig.builder()
                .validate(new ValidateConfig(ValidateMode.LENIENT, ValidateMode.LENIENT))
                .catalog()
                    .name("test0")
                    .schema()
                        .subject("user")
                        .version("latest")
                        .build()
                    .build()
                .build();

        String json = jsonb.toJson(model);

        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldOmitValidateWhenStrict()
    {
        ProtobufModelConfig model = ProtobufModelConfig.builder()
                .catalog()
                    .name("test0")
                    .schema()
                        .subject("user")
                        .version("latest")
                        .build()
                    .build()
                .build();

        String json = jsonb.toJson(model);

        assertThat(json, not(containsString("validate")));
    }
}
