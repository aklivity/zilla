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
package io.aklivity.zilla.specs.metrics.stream.config;

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
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/binding/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/exporter/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/metrics/stream/schema/stream.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/metrics/stream/config");

    @Test
    public void shouldValidateClientWriteClose()
    {
        JsonObject config = schema.validate("client.write.close.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientSentWriteAbort()
    {
        JsonObject config = schema.validate("client.sent.write.abort.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSentReadAbort()
    {
        JsonObject config = schema.validate("server.sent.read.abort.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerWriteClose()
    {
        JsonObject config = schema.validate("server.write.close.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSentWriteAbort()
    {
        JsonObject config = schema.validate("server.sent.write.abort.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientSentReadAbort()
    {
        JsonObject config = schema.validate("client.sent.read.abort.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientSentData()
    {
        JsonObject config = schema.validate("client.sent.data.yaml");

        assertThat(config, not(nullValue()));
    }
}
