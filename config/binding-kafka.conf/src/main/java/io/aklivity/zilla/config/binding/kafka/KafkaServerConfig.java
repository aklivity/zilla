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

public class KafkaServerConfig
{
    public final String host;
    public final int port;

    public static KafkaServerConfigBuilder<KafkaServerConfig> builder()
    {
        return new KafkaServerConfigBuilder<>(KafkaServerConfig.class::cast);
    }

    public static <T> KafkaServerConfigBuilder<T> builder(
        Function<KafkaServerConfig, T> mapper)
    {
        return new KafkaServerConfigBuilder<>(mapper);
    }

    KafkaServerConfig(
        String host,
        int port)
    {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString()
    {
        return String.format("%s:%d", host, port);
    }
}
