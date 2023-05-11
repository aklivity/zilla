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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.stream;

import java.util.Base64;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.GrpcKafkaMessageFieldFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.GrpcKafkaMessageFieldPartitionV1FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.GrpcKafkaMessageFieldV1FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.OctetsFW;

public final class GrpcKafkaIdHelper
{
    private final Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> progressRW =
        new Array32FW.Builder<>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                .wrap(new UnsafeBuffer(new byte[2048]), 0, 2048);

    private final GrpcKafkaMessageFieldFW.Builder messageFieldRW =
            new GrpcKafkaMessageFieldFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

    private final GrpcKafkaMessageFieldFW messageFieldRO = new GrpcKafkaMessageFieldFW();
    private final GrpcKafkaMessageFieldPartitionV1FW partitionV1RO = new GrpcKafkaMessageFieldPartitionV1FW();
    private final GrpcKafkaMessageFieldPartitionV1FW.Builder partitionV1RW = new GrpcKafkaMessageFieldPartitionV1FW.Builder();

    private final Base64.Decoder decoder64 = Base64.getUrlDecoder();

    private final MutableDirectBuffer bufferRW = new UnsafeBuffer(0L, 0);
    private final OctetsFW.Builder lastMessageIdRW = new OctetsFW.Builder()
        .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
    private final byte[] base64RW = new byte[256];

    private final Int2ObjectCache<byte[]> byteArrays = new Int2ObjectCache<>(1, 16, i -> {});

    private final Array32FW<KafkaOffsetFW> historical =
        new Array32FW.Builder<>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                .wrap(new UnsafeBuffer(new byte[36]), 0, 36)
                .item(o -> o.partitionId(-1).partitionOffset(KafkaOffsetType.HISTORICAL.value()))
                .build();
    private final MutableInteger offset = new MutableInteger();

    public OctetsFW encodeProgress(
        final Array32FW<KafkaOffsetFW> progress)
    {
        messageFieldRW.rewrap();
        GrpcKafkaMessageFieldFW messageField = messageFieldRW
            .v1(v1 -> v1.partitionCount(progress.fieldCount()))
            .build();

        MutableDirectBuffer buffer = messageFieldRW.buffer();
        offset.value = messageField.limit();
        progress.forEach(p ->
        {
            offset.value = partitionV1RW.wrap(buffer, offset.value, buffer.capacity())
                .partitionId(p.partitionId())
                .partitionOffset(p.partitionOffset())
                .build()
                .limit();
        });

        OctetsFW encoded = lastMessageIdRW.set(buffer, 0, offset.value).build();

        return encoded;
    }

    public Array32FW<KafkaOffsetFW> decode(
        final DirectBuffer progress64)
    {
        Array32FW<KafkaOffsetFW> progress = historical;

        GrpcKafkaMessageFieldFW decoded = null;

        DirectBuffer decodeBuf = progress64;
        decode:
        if (decodeBuf != null)
        {
            final byte[] encodedBase64 = byteArrays.computeIfAbsent(decodeBuf.capacity(), byte[]::new);
            decodeBuf.getBytes(0, encodedBase64, 0, decodeBuf.capacity());

            boolean padding = false;
            for (int i = 0; i < encodedBase64.length; i++)
            {
                int ch = encodedBase64[i] & 0xff;

                if (padding)
                {
                    if (ch != 61)
                    {
                        break decode;
                    }
                }
                else
                {
                    if (!(48 <= ch && ch <= 57) &&
                        !(65 <= ch && ch <= 90) &&
                        !(97 <= ch && ch <= 122) &&
                        !(ch == 45) &&
                        !(ch == 95) &&
                        !(ch == 61))
                    {
                        break decode;
                    }

                    padding = ch == 61;
                }
            }

            final byte[] decodedBase64 = base64RW;
            final int decodedBytes = decoder64.decode(encodedBase64, decodedBase64);

            DirectBuffer decodedBuf = bufferRW;
            decodedBuf.wrap(decodedBase64, 0, decodedBytes);
            decoded = messageFieldRO.tryWrap(decodedBuf, 0, decodedBuf.capacity());
        }

        if (decoded != null)
        {
            final GrpcKafkaMessageFieldFW messageField = decoded;

            decode:
            switch (messageField.kind())
            {
            case GrpcKafkaMessageFieldFW.KIND_V1:
                final GrpcKafkaMessageFieldV1FW messageFieldV1 = messageField.v1();
                int partitionCount = messageFieldV1.partitionCount();
                DirectBuffer wrapBuffer = messageFieldV1.buffer();
                int wrapOffset = messageFieldV1.limit();

                final Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> progressV1RW = progressRW;
                progressV1RW.wrap(progressV1RW.buffer(), 0, progressV1RW.buffer().capacity());
                for (int i = 0; i < partitionCount; i++)
                {
                    GrpcKafkaMessageFieldPartitionV1FW partitionV1 =
                            partitionV1RO.tryWrap(wrapBuffer, wrapOffset, wrapBuffer.capacity());
                    if (partitionV1 == null)
                    {
                        break decode;
                    }
                    wrapOffset = partitionV1.limit();
                    progressV1RW.item(item -> item.partitionId(partitionV1.partitionId())
                                                  .partitionOffset(partitionV1.partitionOffset()));
                }
                progressV1RW.item(o -> o.partitionId(-1)
                                        .partitionOffset(KafkaOffsetType.HISTORICAL.value()));
                progress = progressV1RW.build();
                break;
            }
        }

        return progress;
    }

}
