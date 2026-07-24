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
package io.aklivity.zilla.config.binding.http.internal;

import static io.aklivity.zilla.config.binding.http.HttpPolicyConfig.CROSS_ORIGIN;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.EnumSet;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.http.HttpOptionsConfig;
import io.aklivity.zilla.config.binding.http.HttpRequestConfig;
import io.aklivity.zilla.config.binding.http.HttpVersion;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class HttpOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new HttpOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptions()
    {
        // GIVEN
        String yaml =
            """
            versions:
              - http/1.1
              - h2
            access-control:
              policy: cross-origin
              allow:
                origins:
                  - "https://example.com:9090"
                methods:
                  - DELETE
                headers:
                  - x-api-key
                credentials: true
              max-age: 10
              expose:
                headers:
                  - x-custom-header
            authorization:
              test0:
                credentials:
                  headers:
                    authorization: "Bearer {credentials}"
            overrides:
              ":authority": "example.com:443"
            requests:
              - path: /hello
                method: GET
                content-type:
                  - application/json
                headers:
                  content-type: test
                params:
                  path:
                    id: test
                  query:
                    index: test
                content: test
            """;

        // WHEN
        HttpOptionsConfig options = jsonb.fromJson(yaml, HttpOptionsConfig.class);

        // THEN
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
        assertThat(options.overrides, equalTo(singletonMap(":authority", "example.com:443")));
        HttpRequestConfig request = options.requests.get(0);
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
    }

    @Test
    public void shouldWriteOptions()
    {
        // GIVEN
        String expectedYaml =
            """
            versions:
              - http/1.1
              - h2
            access-control:
              policy: cross-origin
              allow:
                origins:
                  - "https://example.com:9090"
                methods:
                  - DELETE
                headers:
                  - x-api-key
                credentials: true
              max-age: 10
              expose:
                headers:
                  - x-custom-header
            authorization:
              test0:
                credentials:
                  headers:
                    authorization: "Bearer {credentials}"
            overrides:
              ":authority": "example.com:443"
            requests:
              - path: /hello
                method: GET
                content-type:
                  - application/json
                headers:
                  content-type: test
                params:
                  path:
                    id: test
                  query:
                    index: test
                content: test
            """;
        HttpOptionsConfig options = HttpOptionsConfig.builder()
            .inject(identity())
            .version(HttpVersion.HTTP_1_1)
            .version(HttpVersion.HTTP_2)
            .override(":authority", "example.com:443")
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
            .request()
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
                .build()
            .build();

        // WHEN
        String yaml = jsonb.toJson(options);

        // THEN
        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(expectedYaml));
    }
}
