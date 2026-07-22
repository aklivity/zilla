/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.config.binding.openapi.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.openapi.OpenapiConditionConfig;
import io.aklivity.zilla.config.binding.openapi.OpenapiConditionServerConfig;

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

        assertThat(condition.matches("test", "listPets", null), equalTo(true));
        assertThat(condition.matches("test", "createPets", null), equalTo(false));
    }

    @Test
    public void shouldMatchTag()
    {
        OpenapiConditionConfig condition = new OpenapiConditionConfig("test", null, "admin");

        assertThat(condition.matches("test", "listPets", List.of("admin")), equalTo(true));
        assertThat(condition.matches("test", "listPets", List.of("pets")), equalTo(false));
    }

    @Test
    public void shouldReadConditionWithServers()
    {
        String text =
            "{" +
                "\"spec\":\"test\"," +
                "\"servers\":[{\"url\":\"http://localhost:9090/prod\"}]" +
            "}";

        OpenapiConditionConfig condition = jsonb.fromJson(text, OpenapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.spec, equalTo("test"));
        assertThat(condition.servers, hasSize(1));
    }

    @Test
    public void shouldWriteConditionWithServers()
    {
        OpenapiConditionConfig condition = new OpenapiConditionConfig("test", null, null,
            List.of(new OpenapiConditionServerConfig("http://localhost:9090/prod")));

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                    "\"spec\":\"test\"," +
                    "\"servers\":[{\"url\":\"http://localhost:9090/prod\"}]" +
                "}"));
    }
}
