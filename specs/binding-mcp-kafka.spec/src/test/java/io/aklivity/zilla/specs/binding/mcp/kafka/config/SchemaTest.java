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
package io.aklivity.zilla.specs.binding.mcp.kafka.config;

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
        .schemaPatch("io/aklivity/zilla/config/binding/kafka/schema/kafka.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/config/binding/mcp/kafka/internal/schema/mcp.kafka.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/model/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/guard/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/kafka/config");

    @Test
    public void shouldValidateProxyProduce()
    {
        JsonObject config = schema.validate("proxy.produce.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyConsume()
    {
        JsonObject config = schema.validate("proxy.consume.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientMissingServers()
    {
        schema.validate("client.missing.servers.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyWithOptions()
    {
        schema.validate("proxy.with.options.yaml");
    }

    @Test
    public void shouldValidateProxyProduceTopicAllowlist()
    {
        JsonObject config = schema.validate("proxy.produce.topic.allowlist.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyProduceTopicGlob()
    {
        JsonObject config = schema.validate("proxy.produce.topic.glob.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientTopics()
    {
        JsonObject config = schema.validate("client.topics.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientVault()
    {
        JsonObject config = schema.validate("client.vault.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyWithVault()
    {
        schema.validate("proxy.with.vault.yaml");
    }

    @Test
    public void shouldValidateProxyProduceGuarded()
    {
        JsonObject config = schema.validate("proxy.produce.guarded.yaml");

        assertThat(config, not(nullValue()));
    }
}
