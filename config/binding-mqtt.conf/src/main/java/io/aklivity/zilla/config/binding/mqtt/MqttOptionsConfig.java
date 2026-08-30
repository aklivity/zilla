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

import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class MqttOptionsConfig extends OptionsConfig
{
    public final MqttAuthorizationConfig authorization;
    public final List<MqttTopicConfig> topics;
    public final List<MqttVersion> versions;
    public final String store;
    public final String server;

    public static MqttOptionsConfigBuilder<MqttOptionsConfig> builder()
    {
        return new MqttOptionsConfigBuilder<>(MqttOptionsConfig.class::cast);
    }

    public static <T> MqttOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new MqttOptionsConfigBuilder<>(mapper);
    }

    MqttOptionsConfig(
        MqttAuthorizationConfig authorization,
        List<MqttTopicConfig> topics,
        List<MqttVersion> versions,
        String store,
        String server,
        List<NamedConfig> refs)
    {
        super(null, refs);
        this.authorization = authorization;
        this.topics = topics;
        this.versions = versions;
        this.store = store;
        this.server = server;
    }
}
