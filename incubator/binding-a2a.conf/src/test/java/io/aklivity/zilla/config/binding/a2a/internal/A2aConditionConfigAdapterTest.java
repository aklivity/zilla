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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aConditionConfig;

public class A2aConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new A2aConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadSkillCondition()
    {
        A2aConditionConfig condition = jsonb.fromJson("{\"skill\":\"forecast\"}", A2aConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.skill, contains("forecast"));
        assertThat(condition.agent, nullValue());
    }

    @Test
    public void shouldReadSkillArrayCondition()
    {
        A2aConditionConfig condition = jsonb.fromJson("{\"skill\":[\"forecast\",\"alerts\"]}", A2aConditionConfig.class);

        assertThat(condition.skill, contains("forecast", "alerts"));
    }

    @Test
    public void shouldReadAgentCondition()
    {
        A2aConditionConfig condition = jsonb.fromJson("{\"agent\":\"weather\"}", A2aConditionConfig.class);

        assertThat(condition.agent, contains("weather"));
        assertThat(condition.skill, nullValue());
    }

    @Test
    public void shouldReadAgentArrayCondition()
    {
        A2aConditionConfig condition = jsonb.fromJson("{\"agent\":[\"travel\",\"flights\"]}", A2aConditionConfig.class);

        assertThat(condition.agent, contains("travel", "flights"));
    }

    @Test
    public void shouldWriteSkillCondition()
    {
        A2aConditionConfig condition = A2aConditionConfig.builder()
            .skill(List.of("forecast"))
            .build();

        assertThat(jsonb.toJson(condition), equalTo("{\"skill\":\"forecast\"}"));
    }

    @Test
    public void shouldWriteAgentArrayCondition()
    {
        A2aConditionConfig condition = A2aConditionConfig.builder()
            .agent(List.of("travel", "flights"))
            .build();

        assertThat(jsonb.toJson(condition), equalTo("{\"agent\":[\"travel\",\"flights\"]}"));
    }
}
