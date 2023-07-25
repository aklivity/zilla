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
package io.aklivity.zilla.specs.binding.kafka.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/binding/kafka/schema/kafka.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/schema/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config");

    @Test
    public void shouldValidateCache()
    {
        JsonObject config = schema.validate("cache.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateCacheOptionsBootstrap()
    {
        JsonObject config = schema.validate("cache.options.bootstrap.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateCacheOptionsDeltaType()
    {
        JsonObject config = schema.validate("cache.options.delta.type.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateCacheOptionsMerged()
    {
        JsonObject config = schema.validate("cache.options.merged.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateCacheWhenTopic()
    {
        JsonObject config = schema.validate("cache.when.topic.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientOptionsMerged()
    {
        JsonObject config = schema.validate("client.options.merged.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientOptionsSaslPlain()
    {
        JsonObject config = schema.validate("client.options.sasl.plain.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientOptionsSaslScram()
    {
        JsonObject config = schema.validate("client.options.sasl.scram.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientWhenTopic()
    {
        JsonObject config = schema.validate("client.when.topic.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientOptionsSchema()
    {
        JsonObject config = schema.validate("client.options.schema.registry.yaml");

        assertThat(config, not(nullValue()));
    }
}
