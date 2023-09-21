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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.engine.internal.validator.config.AvroValidatorConfig;
import io.aklivity.zilla.runtime.engine.internal.validator.config.CatalogedConfig;

public class HttpRequestConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new HttpRequestConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        // GIVEN
        String json =
            "{" +
                "\"path\": \"/hello\"," +
                "\"method\": \"GET\"," +
                "\"content-type\": " +
                "[" +
                    "\"application/json\"" +
                "]," +
                "\"content\":" +
                "{" +
                    "\"type\": \"avro\"," +
                    "\"catalog\": " +
                    "{" +
                        "test0:" +
                        "[" +
                            "{" +
                                "\"schema\": \"cat\"" +
                            "}," +
                            "{" +
                                "\"schema\": \"tiger\"" +
                            "}" +
                        "]" +
                    "}" +
                "}" +
             "}";

        // WHEN
        HttpRequestConfig request = jsonb.fromJson(json, HttpRequestConfig.class);

        // THEN
        assertThat(request.path, equalTo("/hello"));
        assertThat(request.method, equalTo(HttpRequestConfig.Method.GET));
        assertThat(request.contentType.get(0), equalTo("application/json"));
        assertThat(request.content.type, equalTo("avro"));
        assertThat(request.content, instanceOf(AvroValidatorConfig.class));
        CatalogedConfig test0 = ((AvroValidatorConfig)request.content).catalogs.get(0);
        assertThat(test0.name, equalTo("test0"));
        assertThat(test0.schemas.get(0).schema, equalTo("cat"));
        assertThat(test0.schemas.get(1).schema, equalTo("tiger"));
        // TODO: Ati - header, param
    }

    @Test
    public void shouldWriteOptions()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"path\":\"/hello\"," +
                "\"method\":\"GET\"," +
                "\"content-type\":" +
                "[" +
                    "\"application/json\"" +
                "]," +
                "\"content\":" +
                "{" +
                    "\"type\":\"avro\"," +
                    "\"catalog\":" +
                    "{" +
                        "\"test0\":" +
                        "[" +
                            "{" +
                                "\"schema\":\"cat\"" +
                            "}," +
                            "{" +
                                "\"schema\":\"tiger\"" +
                            "}" +
                        "]" +
                    "}" +
                "}" +
            "}";
        HttpRequestConfig request = HttpRequestConfig.builder()
            .path("/hello")
            .method(HttpRequestConfig.Method.GET)
            .contentType("application/json")
            .content(AvroValidatorConfig::builder)
                .catalog()
                    .name("test0")
                        .schema()
                            .schema("cat")
                            .build()
                        .schema()
                            .schema("tiger")
                            .build()
                    .build()
                .build()
            .build();

        // WHEN
        String json = jsonb.toJson(request);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
