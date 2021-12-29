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

import io.aklivity.zilla.runtime.engine.config.Route;
import io.aklivity.zilla.runtime.engine.internal.config.ConditionAdapterTest.TestCondition;

public class RouteAdapterTest
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

        Route route = jsonb.fromJson(text, Route.class);

        assertThat(route, not(nullValue()));
        assertThat(route.exit, equalTo("test"));
        assertThat(route.when, hasSize(1));
        assertThat(route.when, contains(instanceOf(TestCondition.class)));
    }


    @Test
    public void shouldWriteRouteWhenMatch()
    {
        Route route = new Route("test", singletonList(new TestCondition("test")));

        String text = jsonb.toJson(route);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"exit\":\"test\",\"when\":[{\"match\":\"test\"}]}"));
    }

    @Test
    public void shouldWriteRoute()
    {
        Route route = new Route("test");

        String text = jsonb.toJson(route);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"exit\":\"test\"}"));
    }
}
