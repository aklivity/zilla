/*
 * Copyright 2021-2023 Aklivity Inc.
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

import java.util.function.Function;

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
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class ConditionConfigAdapterTest
{
    private ConditionAdapter adapter;
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        adapter = new ConditionAdapter();
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

    public static final class TestConditionConfig extends ConditionConfig
    {
        public final String match;

        public static TestConditionConfigBuilder<TestConditionConfig> builder()
        {
            return new TestConditionConfigBuilder<>(TestConditionConfig.class::cast);
        }

        public static <T> TestConditionConfigBuilder<T> builder(
            Function<ConditionConfig, T> mapper)
        {
            return new TestConditionConfigBuilder<>(mapper);
        }

        TestConditionConfig(
            String match)
        {
            this.match = match;
        }
    }

    public static final class TestConditionConfigBuilder<T> implements ConfigBuilder<T>
    {
        private final Function<ConditionConfig, T> mapper;

        private String match;

        TestConditionConfigBuilder(
            Function<ConditionConfig, T> mapper)
        {
            this.mapper = mapper;
        }

        public TestConditionConfigBuilder<T> match(
            String match)
        {
            this.match = match;
            return this;
        }

        @Override
        public T build()
        {
            return mapper.apply(new TestConditionConfig(match));
        }
    }

    public static final class TestConditionConfigAdapter implements ConditionConfigAdapterSpi
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
            TestConditionConfig testCondition = (TestConditionConfig) condition;

            JsonObjectBuilder object = Json.createObjectBuilder();

            object.add(MATCH_NAME, testCondition.match);

            return object.build();
        }

        @Override
        public ConditionConfig adaptFromJson(
            JsonObject object)
        {
            String match = object.getString(MATCH_NAME);

            return new TestConditionConfig(match);
        }
    }
}
