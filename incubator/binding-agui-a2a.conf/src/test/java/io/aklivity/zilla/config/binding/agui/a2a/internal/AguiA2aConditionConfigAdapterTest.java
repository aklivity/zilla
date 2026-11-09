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

import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aConditionConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class AguiA2aConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new AguiA2aConditionConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadCondition()
    {
        AguiA2aConditionConfig condition = jsonb.fromJson("agent: agent0", AguiA2aConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.agent, equalTo("agent0"));
    }

    @Test
    public void shouldWriteCondition()
    {
        AguiA2aConditionConfig condition = AguiA2aConditionConfig.builder()
            .agent("agent0")
            .build();

        assertThat(jsonb.toJson(condition), containsString("agent: agent0"));
    }
}
