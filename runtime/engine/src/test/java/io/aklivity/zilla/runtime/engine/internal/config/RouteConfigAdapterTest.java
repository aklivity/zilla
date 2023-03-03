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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.internal.config.ConditionConfigAdapterTest.TestConditionConfig;

public class RouteConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private ConfigAdapterContext context;

    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new RouteAdapter(context).adaptType("test"));
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadRoute()
    {
        String text =
                "{" +
                    "\"exit\": \"test\"" +
                "}";

        RouteConfig route = jsonb.fromJson(text, RouteConfig.class);

        assertThat(route, not(nullValue()));
        assertThat(route.exit, equalTo("test"));
    }

    @Test
    public void shouldWriteRoute()
    {
        RouteConfig route = new RouteConfig("test");

        String text = jsonb.toJson(route);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"exit\":\"test\"}"));
    }

    @Test
    public void shouldReadRouteGuarded()
    {
        String text =
                "{" +
                    "\"exit\": \"test\"," +
                    "\"guarded\": " +
                    "{" +
                        "\"test\": [ \"role\" ]" +
                    "}" +
                "}";

        RouteConfig route = jsonb.fromJson(text, RouteConfig.class);

        assertThat(route, not(nullValue()));
        assertThat(route.exit, equalTo("test"));
        assertThat(route.guarded, hasSize(1));
        assertThat(route.guarded.get(0).name, equalTo("test"));
        assertThat(route.guarded.get(0).roles, equalTo(singletonList("role")));
    }

    @Test
    public void shouldWriteRouteGuarded()
    {
        RouteConfig route = new RouteConfig("test", singletonList(new GuardedConfig("test", singletonList("role"))));

        String text = jsonb.toJson(route);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"exit\":\"test\",\"guarded\":{\"test\":[\"role\"]}}"));
    }

    @Test
    public void shouldReadRouteWhenMatch()
    {
        String text =
                "{" +
                    "\"exit\": \"test\"," +
                    "\"when\":" +
                    "[" +
                      "{ \"match\": \"test\" }" +
                    "]" +
                "}";

        RouteConfig route = jsonb.fromJson(text, RouteConfig.class);

        assertThat(route, not(nullValue()));
        assertThat(route.exit, equalTo("test"));
        assertThat(route.when, hasSize(1));
        assertThat(route.when, contains(instanceOf(TestConditionConfig.class)));
    }

    @Test
    public void shouldWriteRouteWhenMatch()
    {
        RouteConfig route = new RouteConfig("test", singletonList(new TestConditionConfig("test")), emptyList());

        String text = jsonb.toJson(route);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"exit\":\"test\",\"when\":[{\"match\":\"test\"}]}"));
    }
}
