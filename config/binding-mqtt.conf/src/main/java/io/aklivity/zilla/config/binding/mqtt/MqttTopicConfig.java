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

import java.util.List;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;

public class MqttTopicConfig extends Config
{
    public final String name;
    public final ModelConfig content;
    public final List<MqttUserPropertyConfig> userProperties;
    private final List<NamedConfig> refs;

    public MqttTopicConfig(
        String name,
        ModelConfig content,
        List<MqttUserPropertyConfig> userProperties,
        List<NamedConfig> refs)
    {
        this.name = name;
        this.content = content;
        this.userProperties = userProperties;
        this.refs = refs;
    }

    public List<NamedConfig> refs()
    {
        return refs;
    }

    public static MqttTopicConfigBuilder<MqttTopicConfig> builder()
    {
        return new MqttTopicConfigBuilder<>(identity());
    }
}
