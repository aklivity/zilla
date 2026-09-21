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
package io.aklivity.zilla.config.binding.llm.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.llm.LlmOptionsConfig;

public class LlmOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new LlmOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"dialect\": \"openai\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.dialect, equalTo("openai"));
    }

    @Test
    public void shouldWriteOptions()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .dialect("openai")
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"dialect\":\"openai\"}"));
    }

    @Test
    public void shouldReadOptionsWithoutDialect()
    {
        String text = "{}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.dialect, nullValue());
    }

    @Test
    public void shouldWriteOptionsWithoutDialect()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{}"));
    }

    @Test
    public void shouldReadServerOption()
    {
        String text =
                "{" +
                    "\"server\": \"http://localhost:11434\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server.scheme, equalTo("http"));
        assertThat(options.server.host, equalTo("localhost"));
        assertThat(options.server.port, equalTo(11434));
        assertThat(options.server.path, equalTo("/v1"));
    }

    @Test
    public void shouldWriteServerOption()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .server()
                .scheme("http")
                .host("localhost")
                .port(11434)
                .path("/v1")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"server\":\"http://localhost:11434/v1\"}"));
    }

    @Test
    public void shouldReadOptionsWithoutServer()
    {
        String text =
                "{" +
                    "\"dialect\": \"openai\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server, nullValue());
    }

    @Test
    public void shouldWriteOptionsWithoutServer()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .dialect("openai")
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{\"dialect\":\"openai\"}"));
    }

    @Test
    public void shouldReadServerOptionWithCustomPath()
    {
        String text =
                "{" +
                    "\"server\": \"http://localhost:11434/aicomp/v1\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server.path, equalTo("/aicomp/v1"));
    }

    @Test
    public void shouldWriteServerOptionWithCustomPath()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .server()
                .scheme("http")
                .host("localhost")
                .port(11434)
                .path("/aicomp/v1")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"server\":\"http://localhost:11434/aicomp/v1\"}"));
    }

    @Test
    public void shouldReadDialectAndServerOptions()
    {
        String text =
                "{" +
                    "\"dialect\": \"openai\"," +
                    "\"server\": \"https://example.com:8080\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.dialect, equalTo("openai"));
        assertThat(options.server.scheme, equalTo("https"));
        assertThat(options.server.host, equalTo("example.com"));
        assertThat(options.server.port, equalTo(8080));
        assertThat(options.server.path, equalTo("/v1"));
    }

    @Test
    public void shouldReadServerOptionWithDefaultHttpsPort()
    {
        String text =
                "{" +
                    "\"server\": \"https://example.com\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server.port, equalTo(443));
    }

    @Test
    public void shouldReadServerOptionWithDefaultHttpPort()
    {
        String text =
                "{" +
                    "\"server\": \"http://example.com\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server.port, equalTo(80));
    }

    @Test
    public void shouldWriteDialectAndServerOptions()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .dialect("openai")
            .server()
                .scheme("https")
                .host("example.com")
                .port(8080)
                .path("/v1")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{\"dialect\":\"openai\",\"server\":\"https://example.com:8080/v1\"}"));
    }

    @Test
    public void shouldReadMalformedServerOptionAsAbsent()
    {
        String text =
                "{" +
                    "\"server\": \"not-a-host-and-port\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server, nullValue());
    }

    @Test
    public void shouldReadInvalidServerUriAsAbsent()
    {
        String text =
                "{" +
                    "\"server\": \"http://[invalid\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server, nullValue());
    }

    @Test
    public void shouldReadOptionsWithAuthorizationDefaultCredentials()
    {
        String text =
                "{" +
                    "\"authorization\": {" +
                        "\"test0\": {}" +
                    "}" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("test0"));
        assertThat(options.authorization.credentials, equalTo("Bearer {credentials}"));
    }

    @Test
    public void shouldReadOptionsWithAuthorizationExplicitCredentials()
    {
        String text =
                "{" +
                    "\"authorization\": {" +
                        "\"test0\": {" +
                            "\"credentials\": \"{credentials}\"" +
                        "}" +
                    "}" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("test0"));
        assertThat(options.authorization.credentials, equalTo("{credentials}"));
    }

    @Test
    public void shouldReadOptionsWithoutAuthorization()
    {
        String text = "{}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, nullValue());
    }

    @Test
    public void shouldWriteOptionsWithAuthorization()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .authorization()
                .name("test0")
                .credentials("Bearer {credentials}")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{\"authorization\":{\"test0\":{\"credentials\":\"Bearer {credentials}\"}}}"));
    }
}
