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
package io.aklivity.zilla.config.binding.risingwave.internal;

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

import io.aklivity.zilla.config.binding.risingwave.RisingwaveCommandType;
import io.aklivity.zilla.config.binding.risingwave.RisingwaveConditionConfig;

public class RisingwaveConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new RisingwaveConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"commands\": [ \"CREATE TOPIC\", \"DROP TOPIC\" ]" +
                "}";

        RisingwaveConditionConfig condition = jsonb.fromJson(text, RisingwaveConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.commands, equalTo(List.of(
            RisingwaveCommandType.CREATE_TOPIC_COMMAND,
            RisingwaveCommandType.DROP_TOPIC_COMMAND)));
    }

    @Test
    public void shouldWriteCondition()
    {
        RisingwaveConditionConfig condition = RisingwaveConditionConfig.builder()
            .command(RisingwaveCommandType.CREATE_TOPIC_COMMAND)
            .command(RisingwaveCommandType.DROP_TOPIC_COMMAND)
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"commands\":[\"CREATE TOPIC\",\"DROP TOPIC\"]}"));
    }
}
