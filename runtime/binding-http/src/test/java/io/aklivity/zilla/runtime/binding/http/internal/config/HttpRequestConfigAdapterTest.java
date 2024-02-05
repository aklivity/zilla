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
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

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
                "\"headers\": " +
                "{" +
                    "\"content-type\": \"test\"" +
                "}," +
                "\"params\": " +
                "{" +
                    "\"path\":" +
                    "{" +
                        "\"id\": \"test\"" +
                    "}," +
                    "\"query\":" +
                    "{" +
                        "\"index\": \"test\"" +
                    "}," +
                "}," +
                "\"content\": \"test\"," +
                "\"responses\":" +
                "[" +
                    "{" +
                        "\"status\": 200," +
                        "\"content-type\":" +
                        "[" +
                            "\"application/json\"" +
                        "]," +
                        "\"headers\": " +
                        "{" +
                            "\"content-type\": \"test\"" +
                        "}," +
                        "\"content\": \"test\"" +
                    "}," +
                    "{" +
                        "\"status\":" +
                        "[" +
                            "401, " +
                            "404 " +
                        "]," +
                        "\"content\": \"test\"" +
                    "}" +
                "]" +
             "}";

        // WHEN
        HttpRequestConfig request = jsonb.fromJson(json, HttpRequestConfig.class);

        // THEN
        assertThat(request.path, equalTo("/hello"));
        assertThat(request.method, equalTo(HttpRequestConfig.Method.GET));
        assertThat(request.contentType.get(0), equalTo("application/json"));
        assertThat(request.headers.get(0).name, equalTo("content-type"));
        assertThat(request.headers.get(0).model, instanceOf(TestModelConfig.class));
        assertThat(request.headers.get(0).model.model, equalTo("test"));
        assertThat(request.pathParams.get(0).name, equalTo("id"));
        assertThat(request.pathParams.get(0).model, instanceOf(TestModelConfig.class));
        assertThat(request.pathParams.get(0).model.model, equalTo("test"));
        assertThat(request.queryParams.get(0).name, equalTo("index"));
        assertThat(request.queryParams.get(0).model, instanceOf(TestModelConfig.class));
        assertThat(request.queryParams.get(0).model.model, equalTo("test"));
        assertThat(request.content, instanceOf(TestModelConfig.class));
        assertThat(request.content.model, equalTo("test"));
        assertThat(request.responses.get(0).status.get(0), equalTo("200"));
        assertThat(request.responses.get(0).contentType.get(0), equalTo("application/json"));
        assertThat(request.responses.get(0).headers.get(0).name, equalTo("content-type"));
        assertThat(request.responses.get(0).headers.get(0).model.model, equalTo("test"));
        assertThat(request.responses.get(0).content.model, equalTo("test"));
        assertThat(request.responses.get(1).status.get(0), equalTo("401"));
        assertThat(request.responses.get(1).status.get(1), equalTo("404"));
        assertThat(request.responses.get(1).content.model, equalTo("test"));
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
                "\"headers\":" +
                "{" +
                    "\"content-type\":\"test\"" +
                "}," +
                "\"params\":" +
                "{" +
                    "\"path\":" +
                    "{" +
                        "\"id\":\"test\"" +
                    "}," +
                    "\"query\":" +
                    "{" +
                        "\"index\":\"test\"" +
                    "}" +
                "}," +
                "\"content\":\"test\"," +
                "\"responses\":" +
                "[" +
                    "{" +
                        "\"status\":200," +
                        "\"content-type\":" +
                        "[" +
                            "\"application/json\"" +
                        "]," +
                        "\"headers\":" +
                        "{" +
                            "\"content-type\":\"test\"" +
                        "}," +
                        "\"content\":\"test\"" +
                    "}," +
                    "{" +
                        "\"status\":" +
                        "[" +
                            "401," +
                            "404" +
                        "]," +
                        "\"content\":\"test\"" +
                    "}" +
                "]" +
            "}";
        HttpRequestConfig request = HttpRequestConfig.builder()
            .path("/hello")
            .method(HttpRequestConfig.Method.GET)
            .contentType("application/json")
            .header()
                .name("content-type")
                .model(TestModelConfig::builder)
                    .build()
                .build()
            .pathParam()
                .name("id")
                .model(TestModelConfig::builder)
                    .build()
                .build()
            .queryParam()
                .name("index")
                .model(TestModelConfig::builder)
                    .build()
                .build()
            .content(TestModelConfig::builder)
                .build()
            .response()
                .status(200)
                .contentType("application/json")
                .header()
                    .name("content-type")
                    .model(TestModelConfig::builder)
                        .build()
                    .build()
                .content(TestModelConfig::builder)
                    .build()
                .build()
            .response()
                .status(401)
                .status(404)
                .content(TestModelConfig::builder)
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
