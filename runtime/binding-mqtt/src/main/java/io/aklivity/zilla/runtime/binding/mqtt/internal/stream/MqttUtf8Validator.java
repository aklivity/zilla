/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import org.agrona.BitUtil;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.MemorySegments;

final class MqttUtf8Validator
{
    boolean isWellFormed(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        return MemorySegments.utf8Bytes(buffer.segment(), buffer.wrapAdjustment() + offset, length) == length;
    }

    boolean isValidString(
        String16FW string)
    {
        final int length = string != null ? string.length() : 0;

        boolean valid = true;

        if (length > 0)
        {
            final DirectBufferEx buffer = string.buffer();
            final int offset = string.offset() + BitUtil.SIZE_OF_SHORT;

            valid = isWellFormed(buffer, offset, length);

            for (int index = 0; valid && index < length; index++)
            {
                valid = buffer.getByte(offset + index) != 0;
            }
        }

        return valid;
    }
}
