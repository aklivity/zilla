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

import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class SseKafkaBindingConfig
{
    public final long id;
    public final String entry;
    public final KindConfig kind;
    public final List<SseKafkaRouteConfig> routes;

    public SseKafkaBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.entry = binding.name;
        this.kind = binding.kind;
        this.routes = binding.routes.stream().map(SseKafkaRouteConfig::new).collect(toList());
    }

    public SseKafkaRouteConfig resolve(
        long authorization,
        SseBeginExFW beginEx)
    {
        String path = beginEx != null ? beginEx.path().asString() : null;
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(path))
            .findFirst()
            .orElse(null);
    }
}
