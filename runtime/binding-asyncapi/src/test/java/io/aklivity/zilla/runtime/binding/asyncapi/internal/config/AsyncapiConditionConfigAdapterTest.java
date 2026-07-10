/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiParser;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiServerConfig;
import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiView;

public class AsyncapiConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new AsyncapiConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
            "{" +
                "\"spec\":\"test\"," +
                "\"operation\":\"testOperationId\"" +
            "}";

        AsyncapiConditionConfig condition = jsonb.fromJson(text, AsyncapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.spec, equalTo("test"));
        assertThat(condition.operation, equalTo("testOperationId"));
    }

    @Test
    public void shouldReadConditionWithTag()
    {
        String text =
            "{" +
                "\"spec\":\"test\"," +
                "\"tag\":\"admin\"" +
            "}";

        AsyncapiConditionConfig condition = jsonb.fromJson(text, AsyncapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.spec, equalTo("test"));
        assertThat(condition.tag, equalTo("admin"));
    }

    @Test
    public void shouldWriteCondition()
    {
        AsyncapiConditionConfig condition = new AsyncapiConditionConfig("test", "testOperationId", null);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                    "\"spec\":\"test\"," +
                    "\"operation\":\"testOperationId\"" +
                "}"));
    }

    @Test
    public void shouldWriteConditionWithTag()
    {
        AsyncapiConditionConfig condition = new AsyncapiConditionConfig("test", null, "admin");

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                    "\"spec\":\"test\"," +
                    "\"tag\":\"admin\"" +
                "}"));
    }

    @Test
    public void shouldMatchOperationGlob()
    {
        AsyncapiConditionConfig condition = new AsyncapiConditionConfig("test", "list*", null);

        assertThat(condition.matches("test", "listPets", null, null), equalTo(true));
        assertThat(condition.matches("test", "createPets", null, null), equalTo(false));
    }

    @Test
    public void shouldMatchTag()
    {
        AsyncapiConditionConfig condition = new AsyncapiConditionConfig("test", null, "admin");

        assertThat(condition.matches("test", "listPets", List.of("admin"), null), equalTo(true));
        assertThat(condition.matches("test", "listPets", List.of("pets"), null), equalTo(false));
    }

    @Test
    public void shouldReadConditionWithServers()
    {
        String text =
            "{" +
                "\"spec\":\"test\"," +
                "\"servers\":[{\"name\":\"prod\",\"url\":\"http://localhost:9090/prod\"}]" +
            "}";

        AsyncapiConditionConfig condition = jsonb.fromJson(text, AsyncapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.spec, equalTo("test"));
        assertThat(condition.servers, not(nullValue()));
        assertThat(condition.servers.size(), equalTo(1));
        assertThat(condition.servers.get(0).name, equalTo("prod"));
        assertThat(condition.servers.get(0).url, equalTo("http://localhost:9090/prod"));
    }

    @Test
    public void shouldWriteConditionWithServers()
    {
        AsyncapiConditionConfig condition = new AsyncapiConditionConfig("test", null, null,
            List.of(new AsyncapiConditionServerConfig("prod", "http://localhost:9090/prod")));

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                    "\"spec\":\"test\"," +
                    "\"servers\":[{\"name\":\"prod\",\"url\":\"http://localhost:9090/prod\"}]" +
                "}"));
    }

    @Test
    public void shouldMatchServerByName() throws Exception
    {
        List<AsyncapiServerView> prodOnly = serverViews().stream()
            .filter(s -> "prod".equals(s.name))
            .toList();

        AsyncapiConditionConfig condition = new AsyncapiConditionConfig("test", null, null,
            List.of(new AsyncapiConditionServerConfig("prod", null)));

        assertThat(condition.matches("test", "listPets", null, prodOnly), equalTo(true));

        AsyncapiConditionConfig other = new AsyncapiConditionConfig("test", null, null,
            List.of(new AsyncapiConditionServerConfig("qa", null)));

        assertThat(other.matches("test", "listPets", null, prodOnly), equalTo(false));
    }

    @Test
    public void shouldMatchServerByUrl() throws Exception
    {
        List<AsyncapiServerView> prodOnly = serverViews().stream()
            .filter(s -> "prod".equals(s.name))
            .toList();

        AsyncapiConditionConfig condition = new AsyncapiConditionConfig("test", null, null,
            List.of(new AsyncapiConditionServerConfig(null, "http://localhost:9090/prod")));

        assertThat(condition.matches("test", "listPets", null, prodOnly), equalTo(true));

        AsyncapiConditionConfig other = new AsyncapiConditionConfig("test", null, null,
            List.of(new AsyncapiConditionServerConfig(null, "http://localhost:8080/qa")));

        assertThat(other.matches("test", "listPets", null, prodOnly), equalTo(false));
    }

    @Test
    public void shouldMatchAnyServerWhenOmitted() throws Exception
    {
        AsyncapiConditionConfig condition = new AsyncapiConditionConfig("test", null, null);

        assertThat(condition.matches("test", "listPets", null, serverViews()), equalTo(true));
        assertThat(condition.matches("test", "listPets", null, null), equalTo(true));
    }

    private static List<AsyncapiServerView> serverViews() throws Exception
    {
        Asyncapi model = new AsyncapiParser().parse("""
            asyncapi: 2.6.0
            info:
              title: Test API
              version: 0.1.0
            servers:
              prod:
                url: http://localhost:9090/prod
                protocol: http
              qa:
                url: http://localhost:8080/qa
                protocol: http
            channels: {}
            """);

        AsyncapiView view = AsyncapiView.of(model, List.of(AsyncapiServerConfig.builder().build()));

        return view.servers;
    }
}
