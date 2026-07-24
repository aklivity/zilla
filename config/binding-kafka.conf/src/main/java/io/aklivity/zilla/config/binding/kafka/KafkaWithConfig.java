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

import io.aklivity.zilla.config.engine.WithConfig;

public final class KafkaWithConfig extends WithConfig
{
    public final String defaultOffset;
    public final String deltaType;
    public final String ackMode;

    public static KafkaWithConfigBuilder<KafkaWithConfig> builder()
    {
        return new KafkaWithConfigBuilder<>(KafkaWithConfig.class::cast);
    }

    KafkaWithConfig(
        String defaultOffset,
        String deltaType,
        String ackMode)
    {
        this.defaultOffset = defaultOffset;
        this.deltaType = deltaType;
        this.ackMode = ackMode;
    }
}
