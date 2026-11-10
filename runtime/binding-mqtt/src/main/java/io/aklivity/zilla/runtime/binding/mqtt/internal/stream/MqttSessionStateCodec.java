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

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttSessionStateV1FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class MqttSessionStateCodec
{
    private static final int SESSION_STATE_VERSION_1 = 1;

    private final MqttSessionStateFW sessionStateRO = new MqttSessionStateFW();
    private final MqttSessionStateV1FW sessionStateV1RO = new MqttSessionStateV1FW();
    private final MqttSessionStateFW.Builder sessionStateRW = new MqttSessionStateFW.Builder();
    private final MqttSessionStateV1FW.Builder sessionStateV1RW = new MqttSessionStateV1FW.Builder();

    private final MutableDirectBufferEx sessionStateBuffer;
    private final MutableDirectBufferEx sessionStateV1Buffer;

    private int sharedCount;

    @FunctionalInterface
    interface MqttSessionSubscriptionDecoder
    {
        void accept(
            int subscriptionId,
            int qos,
            int flags,
            int reasonCode,
            String pattern,
            String shareName);
    }

    MqttSessionStateCodec(
        int capacity)
    {
        this.sessionStateBuffer = new UnsafeBufferEx(new byte[capacity]);
        this.sessionStateV1Buffer = new UnsafeBufferEx(new byte[capacity]);
    }

    boolean decode(
        DirectBufferEx buffer,
        int offset,
        int limit,
        MqttSessionSubscriptionDecoder decoder)
    {
        final int version = offset < limit ? buffer.getByte(offset) & 0xff : 0;

        final boolean decoded;
        if (version == SESSION_STATE_VERSION_1)
        {
            final MqttSessionStateV1FW sessionState = sessionStateV1RO.tryWrap(buffer, offset, limit);
            decoded = sessionState != null;

            if (decoded)
            {
                sessionState.subscriptions().forEach(filter ->
                    decoder.accept((int) filter.subscriptionId(), filter.qos(), filter.flags(), filter.reasonCode(),
                        filter.pattern().asString(), null));
            }
        }
        else
        {
            final MqttSessionStateFW sessionState = sessionStateRO.tryWrap(buffer, offset, limit);
            decoded = sessionState != null;

            if (decoded)
            {
                sessionState.subscriptions().forEach(filter ->
                    decoder.accept((int) filter.subscriptionId(), filter.qos(), filter.flags(), filter.reasonCode(),
                        filter.pattern().asString(), filter.shareName().asString()));
            }
        }

        return decoded;
    }

    MqttSessionStateCodec begin()
    {
        sessionStateRW.wrap(sessionStateBuffer, 0, sessionStateBuffer.capacity());
        sessionStateV1RW.wrap(sessionStateV1Buffer, 0, sessionStateV1Buffer.capacity());
        sharedCount = 0;

        return this;
    }

    MqttSessionStateCodec subscription(
        int subscriptionId,
        int qos,
        int flags,
        int reasonCode,
        String pattern,
        String shareName)
    {
        sessionStateRW.subscriptionsItem(filter -> filter
            .subscriptionId(subscriptionId)
            .qos(qos)
            .flags(flags)
            .reasonCode(reasonCode)
            .pattern(pattern)
            .shareName(shareName));

        if (shareName == null)
        {
            sessionStateV1RW.subscriptionsItem(filter -> filter
                .subscriptionId(subscriptionId)
                .qos(qos)
                .flags(flags)
                .reasonCode(reasonCode)
                .pattern(pattern));
        }
        else
        {
            sharedCount++;
        }

        return this;
    }

    Flyweight build()
    {
        return sharedCount > 0 ? sessionStateRW.build() : sessionStateV1RW.build();
    }
}
