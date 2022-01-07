/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.specs.cog.mqtt.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonObject;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.cog.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/cog/mqtt/schema/mqtt.json")
        .configurationRoot("io/aklivity/zilla/specs/cog/mqtt/config");

    @Ignore("TODO")
    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.json");

        assertThat(config, not(nullValue()));
    }

    @Ignore("TODO")
    @Test
    public void shouldValidateClientWhenTopic()
    {
        JsonObject config = schema.validate("client.when.topic.json");

        assertThat(config, not(nullValue()));
    }

    @Ignore("TODO")
    @Test
    public void shouldValidateClientWhenTopicOrSessions()
    {
        JsonObject config = schema.validate("client.when.topic.or.sessions.json");

        assertThat(config, not(nullValue()));
    }

    @Ignore("TODO")
    @Test
    public void shouldValidateClientWhenTopicPublishOnly()
    {
        JsonObject config = schema.validate("client.when.topic.publish.only.json");

        assertThat(config, not(nullValue()));
    }

    @Ignore("TODO")
    @Test
    public void shouldValidateClientWhenTopicSubscribeOnly()
    {
        JsonObject config = schema.validate("client.when.topic.subscribe.only.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServer()
    {
        JsonObject config = schema.validate("server.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerWhenTopic()
    {
        JsonObject config = schema.validate("server.when.topic.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerWhenTopicOrSessions()
    {
        JsonObject config = schema.validate("server.when.topic.or.sessions.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerWhenTopicPublishOnly()
    {
        JsonObject config = schema.validate("server.when.topic.publish.only.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerWhenTopicSubscribeOnly()
    {
        JsonObject config = schema.validate("server.when.topic.subscribe.only.json");

        assertThat(config, not(nullValue()));
    }
}
