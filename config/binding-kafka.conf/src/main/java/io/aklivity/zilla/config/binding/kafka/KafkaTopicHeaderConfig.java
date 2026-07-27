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

public class KafkaTopicHeaderConfig
{
    public final String name;
    public final String path;

    public static KafkaTopicHeaderConfigBuilder<KafkaTopicHeaderConfig> builder()
    {
        return new KafkaTopicHeaderConfigBuilder<>(KafkaTopicHeaderConfig.class::cast);
    }

    public static <T> KafkaTopicHeaderConfigBuilder<T> builder(
        Function<KafkaTopicHeaderConfig, T> mapper)
    {
        return new KafkaTopicHeaderConfigBuilder<>(mapper);
    }

    KafkaTopicHeaderConfig(
        String name,
        String path)
    {
        this.name = name;
        this.path = path;
    }
}
