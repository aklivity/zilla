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
package io.aklivity.zilla.specs.binding.mqtt.kafka.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/binding/mqtt/kafka/schema/mqtt.kafka.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/kafka/config");


    @Test
    public void shouldValidateProxy()
    {
        JsonObject config = schema.validate("proxy.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyWithOptions()
    {
        JsonObject config = schema.validate("proxy.options.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyWithPublishQosMax()
    {
        JsonObject config = schema.validate("proxy.publish.qos.max.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyWhenPublishTopicWithMessages()
    {
        JsonObject config = schema.validate("proxy.when.publish.topic.with.messages.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyWhenSubscribeTopicWithMessages()
    {
        JsonObject config = schema.validate("proxy.when.subscribe.topic.with.messages.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyWhenClientTopicSpace()
    {
        JsonObject config = schema.validate("proxy.when.client.topic.space.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyWhenPublishTopicInvalid()
    {
        schema.validate("proxy.when.publish.topic.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyWhenSubscribeTopicInvalid()
    {
        schema.validate("proxy.when.subscribe.topic.invalid.yaml");
    }
}
