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
package io.aklivity.zilla.runtime.cog.tcp.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class TcpConditionAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new TcpConditionAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"cidr\": \"127.0.0.0/24\"," +
                    "\"authority\": \"*.example.net\"" +
                "}";

        TcpCondition condition = jsonb.fromJson(text, TcpCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.cidr, equalTo("127.0.0.0/24"));
        assertThat(condition.authority, equalTo("*.example.net"));
    }

    @Test
    public void shouldWriteCondition()
    {
        TcpCondition condition = new TcpCondition("127.0.0.0/24", "*.example.net");

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"cidr\":\"127.0.0.0/24\",\"authority\":\"*.example.net\"}"));
    }
}
