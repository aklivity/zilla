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
package io.aklivity.zilla.config.binding.mqtt.kafka;

import static io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionKind.PUBLISH;
import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mqtt.kafka.internal.MqttKafkaBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public class MqttKafkaBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        MqttKafkaBindingConfig binding = MqttKafkaBindingConfig.builder()
            .namespace("test")
            .name("mqtt-kafka0")
            .kind(PROXY)
            .options()
                .clients(List.of("client1"))
                .build()
            .route()
                .when()
                    .kind(PUBLISH)
                    .topic("test")
                    .build()
                .with()
                    .messages("test")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("mqtt-kafka0"));
        assertThat(binding.type, equalTo(MqttKafkaBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(PROXY));

        MqttKafkaOptionsConfig options = (MqttKafkaOptionsConfig) binding.options;
        assertThat(options.clients, equalTo(List.of("client1")));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(MqttKafkaConditionConfig.class));
        assertThat(((MqttKafkaConditionConfig) condition).kind, equalTo(PUBLISH));
        assertThat(((MqttKafkaConditionConfig) condition).topics, equalTo(List.of("test")));

        assertThat(route.with, instanceOf(MqttKafkaWithConfig.class));
        WithConfig with = route.with;
        assertThat(((MqttKafkaWithConfig) with).messages, equalTo("test"));
    }
}
