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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.RouteConfigBuilder;

public final class KafkaRouteConfigBuilder<T> extends RouteConfigBuilder<T, KafkaRouteConfigBuilder<T>>
{
    KafkaRouteConfigBuilder(
        Function<RouteConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaRouteConfigBuilder<T>> thisType()
    {
        return (Class<KafkaRouteConfigBuilder<T>>) getClass();
    }

    public KafkaConditionConfigBuilder<KafkaRouteConfigBuilder<T>> when()
    {
        return new KafkaConditionConfigBuilder<>(this::when);
    }

    public KafkaWithConfigBuilder<KafkaRouteConfigBuilder<T>> with()
    {
        return new KafkaWithConfigBuilder<>(this::with);
    }
}
