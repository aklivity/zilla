/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public class WithConfigAdapterTest
{
    private WithAdapter adapter;
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        adapter = new WithAdapter();
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

    public static final class TestConditionAdapter implements ConditionConfigAdapterSpi
    {
        private static final String MATCH_NAME = "match";

        @Override
        public String type()
        {
            return "test";
        }

        @Override
        public JsonObject adaptToJson(
            ConditionConfig condition)
        {
            TestCondition testCondition = (TestCondition) condition;

            JsonObjectBuilder object = Json.createObjectBuilder();

            object.add(MATCH_NAME, testCondition.match);

            return object.build();
        }

        @Override
        public ConditionConfig adaptFromJson(
            JsonObject object)
        {
            String match = object.getString(MATCH_NAME);

            return new TestCondition(match);
        }
    }

    public static final class TestWithConfig extends WithConfig
    {
        public final String name;

        public TestWithConfig(
            String name)
        {
            this.name = name;
        }
    }

    public static final class TestWithConfigAdapter implements WithConfigAdapterSpi
    {
        private static final String NAME_NAME = "name";

        @Override
        public String type()
        {
            return "test";
        }

        @Override
        public JsonObject adaptToJson(
            WithConfig condition)
        {
            TestWithConfig testWith = (TestWithConfig) condition;

            JsonObjectBuilder object = Json.createObjectBuilder();

            object.add(NAME_NAME, testWith.name);

            return object.build();
        }

        @Override
        public WithConfig adaptFromJson(
            JsonObject object)
        {
            String name = object.getString(NAME_NAME);

            return new TestWithConfig(name);
        }
    }
}
