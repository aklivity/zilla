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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import java.util.List;
import java.util.Optional;

public final class HttpKafkaWithFetchConfig
{
    public final String topic;
    public final Optional<List<HttpKafkaWithFetchFilterConfig>> filters;
    public final Optional<HttpKafkaWithFetchMergeConfig> merge;

    public HttpKafkaWithFetchConfig(
        String topic,
        List<HttpKafkaWithFetchFilterConfig> filters,
        HttpKafkaWithFetchMergeConfig merged)
    {
        this.topic = topic;
        this.filters = Optional.ofNullable(filters);
        this.merge = Optional.ofNullable(merged);
    }
}
