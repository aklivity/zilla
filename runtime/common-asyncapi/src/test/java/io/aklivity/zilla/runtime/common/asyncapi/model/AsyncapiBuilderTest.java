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
package io.aklivity.zilla.runtime.common.asyncapi.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import org.junit.Test;

public class AsyncapiBuilderTest
{
    @Test
    public void shouldBuildFullDocument()
    {
        Asyncapi asyncapi = Asyncapi.builder()
            .asyncapi("3.0.0")
            .info()
                .title("Test API")
                .version("0.1.0")
                .description("A test API")
                .build()
            .server("prod")
                .host("kafka.example.com:9092")
                .protocol("kafka")
                .bindings(Map.of("kafka", Map.of()))
                .build()
            .channel("customer.events")
                .address("customer.events")
                .message("CustomerEvent")
                    .contentType("application/json")
                    .payload()
                        .ref("#/components/schemas/CustomerEvent")
                        .build()
                    .build()
                .build()
            .operation("receiveCustomerEvents")
                .action("receive")
                .channel()
                    .ref("#/channels/customer.events")
                    .build()
                .build()
            .components()
                .message("CustomerEvent")
                    .contentType("application/json")
                    .build()
                .schema("CustomerEvent")
                    .schemaFormat("application/schema+json;version=draft-07")
                    .schema(Map.of("type", "object"))
                    .build()
                .build()
            .build();

        assertThat(asyncapi.asyncapi, equalTo("3.0.0"));
        assertThat(asyncapi.info.title, equalTo("Test API"));
        assertThat(asyncapi.info.version, equalTo("0.1.0"));
        assertThat(asyncapi.info.description, equalTo("A test API"));

        AsyncapiServer server = asyncapi.servers.get("prod");
        assertThat(server.host, equalTo("kafka.example.com:9092"));
        assertThat(server.protocol, equalTo("kafka"));

        AsyncapiChannel channel = asyncapi.channels.get("customer.events");
        assertThat(channel.address, equalTo("customer.events"));
        AsyncapiMessage channelMessage = channel.messages.get("CustomerEvent");
        assertThat(channelMessage.contentType, equalTo("application/json"));
        assertThat(((AsyncapiMultiFormatSchema) channelMessage.payload).ref,
            equalTo("#/components/schemas/CustomerEvent"));

        AsyncapiOperation operation = asyncapi.operations.get("receiveCustomerEvents");
        assertThat(operation.action, equalTo("receive"));
        assertThat(operation.channel.ref, equalTo("#/channels/customer.events"));

        AsyncapiMessage componentMessage = asyncapi.components.messages.get("CustomerEvent");
        assertThat(componentMessage.contentType, equalTo("application/json"));
        AsyncapiMultiFormatSchema schema = (AsyncapiMultiFormatSchema) asyncapi.components.schemas.get("CustomerEvent");
        assertThat(schema.schemaFormat, equalTo("application/schema+json;version=draft-07"));
        assertThat(schema.schema, equalTo(Map.of("type", "object")));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        Asyncapi asyncapi = Asyncapi.builder(Asyncapi.class::cast)
            .asyncapi("3.0.0")
            .build();

        assertThat(asyncapi.asyncapi, equalTo("3.0.0"));
        assertThat(asyncapi.info, nullValue());
    }
}
