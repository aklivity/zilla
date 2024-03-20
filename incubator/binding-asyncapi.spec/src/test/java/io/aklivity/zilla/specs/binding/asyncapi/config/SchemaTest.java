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
package io.aklivity.zilla.specs.binding.asyncapi.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/asyncapi/schema/asyncapi.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/binding/tls/schema/tls.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/binding/tcp/schema/tcp.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/binding/kafka/schema/kafka.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/binding/http/schema/http.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/vault/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/asyncapi/config");

    @Test
    public void shouldValidateMqttClient()
    {
        JsonObject config = schema.validate("client.mqtt.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateMqttSecureClient()
    {
        JsonObject config = schema.validate("client.mqtt.secure.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateMqttServer()
    {
        JsonObject config = schema.validate("server.mqtt.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateMqttSecureServer()
    {
        JsonObject config = schema.validate("server.mqtt.secure.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttpClient()
    {
        JsonObject config = schema.validate("client.http.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttpSecureClient()
    {
        JsonObject config = schema.validate("client.http.secure.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttpServer()
    {
        JsonObject config = schema.validate("server.http.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttpSecureServer()
    {
        JsonObject config = schema.validate("server.http.secure.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateKafkaClient()
    {
        JsonObject config = schema.validate("client.kafka.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateKafkaClientSasl()
    {
        JsonObject config = schema.validate("client.kafka.sasl.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateAsyncapiProxy()
    {
        JsonObject config = schema.validate("proxy.mqtt.kafka.yaml");

        assertThat(config, not(nullValue()));
    }
}
