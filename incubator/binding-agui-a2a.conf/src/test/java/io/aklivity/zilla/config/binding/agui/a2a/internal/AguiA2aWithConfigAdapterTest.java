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
package io.aklivity.zilla.config.binding.agui.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aWithConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class AguiA2aWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new AguiA2aWithConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadWithAgentAndIdentity()
    {
        String yaml =
            """
            agent: agent1
            identity: "${guarded['test0'].identity}"
            """;

        AguiA2aWithConfig with = jsonb.fromJson(yaml, AguiA2aWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.agent, equalTo("agent1"));
        assertThat(with.identity, equalTo("${guarded['test0'].identity}"));
    }

    @Test
    public void shouldReadWithAgentOnly()
    {
        AguiA2aWithConfig with = jsonb.fromJson("agent: agent1", AguiA2aWithConfig.class);

        assertThat(with.agent, equalTo("agent1"));
        assertThat(with.identity, nullValue());
    }

    @Test
    public void shouldWriteWithAgentAndIdentity()
    {
        AguiA2aWithConfig with = AguiA2aWithConfig.builder()
            .agent("agent1")
            .identity("user0")
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, containsString("agent: agent1"));
        assertThat(text, containsString("identity: user0"));
    }

    @Test
    public void shouldRoundTripWithAgentAndIdentity()
    {
        String yaml =
            """
            agent: agent1
            identity: user0
            """;

        AguiA2aWithConfig with = jsonb.fromJson(yaml, AguiA2aWithConfig.class);
        AguiA2aWithConfig again = jsonb.fromJson(jsonb.toJson(with), AguiA2aWithConfig.class);

        assertThat(again.agent, equalTo(with.agent));
        assertThat(again.identity, equalTo(with.identity));
    }
}
