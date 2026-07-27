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
import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.config.engine.test.internal.binding.config.TestWithConfig;

public class WithConfigAdapterTest
{
    private WithConfigAdapter adapter;
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        adapter = new WithConfigAdapter(new EngineInfo());
        adapter.adaptType("test");
        JsonbConfig config = new JsonbConfig()
                .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWith()
    {
        String text =
                "{" +
                    "\"name\": \"test\"" +
                "}";

        WithConfig with = jsonb.fromJson(text, WithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(((TestWithConfig) with).name, equalTo("test"));
    }

    @Test
    public void shouldWriteWith()
    {
        ConditionConfig condition = new TestCondition("test");

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"match\":\"test\"}"));
    }

    @Test
    public void shouldReadNullWhenNotAdapting()
    {
        String text =
                "{" +
                    "\"name\": \"test\"" +
                "}";

        adapter.adaptType(null);
        WithConfig with = jsonb.fromJson(text, WithConfig.class);

        assertThat(with, nullValue());
    }

    @Test
    public void shouldWriteNullWhenNotAdapting()
    {
        WithConfig with = new TestWithConfig("test");

        adapter.adaptType(null);
        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("null"));
    }

    public static final class TestCondition extends ConditionConfig
    {
        public final String match;

        public TestCondition(
            String match)
        {
            this.match = match;
        }
    }
}
