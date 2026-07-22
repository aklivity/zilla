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

public final class KafkaSaslCredentialsConfig
{
    public final String mechanism;
    public final String username;
    public final String password;
    public final String token;

    public static KafkaSaslCredentialsConfigBuilder<KafkaSaslCredentialsConfig> builder()
    {
        return new KafkaSaslCredentialsConfigBuilder<>(KafkaSaslCredentialsConfig.class::cast);
    }

    public static <T> KafkaSaslCredentialsConfigBuilder<T> builder(
        Function<KafkaSaslCredentialsConfig, T> mapper)
    {
        return new KafkaSaslCredentialsConfigBuilder<>(mapper);
    }

    KafkaSaslCredentialsConfig(
        String mechanism,
        String username,
        String password,
        String token)
    {
        this.mechanism = mechanism;
        this.username = username;
        this.password = password;
        this.token = token;
    }
}
