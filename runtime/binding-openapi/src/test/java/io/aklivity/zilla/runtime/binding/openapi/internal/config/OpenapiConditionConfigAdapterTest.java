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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

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

public class OpenapiConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new OpenapiConditionConfigAdapter());
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

        OpenapiConditionConfig condition = jsonb.fromJson(text, OpenapiConditionConfig.class);

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

        OpenapiConditionConfig condition = jsonb.fromJson(text, OpenapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.spec, equalTo("test"));
        assertThat(condition.tag, equalTo("admin"));
    }

    @Test
    public void shouldWriteCondition()
    {
        OpenapiConditionConfig condition = new OpenapiConditionConfig("test", "testOperationId", null);

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
        OpenapiConditionConfig condition = new OpenapiConditionConfig("test", null, "admin");

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
        OpenapiConditionConfig condition = new OpenapiConditionConfig("test", "list*", null);

        assertThat(condition.matches("test", "listPets", null, null), equalTo(true));
        assertThat(condition.matches("test", "createPets", null, null), equalTo(false));
    }

    @Test
    public void shouldMatchTag()
    {
        OpenapiConditionConfig condition = new OpenapiConditionConfig("test", null, "admin");

        assertThat(condition.matches("test", "listPets", List.of("admin"), null), equalTo(true));
        assertThat(condition.matches("test", "listPets", List.of("pets"), null), equalTo(false));
    }

    @Test
    public void shouldReadConditionWithServers()
    {
        String text =
            "{" +
                "\"spec\":\"test\"," +
                "\"servers\":[{\"url\":\"https://api.example.com/v1\"}]" +
            "}";

        OpenapiConditionConfig condition = jsonb.fromJson(text, OpenapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.spec, equalTo("test"));
        assertThat(condition.servers, not(nullValue()));
        assertThat(condition.servers.size(), equalTo(1));
        assertThat(condition.servers.get(0).url, equalTo("https://api.example.com/v1"));
    }

    @Test
    public void shouldWriteConditionWithServers()
    {
        OpenapiConditionConfig condition = new OpenapiConditionConfig("test", null, null,
            List.of(new OpenapiConditionServerConfig("https://api.example.com/v1")));

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                    "\"spec\":\"test\"," +
                    "\"servers\":[{\"url\":\"https://api.example.com/v1\"}]" +
                "}"));
    }

    @Test
    public void shouldMatchServer()
    {
        OpenapiConditionConfig condition = new OpenapiConditionConfig("test", null, null,
            List.of(new OpenapiConditionServerConfig("https://api.example.com/v1")));

        assertThat(condition.matches("test", "listPets", null, "https://api.example.com/v1"), equalTo(true));
        assertThat(condition.matches("test", "listPets", null, "https://api.example.com/v2"), equalTo(false));
    }

    @Test
    public void shouldMatchAnyServerWhenOmitted()
    {
        OpenapiConditionConfig condition = new OpenapiConditionConfig("test", null, null);

        assertThat(condition.matches("test", "listPets", null, "https://api.example.com/v1"), equalTo(true));
    }
}
