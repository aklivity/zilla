/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.grpc.kafka.internal;


import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.grpc.kafka.internal.types.GrpcKafkaMessageFieldFW;
import io.aklivity.zilla.specs.binding.grpc.kafka.internal.types.GrpcKafkaMessageFieldPartitionV1FW;

public final class GrpcKafkaFunctions
{
    @Function
    public static MessageIdBuilder messageId()
    {
        return new MessageIdBuilder();
    }

    public static final class MessageIdBuilder
    {
        private final GrpcKafkaMessageFieldFW.Builder messageFieldRW = new GrpcKafkaMessageFieldFW.Builder();
        private final GrpcKafkaMessageFieldPartitionV1FW.Builder partitionV1RW =
            new GrpcKafkaMessageFieldPartitionV1FW.Builder();
        private final MutableDirectBuffer progressBuffer = new UnsafeBuffer(new byte[1024 * 8]);
        private int progressOffset;

        private MessageIdBuilder()
        {
            messageFieldRW.wrap(progressBuffer, 0, progressBuffer.capacity());
        }

        public MessageIdBuilder partitionCount(
            int fieldCount)
        {
            GrpcKafkaMessageFieldFW messageField =
                messageFieldRW.v1(v1 -> v1.partitionCount(fieldCount)).build();
            progressOffset = messageField.limit();
            return this;
        }

        public MessageIdBuilder partition(
            int partitionId,
            long offset)
        {
            MutableDirectBuffer buffer = messageFieldRW.buffer();
            progressOffset = partitionV1RW.wrap(buffer, progressOffset, buffer.capacity())
                .partitionId(partitionId)
                .partitionOffset(offset)
                .build()
                .limit();
            return this;
        }

        public byte[] build()
        {
            final byte[] array = new byte[progressOffset];
            messageFieldRW.buffer().getBytes(0, array);
            return array;
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(GrpcKafkaFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "grpc_kafka";
        }
    }

    private GrpcKafkaFunctions()
    {
        // utility
    }
}
