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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class KafkaOptionsConfig extends OptionsConfig
{
    public final List<String> bootstrap;
    public final List<KafkaTopicConfig> topics;
    public final List<KafkaServerConfig> servers;
    public final KafkaAuthorizationConfig authorization;

    public static KafkaOptionsConfigBuilder<KafkaOptionsConfig> builder()
    {
        return new KafkaOptionsConfigBuilder<>(KafkaOptionsConfig.class::cast);
    }

    public static <T> KafkaOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new KafkaOptionsConfigBuilder<>(mapper);
    }

    KafkaOptionsConfig(
        List<String> bootstrap,
        List<KafkaTopicConfig> topics,
        List<KafkaServerConfig> servers,
        KafkaAuthorizationConfig authorization,
        List<NamedConfig> refs)
    {
        super(List.of(), null, refs);
        this.bootstrap = bootstrap;
        this.topics = topics;
        this.servers = servers;
        this.authorization = authorization;
    }
}
