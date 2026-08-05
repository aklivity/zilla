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
package io.aklivity.zilla.config.binding.kafka.grpc;

import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.binding.kafka.grpc.internal.KafkaGrpcBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public class KafkaGrpcBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        KafkaGrpcBindingConfig binding = KafkaGrpcBindingConfig.builder()
            .namespace("test")
            .name("kafka_grpc0")
            .kind(PROXY)
            .options()
                .acks("in_sync_replicas")
                .build()
            .route()
                .when()
                    .topic("test")
                    .build()
                .with()
                    .scheme("http")
                    .authority("test")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("kafka_grpc0"));
        assertThat(binding.type, equalTo(KafkaGrpcBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(PROXY));

        KafkaGrpcOptionsConfig options = (KafkaGrpcOptionsConfig) binding.options;
        assertThat(options.acks, equalTo("in_sync_replicas"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(KafkaGrpcConditionConfig.class));
        assertThat(((KafkaGrpcConditionConfig) condition).topic, equalTo("test"));

        WithConfig with = route.with;
        assertThat(with, instanceOf(KafkaGrpcWithConfig.class));
        assertThat(((KafkaGrpcWithConfig) with).scheme, equalTo("http"));
        assertThat(((KafkaGrpcWithConfig) with).authority, equalTo("test"));
    }
}
