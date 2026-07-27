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
package io.aklivity.zilla.config.binding.mcp.kafka;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.kafka.internal.McpKafkaBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;

public class McpKafkaBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        McpKafkaBindingConfig binding = McpKafkaBindingConfig.builder()
            .namespace("test")
            .name("mcp_kafka0")
            .kind(SERVER)
            .options()
                .server()
                    .host("localhost")
                    .port(9092)
                    .build()
                .build()
            .route()
                .when()
                    .tool("test")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("mcp_kafka0"));
        assertThat(binding.type, equalTo(McpKafkaBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        McpKafkaOptionsConfig options = (McpKafkaOptionsConfig) binding.options;
        assertThat(options.servers, hasSize(1));
        assertThat(options.servers.get(0).host, equalTo("localhost"));
        assertThat(options.servers.get(0).port, equalTo(9092));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(McpKafkaConditionConfig.class));
        assertThat(((McpKafkaConditionConfig) condition).tool, equalTo("test"));
    }
}
