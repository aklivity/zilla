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

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequest;
import io.aklivity.zilla.runtime.binding.http.config.HttpVersion;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.ValidatorTypeConfig;

public class HttpOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new HttpOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"versions\":" +
                    "[" +
                        "\"http/1.1\"," +
                        "\"h2\"" +
                    "]," +
                    "\"access-control\":" +
                    "{" +
                        "\"policy\": \"cross-origin\"," +
                        "\"allow\":" +
                        "{" +
                            "\"origins\": [ \"https://example.com:9090\" ]," +
                            "\"methods\": [ \"DELETE\" ]," +
                            "\"headers\": [ \"x-api-key\" ]," +
                            "\"credentials\": true" +
                        "}," +
                        "\"max-age\": 10," +
                        "\"expose\":" +
                        "{" +
                            "\"headers\": [ \"x-custom-header\" ]" +
                        "}" +
                    "}," +
                    "\"authorization\":" +
                    "{" +
                        "\"test0\":" +
                        "{" +
                            "\"credentials\":" +
                            "{" +
                                "\"headers\":" +
                                "{" +
                                    "\"authorization\":\"Bearer {credentials}\"" +
                                "}" +
                            "}" +
                        "}" +
                    "}," +
                    "\"overrides\":" +
                    "{" +
                        "\":authority\": \"example.com:443\"" +
                    "}," +
                    "\"requests\":" +
                    "[" +
                        "{" +
                            "\"path\": \"/hello\"," +
                            "\"method\": \"GET\"," +
                            "\"content-type\": " +
                            "[" +
                                "\"application/json\"" +
                            "]" +
                        "}" +
                    "]" +
                "}";

        HttpOptionsConfig options = jsonb.fromJson(text, HttpOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.versions, equalTo(EnumSet.allOf(HttpVersion.class)));
        assertThat(options.access, not(nullValue()));
        assertThat(options.access.allow, not(nullValue()));
        assertThat(options.access.allow.origins, equalTo(singleton("https://example.com:9090")));
        assertThat(options.access.allow.methods, equalTo(singleton("DELETE")));
        assertThat(options.access.allow.headers, equalTo(singleton("x-api-key")));
        assertTrue(options.access.allow.credentials);
        assertThat(options.access.maxAge, equalTo(Duration.ofSeconds(10)));
        assertThat(options.access.expose, not(nullValue()));
        assertThat(options.access.expose.headers, equalTo(singleton("x-custom-header")));
        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("test0"));
        assertThat(options.authorization.credentials, not(nullValue()));
        assertThat(options.authorization.credentials.headers, not(nullValue()));
        assertThat(options.authorization.credentials.headers, hasSize(1));
        assertThat(options.authorization.credentials.headers.get(0), not(nullValue()));
        assertThat(options.authorization.credentials.parameters, nullValue());
        assertThat(options.authorization.credentials.cookies, nullValue());
        assertThat(options.overrides, equalTo(singletonMap(new String8FW(":authority"), new String16FW("example.com:443"))));
        assertThat(options.requests.get(0).path, equalTo("/hello"));
        assertThat(options.requests.get(0).method, equalTo(HttpRequest.Method.GET));
        assertThat(options.requests.get(0).contentType.get(0), equalTo("application/json"));
    }

    @Test
    public void shouldWriteOptions()
    {
        HttpRequest request = new HttpRequest("/hello", HttpRequest.Method.GET, List.of("application/json"),
            ValidatorTypeConfig.STRING);
        HttpOptionsConfig options = HttpOptionsConfig.builder()
            .inject(identity())
            .version(HttpVersion.HTTP_1_1)
            .version(HttpVersion.HTTP_2)
            .override(new String8FW(":authority"), new String16FW("example.com:443"))
            .access()
                .inject(identity())
                .policy(CROSS_ORIGIN)
                .allow()
                    .inject(identity())
                    .origin("https://example.com:9090")
                    .method("DELETE")
                    .header("x-api-key")
                    .credentials(true)
                    .build()
                .maxAge(Duration.ofSeconds(10))
                .expose()
                    .inject(identity())
                    .header("x-custom-header")
                    .build()
                .build()
            .authorization()
                .name("test0")
                .credentials()
                    .inject(identity())
                    .header()
                        .inject(identity())
                        .name("authorization")
                        .pattern("Bearer {credentials}")
                        .build()
                    .build()
                .build()
            .requests(List.of(request))
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                    "{" +
                        "\"versions\":" +
                        "[" +
                            "\"http/1.1\"," +
                            "\"h2\"" +
                        "]," +
                        "\"access-control\":" +
                        "{" +
                            "\"policy\":\"cross-origin\"," +
                            "\"allow\":" +
                            "{" +
                                "\"origins\":[\"https://example.com:9090\"]," +
                                "\"methods\":[\"DELETE\"]," +
                                "\"headers\":[\"x-api-key\"]," +
                                "\"credentials\":true" +
                            "}," +
                            "\"max-age\":10," +
                            "\"expose\":" +
                            "{" +
                                "\"headers\":[\"x-custom-header\"]" +
                            "}" +
                        "}," +
                        "\"authorization\":" +
                        "{" +
                            "\"test0\":" +
                            "{" +
                                "\"credentials\":" +
                                "{" +
                                    "\"headers\":" +
                                    "{" +
                                        "\"authorization\":\"Bearer {credentials}\"" +
                                    "}" +
                                "}" +
                            "}" +
                        "}," +
                        "\"overrides\":" +
                        "{" +
                            "\":authority\":\"example.com:443\"" +
                        "}," +
                        "\"requests\":" +
                        "[" +
                            "{" +
                                "\"path\":\"/hello\"," +
                                "\"method\":\"GET\"," +
                                "\"content-type\":" +
                                "[" +
                                    "\"application/json\"" +
                                "]" +
                            "}" +
                        "]" +
                    "}"));
    }
}
