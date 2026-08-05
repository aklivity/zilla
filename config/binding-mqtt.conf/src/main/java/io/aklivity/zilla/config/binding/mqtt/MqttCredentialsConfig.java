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

import java.util.List;
import java.util.function.Function;

public final class MqttCredentialsConfig
{
    public final List<MqttPatternConfig> connect;

    public static MqttCredentialsConfigBuilder<MqttCredentialsConfig> builder()
    {
        return new MqttCredentialsConfigBuilder<>(MqttCredentialsConfig.class::cast);
    }

    public static <T> MqttCredentialsConfigBuilder<T> builder(
        Function<MqttCredentialsConfig, T> mapper)
    {
        return new MqttCredentialsConfigBuilder<>(mapper);
    }

    MqttCredentialsConfig(
        List<MqttPatternConfig> connect)
    {
        this.connect = connect;
    }
}
