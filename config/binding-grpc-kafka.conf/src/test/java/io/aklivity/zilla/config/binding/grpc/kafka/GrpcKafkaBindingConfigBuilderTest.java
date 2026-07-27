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
package io.aklivity.zilla.config.binding.grpc.kafka;

import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.binding.grpc.kafka.internal.GrpcKafkaBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public class GrpcKafkaBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        GrpcKafkaBindingConfig binding = GrpcKafkaBindingConfig.builder()
            .namespace("test")
            .name("grpc_kafka0")
            .kind(PROXY)
            .options()
                .reliability()
                    .field(32767)
                    .metadata("last-message-id")
                    .build()
                .build()
            .route()
                .when()
                    .service("example.EchoService")
                    .method("Echo")
                    .build()
                .with()
                    .fetch()
                        .topic("events")
                        .build()
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("grpc_kafka0"));
        assertThat(binding.type, equalTo(GrpcKafkaBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(PROXY));

        GrpcKafkaOptionsConfig options = (GrpcKafkaOptionsConfig) binding.options;
        assertThat(options.reliability.field, equalTo(32767));
        assertThat(options.reliability.metadata, equalTo("last-message-id"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(GrpcKafkaConditionConfig.class));
        assertThat(((GrpcKafkaConditionConfig) condition).service, equalTo("example.EchoService"));
        assertThat(((GrpcKafkaConditionConfig) condition).method, equalTo("Echo"));

        WithConfig with = route.with;
        assertThat(with, instanceOf(GrpcKafkaWithConfig.class));
        GrpcKafkaWithConfig withConfig = (GrpcKafkaWithConfig) with;
        assertThat(withConfig.capability, equalTo(GrpcKafkaCapability.FETCH));
        assertThat(withConfig.fetch.get().topic, equalTo("events"));
    }
}
