/*
 * Copyright 2021-2021 Aklivity Inc.
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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.Condition;
import io.aklivity.zilla.runtime.engine.config.ConditionAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.With;
import io.aklivity.zilla.runtime.engine.config.WithAdapterSpi;

public class WithAdapterTest
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

        With with = jsonb.fromJson(text, With.class);

        assertThat(with, not(nullValue()));
        assertThat(((TestWith) with).name, equalTo("test"));
    }

    @Test
    public void shouldWriteWith()
    {
        Condition condition = new TestCondition("test");

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
        With with = jsonb.fromJson(text, With.class);

        assertThat(with, nullValue());
    }

    @Test
    public void shouldWriteNullWhenNotAdapting()
    {
        With with = new TestWith("test");

        adapter.adaptType(null);
        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("null"));
    }

    public static final class TestCondition extends Condition
    {
        public final String match;

        public TestCondition(
            String match)
        {
            this.match = match;
        }
    }

    public static final class TestConditionAdapter implements ConditionAdapterSpi
    {
        private static final String MATCH_NAME = "match";

        @Override
        public String type()
        {
            return "test";
        }

        @Override
        public JsonObject adaptToJson(
            Condition condition)
        {
            TestCondition testCondition = (TestCondition) condition;

            JsonObjectBuilder object = Json.createObjectBuilder();

            object.add(MATCH_NAME, testCondition.match);

            return object.build();
        }

        @Override
        public Condition adaptFromJson(
            JsonObject object)
        {
            String match = object.getString(MATCH_NAME);

            return new TestCondition(match);
        }
    }

    public static final class TestWith extends With
    {
        public final String name;

        public TestWith(
            String name)
        {
            this.name = name;
        }
    }

    public static final class TestWithAdapter implements WithAdapterSpi
    {
        private static final String NAME_NAME = "name";

        @Override
        public String type()
        {
            return "test";
        }

        @Override
        public JsonObject adaptToJson(
            With condition)
        {
            TestWith testWith = (TestWith) condition;

            JsonObjectBuilder object = Json.createObjectBuilder();

            object.add(NAME_NAME, testWith.name);

            return object.build();
        }

        @Override
        public With adaptFromJson(
            JsonObject object)
        {
            String name = object.getString(NAME_NAME);

            return new TestWith(name);
        }
    }
}
