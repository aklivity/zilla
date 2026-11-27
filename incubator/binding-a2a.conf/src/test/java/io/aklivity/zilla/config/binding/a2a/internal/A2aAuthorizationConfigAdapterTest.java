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
package io.aklivity.zilla.config.binding.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aAuthorizationConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aAuthorizationConfigAdapterTest
{
    private Jsonb jsonb;
    private Jsonb jsonbWithDefault;

    @Before
    public void initJson()
    {
        jsonb = jsonb(new A2aAuthorizationConfigAdapter(null));
        jsonbWithDefault = jsonb(new A2aAuthorizationConfigAdapter("Bearer {credentials}"));
    }

    @Test
    public void shouldReadAuthorizationWithCredentials()
    {
        String text =
            """
            test0:
              credentials: "{token}"
            """;

        A2aAuthorizationConfig authorization = jsonb.fromJson(text, A2aAuthorizationConfig.class);

        assertThat(authorization.name, equalTo("test0"));
        assertThat(authorization.credentials, equalTo("{token}"));
    }

    @Test
    public void shouldReadAuthorizationWithoutCredentials()
    {
        A2aAuthorizationConfig authorization = jsonb.fromJson("test0: {}", A2aAuthorizationConfig.class);

        assertThat(authorization.name, equalTo("test0"));
        assertThat(authorization.credentials, nullValue());
    }

    @Test
    public void shouldReadAuthorizationWithDefaultCredentials()
    {
        A2aAuthorizationConfig authorization = jsonbWithDefault.fromJson("test0: {}", A2aAuthorizationConfig.class);

        assertThat(authorization.name, equalTo("test0"));
        assertThat(authorization.credentials, equalTo("Bearer {credentials}"));
    }

    @Test
    public void shouldWriteAuthorizationWithCredentials()
    {
        A2aAuthorizationConfig authorization = A2aAuthorizationConfig.builder()
            .name("test0")
            .credentials("{token}")
            .build();

        assertThat(jsonb.toJson(authorization), equalTo(
            """
            test0:
              credentials: "{token}"
            """));
    }

    @Test
    public void shouldWriteAuthorizationWithoutCredentials()
    {
        A2aAuthorizationConfig authorization = A2aAuthorizationConfig.builder()
            .name("test0")
            .build();

        assertThat(jsonb.toJson(authorization), equalTo("test0: {}\n"));
    }

    private static Jsonb jsonb(
        A2aAuthorizationConfigAdapter adapter)
    {
        return JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(new JsonbConfig().withAdapters(adapter))
            .build();
    }
}
