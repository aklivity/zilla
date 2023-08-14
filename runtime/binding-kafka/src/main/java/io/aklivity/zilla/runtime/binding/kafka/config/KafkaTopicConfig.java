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

import java.util.Objects;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;

public class KafkaTopicConfig
{
    public final String name;
    public final KafkaOffsetType defaultOffset;
    public final KafkaDeltaType deltaType;

    public KafkaTopicConfig(
        String name,
        KafkaOffsetType defaultOffset,
        KafkaDeltaType deltaType)
    {
        this.name = name;
        this.defaultOffset = defaultOffset;
        this.deltaType = deltaType;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, deltaType);
    }

    @Override
    public boolean equals(
        Object other)
    {
        if (other == this)
        {
            return true;
        }

        if (!(other instanceof KafkaTopicConfig))
        {
            return false;
        }

        KafkaTopicConfig that = (KafkaTopicConfig) other;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.defaultOffset, that.defaultOffset) &&
                Objects.equals(this.deltaType, that.deltaType);
    }

    @Override
    public String toString()
    {
        return String.format("%s [name=%s, deltaType=%s]", name, deltaType);
    }
}
