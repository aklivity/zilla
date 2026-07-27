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
package io.aklivity.zilla.config.engine.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.test.internal.binding.config.TestConditionConfig;

public class ConditionConfigAdapterTest
{
    private ConditionConfigAdapter adapter;
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        adapter = new ConditionConfigAdapter(new EngineInfo());
        adapter.adaptType("test");
        JsonbConfig config = new JsonbConfig()
                .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"match\": \"test\"" +
                "}";

        ConditionConfig condition = jsonb.fromJson(text, ConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(((TestConditionConfig) condition).match, equalTo("test"));
    }

    @Test
    public void shouldWriteCondition()
    {
        ConditionConfig condition = new TestConditionConfig("test");

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"match\":\"test\"}"));
    }

    @Test
    public void shouldReadNullWhenNotAdapting()
    {
        String text =
                "{" +
                    "\"match\": \"test\"" +
                "}";

        adapter.adaptType(null);
        ConditionConfig condition = jsonb.fromJson(text, ConditionConfig.class);

        assertThat(condition, nullValue());
    }

    @Test
    public void shouldWriteNullWhenNotAdapting()
    {
        ConditionConfig condition = new TestConditionConfig("test");

        adapter.adaptType(null);
        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("null"));
    }
}
