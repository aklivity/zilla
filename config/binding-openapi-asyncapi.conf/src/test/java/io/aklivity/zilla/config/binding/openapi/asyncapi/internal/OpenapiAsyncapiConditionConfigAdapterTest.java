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
package io.aklivity.zilla.config.binding.openapi.asyncapi.internal;

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

import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiAsyncapiConditionConfig;
import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiAsyncapiConditionServerConfig;

public class OpenapiAsyncapiConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new OpenapiAsyncapiConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
            "{" +
                "\"spec\":\"test\"," +
                "\"operation\":\"o-id\"" +
            "}";

        OpenapiAsyncapiConditionConfig condition = jsonb.fromJson(text, OpenapiAsyncapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.spec, equalTo("test"));
        assertThat(condition.operation, equalTo("o-id"));
    }

    @Test
    public void shouldWriteCondition()
    {
        OpenapiAsyncapiConditionConfig condition = new OpenapiAsyncapiConditionConfig("test", "o-id");

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                    "\"spec\":\"test\"," +
                    "\"operation\":\"o-id\"" +
                "}"));
    }

    @Test
    public void shouldReadConditionWithTagAndServers()
    {
        String text =
            "{" +
                "\"spec\":\"test\"," +
                "\"operation\":\"o-id\"," +
                "\"tag\":\"pets\"," +
                "\"servers\":[{\"url\":\"http://localhost:9090/prod\"}]" +
            "}";

        OpenapiAsyncapiConditionConfig condition = jsonb.fromJson(text, OpenapiAsyncapiConditionConfig.class);

        assertThat(condition.tag, equalTo("pets"));
        assertThat(condition.servers.size(), equalTo(1));
        assertThat(condition.servers.get(0).url, equalTo("http://localhost:9090/prod"));
    }

    @Test
    public void shouldWriteConditionWithTagAndServers()
    {
        OpenapiAsyncapiConditionConfig condition = new OpenapiAsyncapiConditionConfig("test", "o-id", "pets",
            List.of(new OpenapiAsyncapiConditionServerConfig("http://localhost:9090/prod")));

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                    "\"spec\":\"test\"," +
                    "\"operation\":\"o-id\"," +
                    "\"tag\":\"pets\"," +
                    "\"servers\":[{\"url\":\"http://localhost:9090/prod\"}]" +
                "}"));
    }
}
