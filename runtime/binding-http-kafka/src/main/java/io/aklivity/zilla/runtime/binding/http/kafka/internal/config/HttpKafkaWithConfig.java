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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import java.util.Optional;

import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class HttpKafkaWithConfig extends WithConfig
{
    public final HttpKafkaCapability capability;
    public final Optional<HttpKafkaWithFetchConfig> fetch;
    public final Optional<HttpKafkaWithProduceConfig> produce;

    public HttpKafkaWithConfig(
        HttpKafkaWithFetchConfig fetch)
    {
        this(HttpKafkaCapability.FETCH, fetch, null);
    }

    public HttpKafkaWithConfig(
        HttpKafkaWithProduceConfig produce)
    {
        this(HttpKafkaCapability.PRODUCE, null, produce);
    }

    private HttpKafkaWithConfig(
        HttpKafkaCapability capability,
        HttpKafkaWithFetchConfig fetch,
        HttpKafkaWithProduceConfig produce)
    {
        this.capability = capability;
        this.fetch = Optional.ofNullable(fetch);
        this.produce = Optional.ofNullable(produce);
    }
}
