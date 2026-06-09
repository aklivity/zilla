/*
 * Copyright 2021-2024 Aklivity Inc.
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

import static java.util.function.Function.identity;

import java.util.function.Function;

public final class KafkaAuthorizationConfig
{
    public final String name;
    public final KafkaSaslCredentialsConfig credentials;

    public transient String qname;

    public static KafkaAuthorizationConfigBuilder<KafkaAuthorizationConfig> builder()
    {
        return new KafkaAuthorizationConfigBuilder<>(identity());
    }

    public static <T> KafkaAuthorizationConfigBuilder<T> builder(
        Function<KafkaAuthorizationConfig, T> mapper)
    {
        return new KafkaAuthorizationConfigBuilder<>(mapper);
    }

    KafkaAuthorizationConfig(
        String name,
        KafkaSaslCredentialsConfig credentials)
    {
        this.name = name;
        this.credentials = credentials;
    }
}
