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
package io.aklivity.zilla.config.binding.agui.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.agui.AguiAuthorizationConfig;
import io.aklivity.zilla.config.binding.agui.AguiOptionsConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class AguiOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new AguiOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptionsWithoutAuthorization()
    {
        String text = "{}";

        AguiOptionsConfig options = (AguiOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, nullValue());
    }

    @Test
    public void shouldReadOptionsWithAuthorizationDefaultCredentials()
    {
        String text =
                """
                authorization:
                  oauth: {}
                """;

        AguiOptionsConfig options = (AguiOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("oauth"));
        assertThat(options.authorization.credentials, equalTo("Bearer {credentials}"));
    }

    @Test
    public void shouldReadOptionsWithAuthorizationCredentials()
    {
        String text =
                """
                authorization:
                  oauth:
                    credentials: Token {credentials}
                """;

        AguiOptionsConfig options = (AguiOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization.name, equalTo("oauth"));
        assertThat(options.authorization.credentials, equalTo("Token {credentials}"));
    }

    @Test
    public void shouldWriteOptionsWithoutAuthorization()
    {
        AguiOptionsConfig options = AguiOptionsConfig.builder()
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{}\n"));
    }

    @Test
    public void shouldWriteOptionsWithAuthorization()
    {
        AguiOptionsConfig options = AguiOptionsConfig.builder()
                .authorization()
                    .name("oauth")
                    .credentials("Bearer {credentials}")
                    .build()
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo(
                """
                authorization:
                  oauth:
                    credentials: "Bearer {credentials}"
                """));
    }

    @Test
    public void shouldWriteOptionsWithAuthorizationWithoutCredentials()
    {
        AguiOptionsConfig options = AguiOptionsConfig.builder()
                .authorization()
                    .name("oauth")
                    .build()
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo(
                """
                authorization:
                  oauth: {}
                """));
    }

    @Test
    public void shouldWriteOptionsWithAuthorizationWithoutName()
    {
        AguiOptionsConfig options = AguiOptionsConfig.builder()
                .authorization(AguiAuthorizationConfig.builder().credentials("Bearer {credentials}").build())
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{}\n"));
    }
}
