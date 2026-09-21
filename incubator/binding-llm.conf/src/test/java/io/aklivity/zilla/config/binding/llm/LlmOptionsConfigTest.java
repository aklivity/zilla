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
package io.aklivity.zilla.config.binding.llm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.Test;

public class LlmOptionsConfigTest
{
    @Test
    public void shouldBuildViaCustomMapper()
    {
        String dialect = LlmOptionsConfig
            .builder(options -> ((LlmOptionsConfig) options).dialect)
            .dialect("openai")
            .build();

        assertThat(dialect, equalTo("openai"));
    }

    @Test
    public void shouldInjectBuilder()
    {
        LlmOptionsConfigBuilder<LlmOptionsConfig> builder = LlmOptionsConfig.builder();

        LlmOptionsConfigBuilder<LlmOptionsConfig> injected = builder.inject(identity -> identity);

        assertThat(injected, sameInstance(builder));
    }

    @Test
    public void shouldBuildServerViaNestedBuilder()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .server()
                .scheme("http")
                .host("localhost")
                .port(11434)
                .path("/v1")
                .build()
            .build();

        assertThat(options.server.host, equalTo("localhost"));
        assertThat(options.server.port, equalTo(11434));
        assertThat(options.server.path, equalTo("/v1"));
    }

    @Test
    public void shouldBuildServerViaSetter()
    {
        LlmServerConfig server = LlmServerConfig.builder()
            .scheme("http")
            .host("localhost")
            .port(11434)
            .path("/v1")
            .build();

        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .server(server)
            .build();

        assertThat(options.server, sameInstance(server));
    }

    @Test
    public void shouldConvertServerToString()
    {
        LlmServerConfig server = LlmServerConfig.builder()
            .scheme("http")
            .host("localhost")
            .port(11434)
            .path("/v1")
            .build();

        assertThat(server.toString(), equalTo("http://localhost:11434/v1"));
    }

    @Test
    public void shouldInjectServerBuilder()
    {
        LlmServerConfigBuilder<LlmServerConfig> builder = LlmServerConfig.builder();

        LlmServerConfigBuilder<LlmServerConfig> injected = builder.inject(identity -> identity);

        assertThat(injected, sameInstance(builder));
    }

    @Test
    public void shouldBuildAuthorizationViaCustomMapper()
    {
        String name = LlmAuthorizationConfig
            .builder(authorization -> ((LlmAuthorizationConfig) authorization).name)
            .name("test0")
            .credentials("Bearer {credentials}")
            .build();

        assertThat(name, equalTo("test0"));
    }

    @Test
    public void shouldInjectAuthorizationBuilder()
    {
        LlmAuthorizationConfigBuilder<LlmAuthorizationConfig> builder = LlmAuthorizationConfig.builder();

        LlmAuthorizationConfigBuilder<LlmAuthorizationConfig> injected = builder.inject(identity -> identity);

        assertThat(injected, sameInstance(builder));
    }
}
