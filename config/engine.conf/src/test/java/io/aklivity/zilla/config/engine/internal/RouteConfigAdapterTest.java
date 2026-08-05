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

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.test.internal.binding.TestBindingInfo;
import io.aklivity.zilla.config.engine.test.internal.binding.config.TestConditionConfig;
import io.aklivity.zilla.config.engine.test.internal.binding.config.TestWithConfig;

public class RouteConfigAdapterTest
{
    private RouteConfigAdapter adapter;

    @Before
    public void initJson()
    {
        adapter = new RouteConfigAdapter(new TestBindingInfo());
    }

    @Test
    public void shouldReadRoute() throws Exception
    {
        String text =
                "{" +
                    "\"exit\": \"test\"" +
                "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        RouteConfig route = adapter.adaptFromJson(0, object);

        assertThat(route, not(nullValue()));
        assertThat(route.exit, equalTo("test"));
    }

    @Test
    public void shouldWriteRoute() throws Exception
    {
        RouteConfig route = RouteConfig.builder()
                .exit("test")
                .build();

        JsonObject object = adapter.adaptToJson(route);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"exit\":\"test\"}"));
    }

    @Test
    public void shouldReadRouteGuarded() throws Exception
    {
        String text =
                "{" +
                    "\"exit\": \"test\"," +
                    "\"guarded\": " +
                    "{" +
                        "\"test\": [ \"role\" ]" +
                    "}" +
                "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        RouteConfig route = adapter.adaptFromJson(0, object);

        assertThat(route, not(nullValue()));
        assertThat(route.exit, equalTo("test"));
        assertThat(route.guarded, hasSize(1));
        assertThat(route.guarded.get(0).name, equalTo("test"));
        assertThat(route.guarded.get(0).roles, equalTo(singletonList("role")));
    }

    @Test
    public void shouldWriteRouteGuarded() throws Exception
    {
        RouteConfig route = RouteConfig.builder()
                .inject(identity())
                .exit("test")
                .guarded()
                    .inject(identity())
                    .name("test")
                    .role("role")
                    .build()
                .build();

        JsonObject object = adapter.adaptToJson(route);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"exit\":\"test\",\"guarded\":{\"test\":[\"role\"]}}"));
    }

    @Test
    public void shouldReadRouteWhenMatch() throws Exception
    {
        String text =
                "{" +
                    "\"exit\": \"test\"," +
                    "\"when\":" +
                    "[" +
                      "{ \"match\": \"test\" }" +
                    "]" +
                "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        RouteConfig route = adapter.adaptFromJson(0, object);

        assertThat(route, not(nullValue()));
        assertThat(route.exit, equalTo("test"));
        assertThat(route.when, hasSize(1));
        assertThat(route.when, contains(instanceOf(TestConditionConfig.class)));
    }

    @Test
    public void shouldWriteRouteWhenMatch() throws Exception
    {
        RouteConfig route = RouteConfig.builder()
                .inject(identity())
                .exit("test")
                .when(TestConditionConfig::builder)
                    .inject(identity())
                    .match("test")
                    .build()
                .build();

        JsonObject object = adapter.adaptToJson(route);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"exit\":\"test\",\"when\":[{\"match\":\"test\"}]}"));
    }

    @Test
    public void shouldReadRouteWith() throws Exception
    {
        String text =
                "{" +
                    "\"exit\": \"test\"," +
                    "\"with\":" +
                    "{ \"name\": \"test\" }" +
                "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        RouteConfig route = adapter.adaptFromJson(0, object);

        assertThat(route, not(nullValue()));
        assertThat(route.exit, equalTo("test"));
        assertThat(route.with, instanceOf(TestWithConfig.class));
        assertThat(((TestWithConfig) route.with).name, equalTo("test"));
    }

    @Test
    public void shouldWriteRouteWith() throws Exception
    {
        RouteConfig route = RouteConfig.builder()
                .inject(identity())
                .exit("test")
                .with(new TestWithConfig("test"))
                .build();

        JsonObject object = adapter.adaptToJson(route);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"exit\":\"test\",\"with\":{\"name\":\"test\"}}"));
    }
}
