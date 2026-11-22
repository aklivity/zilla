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

import org.agrona.BitUtil;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

final class MqttKafkaOffsetHex
{
    private static final int INITIAL_CAPACITY = 256;

    private byte[] bytes = new byte[INITIAL_CAPACITY];

    String16FW toHex(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (length > bytes.length)
        {
            bytes = new byte[Math.max(length, bytes.length << 1)];
        }

        buffer.getBytes(offset, bytes, 0, length);
        return new String16FW(BitUtil.toHex(bytes, 0, length));
    }
}
