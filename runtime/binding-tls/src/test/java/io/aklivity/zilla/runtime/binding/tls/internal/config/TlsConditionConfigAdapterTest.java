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
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.tls.config.TlsConditionConfig;

public class TlsConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new TlsConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{" +
                    "\"authority\": \"example.net\"," +
                    "\"alpn\": \"echo\"" +
                "}";

        TlsConditionConfig condition = jsonb.fromJson(text, TlsConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.authority, equalTo("example.net"));
        assertThat(condition.alpn, equalTo("echo"));
    }

    @Test
    public void shouldWriteCondition()
    {
        TlsConditionConfig condition = TlsConditionConfig.builder()
            .inject(identity())
            .authority("example.net")
            .alpn("echo")
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"authority\":\"example.net\",\"alpn\":\"echo\"}"));
    }

    @Test
    public void shouldReadConditionWithPortRange()
    {
        String text =
                "{" +
                    "\"authority\": \"example.net\"," +
                    "\"alpn\": \"echo\"," +
                    "\"port\": 8080-8081" +
                "}";

        TlsConditionConfig condition = jsonb.fromJson(text, TlsConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.ports, not(nullValue()));
        assertThat(condition.ports.length, equalTo(2));
        assertThat(condition.ports[0], equalTo(8080));
        assertThat(condition.ports[1], equalTo(8081));
    }

    @Test
    public void shouldWriteConditionWithPorts()
    {
        TlsConditionConfig condition = TlsConditionConfig.builder()
            .inject(identity())
            .authority("example.net")
            .alpn("echo")
            .ports(new int[] { 8080, 8081 })
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"authority\":\"example.net\",\"alpn\":\"echo\",\"port\":[8080,8081]}"));
    }

    @Test
    public void shouldReadConditionWithPortRangeSingleton()
    {
        String text =
                "{" +
                    "\"authority\": \"example.net\"," +
                    "\"alpn\": \"echo\"," +
                    "\"port\": \"8080\"" +
                "}";

        TlsConditionConfig condition = jsonb.fromJson(text, TlsConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.ports, not(nullValue()));
        assertThat(condition.ports.length, equalTo(1));
        assertThat(condition.ports[0], equalTo(8080));
    }

    @Test
    public void shouldWriteConditionWithPortRangeSingleton()
    {
        TlsConditionConfig condition = TlsConditionConfig.builder()
            .inject(identity())
            .authority("example.net")
            .alpn("echo")
            .ports(new int[] {8080})
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"authority\":\"example.net\",\"alpn\":\"echo\",\"port\":8080}"));
    }
}
