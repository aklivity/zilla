/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class SseKafkaRouteConfig extends OptionsConfig
{
    public final long id;
    public final int order;
    public final List<SseKafkaConditionMatcher> when;
    public final Optional<SseKafkaWithResolver> with;

    public SseKafkaRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.order = route.order;
        this.when = route.when.stream()
            .map(SseKafkaConditionConfig.class::cast)
            .map(SseKafkaConditionMatcher::new)
            .collect(toList());
        this.with = Optional.ofNullable(route.with)
            .map(SseKafkaWithConfig.class::cast)
            .map(SseKafkaWithResolver::new);
    }
}
