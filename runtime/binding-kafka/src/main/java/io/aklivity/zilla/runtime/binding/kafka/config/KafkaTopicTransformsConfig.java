/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.config;

import java.util.List;
import java.util.function.Function;

public class KafkaTopicTransformsConfig
{
    public final String extractKey;

    public final List<KafkaTopicHeaderType> extractHeaders;

    public static KafkaTopicTransformsConfigBuilder<KafkaTopicTransformsConfig> builder()
    {
        return new KafkaTopicTransformsConfigBuilder<>(KafkaTopicTransformsConfig.class::cast);
    }

    public static <T> KafkaTopicTransformsConfigBuilder<T> builder(
        Function<KafkaTopicTransformsConfig, T> mapper)
    {
        return new KafkaTopicTransformsConfigBuilder<>(mapper);
    }

    KafkaTopicTransformsConfig(
        String extractKey,
        List<KafkaTopicHeaderType> extractHeaders)
    {
        this.extractKey = extractKey;
        this.extractHeaders = extractHeaders;
    }
}
