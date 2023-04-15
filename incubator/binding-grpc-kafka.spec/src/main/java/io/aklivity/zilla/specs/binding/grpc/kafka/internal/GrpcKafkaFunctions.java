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

import static io.aklivity.zilla.specs.binding.grpc.kafka.internal.types.KafkaOffsetFW.Builder.DEFAULT_LATEST_OFFSET;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.specs.binding.grpc.kafka.internal.types.KafkaOffsetFW;

public final class GrpcKafkaFunctions
{
    @Function
    public static MessageIdBuilder messageId()
    {
        return new MessageIdBuilder();
    }

    public static final class MessageIdBuilder
    {
        private final Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> partitionsRW =
            new Array32FW.Builder<>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW());
        private final MutableDirectBuffer partitionBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private MessageIdBuilder()
        {
            partitionsRW.wrap(partitionBuffer, 0, partitionBuffer.capacity());
        }

        public MessageIdBuilder partition(
            int partitionId,
            long offset)
        {
            partition(partitionId, offset, DEFAULT_LATEST_OFFSET);
            return this;
        }

        public MessageIdBuilder partition(
            int partitionId,
            long offset,
            long latestOffset)
        {
            partition(partitionId, offset, latestOffset, latestOffset);
            return this;
        }

        public MessageIdBuilder partition(
            int partitionId,
            long offset,
            long stableOffset,
            long latestOffset)
        {
            partitionsRW.item(p -> p.partitionId(partitionId)
                .partitionOffset(offset)
                .stableOffset(stableOffset)
                .latestOffset(latestOffset));

            return this;
        }

        public byte[] build()
        {
            Array32FW<KafkaOffsetFW> partitions = partitionsRW.build();
            final byte[] array = new byte[partitions.sizeof()];
            partitions.buffer().getBytes(partitions.offset(), array);
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
