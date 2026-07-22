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
package io.aklivity.zilla.config.binding.mqtt;

import static java.util.function.Function.identity;

import java.util.function.Function;

public class MqttSessionConfig
{
    public final String clientId;

    public static MqttSessionConfigBuilder<MqttSessionConfig> builder()
    {
        return new MqttSessionConfigBuilder<>(identity());
    }

    public static <T> MqttSessionConfigBuilder<T> builder(
        Function<MqttSessionConfig, T> mapper)
    {
        return new MqttSessionConfigBuilder<>(mapper);
    }

    MqttSessionConfig(
        String clientId)
    {
        this.clientId = clientId;
    }
}
