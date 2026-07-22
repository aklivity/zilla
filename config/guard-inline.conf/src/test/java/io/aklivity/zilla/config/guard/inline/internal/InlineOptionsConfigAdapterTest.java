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
package io.aklivity.zilla.config.guard.inline.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.guard.inline.InlineOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class InlineOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new InlineOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptionsWithCredentials()
    {
        String yaml = "credentials: token";

        InlineOptionsConfig options = jsonb.fromJson(yaml, InlineOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.credentials, equalTo("token"));
    }

    @Test
    public void shouldReadOptionsWithIdentity()
    {
        String yaml = "identity: alice";

        InlineOptionsConfig options = jsonb.fromJson(yaml, InlineOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.identity, equalTo("alice"));
    }

    @Test
    public void shouldWriteOptionsWithCredentials()
    {
        InlineOptionsConfig options = new InlineOptionsConfig(null, "token", null);

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("credentials: token\n"));
    }

    @Test
    public void shouldWriteOptionsWithIdentity()
    {
        InlineOptionsConfig options = new InlineOptionsConfig("alice", null, null);

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("identity: alice\n"));
    }

    @Test
    public void shouldWriteOptionsWithNullFields()
    {
        InlineOptionsConfig options = new InlineOptionsConfig(null, null, null);

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("{}\n"));
    }

    @Test
    public void shouldReadOptionsWithFormat()
    {
        String yaml = "format: \"{identity}:{credentials}\"";

        InlineOptionsConfig options = jsonb.fromJson(yaml, InlineOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.format, equalTo("{identity}:{credentials}"));
    }

    @Test
    public void shouldWriteOptionsWithFormat()
    {
        InlineOptionsConfig options = new InlineOptionsConfig(null, null, "{identity}:{credentials}");

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("format: \"{identity}:{credentials}\"\n"));
    }
}
