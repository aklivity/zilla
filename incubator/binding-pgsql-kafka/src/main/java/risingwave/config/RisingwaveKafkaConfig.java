/*
 * Copyright 2021-2023 Aklivity Inc
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
package risingwave.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public class RisingwaveKafkaConfig
{
    public final RisingwaveKafkaPropertiesConfig properties;
    public final ModelConfig format;

    public static RisingwaveKafkaConfigBuilder<RisingwaveKafkaConfig> builder()
    {
        return new RisingwaveKafkaConfigBuilder<>(RisingwaveKafkaConfig.class::cast);
    }

    public static <T> RisingwaveKafkaConfigBuilder<T> builder(
        Function<RisingwaveKafkaConfig, T> mapper)
    {
        return new RisingwaveKafkaConfigBuilder<>(mapper);
    }

    RisingwaveKafkaConfig(
        RisingwaveKafkaPropertiesConfig properties,
        ModelConfig format)
    {
        this.properties = properties;
        this.format = format;
    }
}
