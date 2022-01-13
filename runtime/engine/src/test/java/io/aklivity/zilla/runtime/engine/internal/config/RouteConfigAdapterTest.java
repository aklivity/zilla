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
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.internal.config.ConditionConfigAdapterTest.TestConditionConfig;

public class RouteConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new RouteAdapter().adaptType("test"));
        jsonb = JsonbBuilder.create(config);
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
        RouteConfig route = new RouteConfig("test", singletonList(new TestConditionConfig("test")));

        String text = jsonb.toJson(route);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"exit\":\"test\",\"when\":[{\"match\":\"test\"}]}"));
    }

    @Test
    public void shouldWriteRoute()
    {
        RouteConfig route = new RouteConfig("test");

        String text = jsonb.toJson(route);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"exit\":\"test\"}"));
    }
}
