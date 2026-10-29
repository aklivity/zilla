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
package io.aklivity.zilla.config.binding.a2a.openapi.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiConditionConfig;

public class A2aOpenapiConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new A2aOpenapiConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        A2aOpenapiConditionConfig condition = jsonb.fromJson("{\"skill\":\"list_pets\"}", A2aOpenapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.skill, equalTo("list_pets"));
    }

    @Test
    public void shouldReadEmptyCondition()
    {
        A2aOpenapiConditionConfig condition = jsonb.fromJson("{}", A2aOpenapiConditionConfig.class);

        assertThat(condition.skill, nullValue());
    }

    @Test
    public void shouldWriteCondition()
    {
        A2aOpenapiConditionConfig condition = A2aOpenapiConditionConfig.builder()
            .skill("list_pets")
            .build();

        assertThat(jsonb.toJson(condition), equalTo("{\"skill\":\"list_pets\"}"));
    }
}
