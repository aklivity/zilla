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
package io.aklivity.zilla.runtime.model.protobuf.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

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
        String json =
            "{" +
                "\"model\": \"protobuf\"," +
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
        ProtobufModelConfig converter = jsonb.fromJson(json, ProtobufModelConfig.class);

        // THEN
        assertThat(converter, not(nullValue()));
        assertThat(converter.model, equalTo("protobuf"));
        assertThat(converter.cataloged.size(), equalTo(1));
        assertThat(converter.cataloged.get(0).name, equalTo("test0"));
        assertThat(converter.cataloged.get(0).schemas.get(0).strategy, equalTo("topic"));
        assertThat(converter.cataloged.get(0).schemas.get(0).version, equalTo("latest"));
        assertThat(converter.cataloged.get(0).schemas.get(0).id, equalTo(0));
        assertThat(converter.cataloged.get(0).schemas.get(1).subject, equalTo("cat"));
        assertThat(converter.cataloged.get(0).schemas.get(1).strategy, nullValue());
        assertThat(converter.cataloged.get(0).schemas.get(1).version, equalTo("latest"));
        assertThat(converter.cataloged.get(0).schemas.get(1).id, equalTo(0));
        assertThat(converter.cataloged.get(0).schemas.get(2).strategy, nullValue());
        assertThat(converter.cataloged.get(0).schemas.get(2).version, nullValue());
        assertThat(converter.cataloged.get(0).schemas.get(2).id, equalTo(42));
    }

    @Test
    public void shouldWriteAvroConverter()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"protobuf\"," +
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
        ProtobufModelConfig converter = ProtobufModelConfig.builder()
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
