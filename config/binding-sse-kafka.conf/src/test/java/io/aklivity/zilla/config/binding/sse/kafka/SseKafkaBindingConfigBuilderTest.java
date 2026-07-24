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
package io.aklivity.zilla.config.binding.sse.kafka;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.binding.sse.kafka.internal.SseKafkaBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public class SseKafkaBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        SseKafkaBindingConfig binding = SseKafkaBindingConfig.builder()
            .namespace("test")
            .name("sse_kafka0")
            .kind(SERVER)
            .route()
                .when()
                    .path("/events")
                    .build()
                .with()
                    .topic("events")
                    .eventId(SseKafkaWithConfig.EVENT_ID_DEFAULT)
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("sse_kafka0"));
        assertThat(binding.type, equalTo(SseKafkaBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(SseKafkaConditionConfig.class));
        assertThat(((SseKafkaConditionConfig) condition).path, equalTo("/events"));

        WithConfig with = route.with;
        assertThat(with, instanceOf(SseKafkaWithConfig.class));
        assertThat(((SseKafkaWithConfig) with).topic, equalTo("events"));
    }
}
