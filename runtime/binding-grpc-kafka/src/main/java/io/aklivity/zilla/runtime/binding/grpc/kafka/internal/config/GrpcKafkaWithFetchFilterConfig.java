/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config;

import java.util.List;
import java.util.Optional;

public final class GrpcKafkaWithFetchFilterConfig
{
    public final Optional<String> key;
    public final Optional<List<GrpcKafkaWithFetchFilterHeaderConfig>> headers;

    public GrpcKafkaWithFetchFilterConfig(
        String key,
        List<GrpcKafkaWithFetchFilterHeaderConfig> headers)
    {
        this.key = Optional.ofNullable(key);
        this.headers = Optional.ofNullable(headers);
    }
}
