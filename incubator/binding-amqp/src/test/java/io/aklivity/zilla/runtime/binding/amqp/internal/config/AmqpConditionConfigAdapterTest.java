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
package io.aklivity.zilla.runtime.binding.amqp.internal.config;

import static io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpCapabilities.RECEIVE_ONLY;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpCapabilities.SEND_ONLY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class AmqpConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new AmqpConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"address\": \"test\"," +
                    "\"capabilities\": \"send_only\"" +
                "}";

        AmqpConditionConfig condition = jsonb.fromJson(text, AmqpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.address, equalTo("test"));
        assertThat(condition.capabilities, equalTo(SEND_ONLY));
    }

    @Test
    public void shouldWriteCondition()
    {
        AmqpConditionConfig condition = new AmqpConditionConfig("test", RECEIVE_ONLY);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"address\":\"test\",\"capabilities\":\"receive_only\"}"));
    }
}
