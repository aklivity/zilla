/*
 * Copyright 2021-2023 Aklivity Inc
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

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

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
                "\"subject\":\"test\"," +
                "\"operation-id\":\"testOperationId\"" +
            "}";

        AsyncapiConditionConfig condition = jsonb.fromJson(text, AsyncapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.subject, equalTo("test"));
        assertThat(condition.operationId, equalTo("testOperationId"));
    }

    @Test
    public void shouldWriteCondition()
    {
        AsyncapiConditionConfig condition = new AsyncapiConditionConfig("test", "testOperationId");

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                    "\"subject\":\"test\"," +
                    "\"operation-id\":\"testOperationId\"" +
                "}"));
    }
}
