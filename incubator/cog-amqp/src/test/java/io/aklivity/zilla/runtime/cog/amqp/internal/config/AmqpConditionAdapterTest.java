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
package io.aklivity.zilla.runtime.cog.amqp.internal.config;

import static io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpCapabilities.RECEIVE_ONLY;
import static io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpCapabilities.SEND_ONLY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class AmqpConditionAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new AmqpConditionAdapter());
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

        AmqpCondition condition = jsonb.fromJson(text, AmqpCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.address, equalTo("test"));
        assertThat(condition.capabilities, equalTo(SEND_ONLY));
    }

    @Test
    public void shouldWriteCondition()
    {
        AmqpCondition condition = new AmqpCondition("test", RECEIVE_ONLY);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"address\":\"test\",\"capabilities\":\"receive_only\"}"));
    }
}
