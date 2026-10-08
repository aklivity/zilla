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
package io.aklivity.zilla.config.embedding.openai.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.embedding.openai.OpenaiCredentialsConfig;
import io.aklivity.zilla.config.embedding.openai.OpenaiOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class OpenaiOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new OpenaiOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String yaml = """
            model: text-embedding-3-small
            credentials:
              api-key: test-key
            """;

        OpenaiOptionsConfig options = jsonb.fromJson(yaml, OpenaiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.model, equalTo("text-embedding-3-small"));
        assertThat(options.endpoint, equalTo("https://api.openai.com/v1/embeddings"));
        assertThat(options.credentials.apiKey, equalTo("test-key"));
    }

    @Test
    public void shouldReadOptionsWithEndpoint()
    {
        String yaml = """
            model: text-embedding-3-small
            endpoint: http://localhost:8000/v1/embeddings
            credentials:
              api-key: test-key
            """;

        OpenaiOptionsConfig options = jsonb.fromJson(yaml, OpenaiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.endpoint, equalTo("http://localhost:8000/v1/embeddings"));
    }

    @Test
    public void shouldWriteOptions()
    {
        OpenaiOptionsConfig options = OpenaiOptionsConfig.builder()
            .model("text-embedding-3-small")
            .credentials(OpenaiCredentialsConfig.builder()
                .apiKey("test-key")
                .build())
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("""
            model: text-embedding-3-small
            credentials:
              api-key: test-key
            """));
    }

    @Test
    public void shouldWriteOptionsWithEndpoint()
    {
        OpenaiOptionsConfig options = OpenaiOptionsConfig.builder()
            .model("text-embedding-3-small")
            .endpoint("http://localhost:8000/v1/embeddings")
            .credentials(OpenaiCredentialsConfig.builder()
                .apiKey("test-key")
                .build())
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, equalTo("""
            model: text-embedding-3-small
            endpoint: "http://localhost:8000/v1/embeddings"
            credentials:
              api-key: test-key
            """));
    }
}
