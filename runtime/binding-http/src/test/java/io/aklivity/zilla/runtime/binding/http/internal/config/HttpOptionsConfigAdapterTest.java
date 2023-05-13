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

import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpPolicyConfig.CROSS_ORIGIN;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.EnumSet;
import java.util.TreeSet;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpAllowConfig;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpExposeConfig;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAuthorizationConfig.HttpCredentialsConfig;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAuthorizationConfig.HttpPatternConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;

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
                    "}" +
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
    }

    @Test
    public void shouldWriteOptions()
    {
        HttpOptionsConfig options = new HttpOptionsConfig(
                new TreeSet<>(EnumSet.allOf(HttpVersion.class)),
                singletonMap(new String8FW(":authority"), new String16FW("example.com:443")),
                new HttpAccessControlConfig(
                    CROSS_ORIGIN,
                    new HttpAllowConfig(
                        singleton("https://example.com:9090"),
                        singleton("DELETE"),
                        singleton("x-api-key"),
                        true),
                    Duration.ofSeconds(10),
                    new HttpExposeConfig(
                        singleton("x-custom-header"))),
                new HttpAuthorizationConfig(
                    "test0",
                    new HttpCredentialsConfig(
                        singletonList(new HttpPatternConfig(
                            "authorization",
                            "Bearer {credentials}")))));

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
                        "}" +
                    "}"));
    }
}
