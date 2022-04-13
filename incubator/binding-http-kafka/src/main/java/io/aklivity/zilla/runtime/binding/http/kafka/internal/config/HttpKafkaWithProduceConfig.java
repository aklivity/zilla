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

public final class HttpKafkaWithProduceConfig
{
    public final String topic;
    public final Optional<String> key;
    public final Optional<List<HttpKafkaWithProduceOverrideConfig>> overrides;
    public final Optional<String> replyTo;
    public final Optional<List<HttpKafkaWithProduceAsyncHeaderConfig>> async;

    public HttpKafkaWithProduceConfig(
        String topic,
        String key,
        List<HttpKafkaWithProduceOverrideConfig> overrides,
        String replyTo,
        List<HttpKafkaWithProduceAsyncHeaderConfig> async)
    {
        this.topic = topic;
        this.key = Optional.ofNullable(key);
        this.overrides = Optional.ofNullable(overrides);
        this.replyTo = Optional.ofNullable(replyTo);
        this.async = Optional.ofNullable(async);
    }
}
