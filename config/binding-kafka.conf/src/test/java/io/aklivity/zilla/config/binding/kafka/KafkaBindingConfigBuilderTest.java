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
package io.aklivity.zilla.config.binding.kafka;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;

public class KafkaBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        KafkaBindingConfig binding = KafkaBindingConfig.builder()
            .namespace("test")
            .name("kafka0")
            .kind(SERVER)
            .options()
                .bootstrap("test")
                .build()
            .route()
                .when()
                    .topic("test")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("kafka0"));
        assertThat(binding.type, equalTo(KafkaBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        KafkaOptionsConfig options = (KafkaOptionsConfig) binding.options;
        assertThat(options.bootstrap, equalTo(List.of("test")));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(KafkaConditionConfig.class));
        assertThat(((KafkaConditionConfig) condition).topic, equalTo("test"));
    }
}
