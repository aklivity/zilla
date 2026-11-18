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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

final class MqttKafkaMembership
{
    final String shareName;
    final String filter;

    MqttKafkaMembership(
        String shareName,
        String filter)
    {
        this.shareName = shareName;
        this.filter = filter;
    }

    boolean subscribed(
        String shareName,
        String filter)
    {
        return this.shareName.equals(shareName) && this.filter.equals(filter);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        return obj instanceof MqttKafkaMembership that && subscribed(that.shareName, that.filter);
    }

    @Override
    public int hashCode()
    {
        return 31 * shareName.hashCode() + filter.hashCode();
    }
}
