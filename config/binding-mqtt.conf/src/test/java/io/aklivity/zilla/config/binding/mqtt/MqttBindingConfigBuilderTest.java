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
package io.aklivity.zilla.config.binding.mqtt;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;

public class MqttBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        MqttBindingConfig binding = MqttBindingConfig.builder()
            .namespace("test")
            .name("mqtt0")
            .kind(SERVER)
            .options()
                .server("test")
                .build()
            .route()
                .when()
                    .subscribe()
                        .topic("test")
                        .build()
                    .build()
                .with()
                    .compositeId(42L)
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("mqtt0"));
        assertThat(binding.type, equalTo(MqttBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        MqttOptionsConfig options = (MqttOptionsConfig) binding.options;
        assertThat(options.server, equalTo("test"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(MqttConditionConfig.class));
        assertThat(((MqttConditionConfig) condition).subscribes, hasSize(1));
        assertThat(((MqttConditionConfig) condition).subscribes.get(0).topic, equalTo("test"));

        WithConfig with = route.with;
        assertThat(with, instanceOf(MqttWithConfig.class));
        assertThat(((MqttWithConfig) with).compositeId, equalTo(42L));
    }
}
