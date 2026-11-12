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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedMembershipsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedMembershipsVersion;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class MqttKafkaMemberships
{
    private static final int HEADER_SIZE = 16;
    private static final int MEMBERSHIP_FIXED_SIZE = 4;
    private static final int MAXIMUM_UTF8_BYTES_PER_CHAR = 3;

    private final MqttSharedMembershipsFW.Builder membershipsRW = new MqttSharedMembershipsFW.Builder();
    private final MqttSharedMembershipsFW membershipsRO = new MqttSharedMembershipsFW();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    private MutableDirectBufferEx encodeBuffer = new UnsafeBufferEx(new byte[0]);

    String encode(
        List<MqttKafkaMembership> memberships)
    {
        final int capacity = capacity(memberships);

        if (encodeBuffer.capacity() < capacity)
        {
            encodeBuffer = new UnsafeBufferEx(new byte[capacity]);
        }

        final MqttSharedMembershipsFW encoded = membershipsRW.wrap(encodeBuffer, 0, encodeBuffer.capacity())
            .kind(MqttSharedMembershipsVersion.V1)
            .membershipsV1(v1 -> memberships.forEach(m -> v1.membershipsItem(i -> i
                .shareName(m.shareName)
                .filter(m.filter))))
            .build();

        final byte[] bytes = new byte[encoded.sizeof()];
        encodeBuffer.getBytes(encoded.offset(), bytes);

        return encoder.encodeToString(bytes);
    }

    List<MqttKafkaMembership> decode(
        String value)
    {
        final List<MqttKafkaMembership> memberships = new ArrayList<>();
        boolean decodable = true;

        if (value != null)
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(decoder.decode(value));
            final MqttSharedMembershipsVersion version = buffer.capacity() > 0
                ? MqttSharedMembershipsVersion.valueOf(buffer.getByte(0) & 0xFF)
                : null;
            final MqttSharedMembershipsFW decoded = version == MqttSharedMembershipsVersion.V1
                ? membershipsRO.tryWrap(buffer, 0, buffer.capacity())
                : null;

            decodable = decoded != null;

            if (decodable)
            {
                decoded.membershipsV1().memberships().forEach(m ->
                    memberships.add(new MqttKafkaMembership(m.shareName().asString(), m.filter().asString())));
            }
        }

        return decodable ? memberships : null;
    }

    private static int capacity(
        List<MqttKafkaMembership> memberships)
    {
        int capacity = HEADER_SIZE;

        for (int i = 0; i < memberships.size(); i++)
        {
            final MqttKafkaMembership membership = memberships.get(i);
            capacity += MEMBERSHIP_FIXED_SIZE +
                (membership.shareName.length() + membership.filter.length()) * MAXIMUM_UTF8_BYTES_PER_CHAR;
        }

        return capacity;
    }
}
