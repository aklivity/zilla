/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.amqp.internal.util;

import io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpCapabilities;
import io.aklivity.zilla.runtime.cog.amqp.internal.types.codec.AmqpReceiverSettleMode;
import io.aklivity.zilla.runtime.cog.amqp.internal.types.codec.AmqpRole;
import io.aklivity.zilla.runtime.cog.amqp.internal.types.codec.AmqpSenderSettleMode;

public final class AmqpTypeUtil
{
    public static AmqpCapabilities amqpCapabilities(
        AmqpRole role)
    {
        switch (role)
        {
        case RECEIVER:
            return AmqpCapabilities.RECEIVE_ONLY;
        case SENDER:
            return AmqpCapabilities.SEND_ONLY;
        default:
            throw new IllegalArgumentException("Illegal role: " + role);
        }
    }

    public static io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpSenderSettleMode amqpSenderSettleMode(
        AmqpSenderSettleMode senderSettleMode)
    {
        switch (senderSettleMode)
        {
        case UNSETTLED:
            return io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpSenderSettleMode.UNSETTLED;
        case SETTLED:
            return io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpSenderSettleMode.SETTLED;
        case MIXED:
            return io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpSenderSettleMode.MIXED;
        default:
            throw new IllegalArgumentException("Illegal senderSettleMode: " + senderSettleMode);
        }
    }

    public static io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpReceiverSettleMode amqpReceiverSettleMode(
        AmqpReceiverSettleMode receiverSettleMode)
    {
        switch (receiverSettleMode)
        {
        case FIRST:
            return io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpReceiverSettleMode.FIRST;
        case SECOND:
            return io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpReceiverSettleMode.SECOND;
        default:
            throw new IllegalArgumentException("Illegal receiverSettleMode: " + receiverSettleMode);
        }
    }

    public static AmqpRole amqpRole(
        AmqpCapabilities role)
    {
        switch (role)
        {
        case RECEIVE_ONLY:
            return AmqpRole.SENDER;
        case SEND_ONLY:
            return AmqpRole.RECEIVER;
        default:
            throw new IllegalArgumentException("Illegal role: " + role);
        }
    }

    public static AmqpSenderSettleMode amqpSenderSettleMode(
        io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpSenderSettleMode senderSettleMode)
    {
        switch (senderSettleMode)
        {
        case UNSETTLED:
            return AmqpSenderSettleMode.UNSETTLED;
        case SETTLED:
            return AmqpSenderSettleMode.SETTLED;
        case MIXED:
            return AmqpSenderSettleMode.MIXED;
        default:
            throw new IllegalArgumentException("Illegal senderSettleMode: " + senderSettleMode);
        }
    }

    public static AmqpReceiverSettleMode amqpReceiverSettleMode(
        io.aklivity.zilla.runtime.cog.amqp.internal.types.AmqpReceiverSettleMode receiverSettleMode)
    {
        switch (receiverSettleMode)
        {
        case FIRST:
            return AmqpReceiverSettleMode.FIRST;
        case SECOND:
            return AmqpReceiverSettleMode.SECOND;
        default:
            throw new IllegalArgumentException("Illegal receiverSettleMode: " + receiverSettleMode);
        }
    }

    private AmqpTypeUtil()
    {
        // utility class, no instances
    }
}
