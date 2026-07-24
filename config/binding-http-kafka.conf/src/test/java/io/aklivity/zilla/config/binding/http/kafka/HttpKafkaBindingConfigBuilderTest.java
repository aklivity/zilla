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
package io.aklivity.zilla.config.binding.http.kafka;

import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.binding.http.kafka.internal.HttpKafkaBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;

public class HttpKafkaBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        HttpKafkaBindingConfig binding = HttpKafkaBindingConfig.builder()
            .namespace("test")
            .name("http-kafka0")
            .kind(PROXY)
            .options()
                .idempotency()
                    .header("idempotency-key")
                    .build()
                .build()
            .route()
                .when()
                    .method("GET")
                    .path("/items")
                    .build()
                .with()
                    .fetch()
                        .topic("items")
                        .build()
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("http-kafka0"));
        assertThat(binding.type, equalTo(HttpKafkaBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(PROXY));

        HttpKafkaOptionsConfig options = (HttpKafkaOptionsConfig) binding.options;
        assertThat(options.idempotency.header, equalTo("idempotency-key"));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(HttpKafkaConditionConfig.class));
        assertThat(((HttpKafkaConditionConfig) condition).method, equalTo("GET"));
        assertThat(((HttpKafkaConditionConfig) condition).path, equalTo("/items"));

        assertThat(route.with, instanceOf(HttpKafkaWithConfig.class));
        HttpKafkaWithConfig with = (HttpKafkaWithConfig) route.with;
        assertThat(with.capability, equalTo(HttpKafkaCapability.FETCH));
        assertThat(with.fetch.isPresent(), equalTo(true));
        assertThat(with.fetch.get().topic, equalTo("items"));
    }
}
