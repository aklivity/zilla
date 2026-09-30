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
package io.aklivity.zilla.specs.metrics.grpc.config;

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
        .schemaPatch("io/aklivity/zilla/specs/metrics/grpc/schema/grpc.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/metrics/grpc/config");

    @Test
    public void shouldValidateUnaryRpcMessageExchange()
    {
        JsonObject config = schema.validate("unary.rpc.message.exchange.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientStreamRpcMessageExchange()
    {
        JsonObject config = schema.validate("client.stream.rpc.message.exchange.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateUnaryRpcEmptyMessageExchange()
    {
        JsonObject config = schema.validate("unary.rpc.empty.message.exchange.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateUnaryRpcServerSendWriteAbortOnOpenResponse()
    {
        JsonObject config = schema.validate("unary.rpc.server.send.write.abort.on.open.response.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateUnaryRpcServerSendReadAbortOnOpenRequest()
    {
        JsonObject config = schema.validate("unary.rpc.server.send.read.abort.on.open.request.yaml");

        assertThat(config, not(nullValue()));
    }
}
