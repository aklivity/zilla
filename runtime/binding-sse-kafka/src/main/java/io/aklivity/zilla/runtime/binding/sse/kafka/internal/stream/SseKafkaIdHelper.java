/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.stream;

import java.util.Base64;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.codec.SseKafkaEventIdFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.codec.SseKafkaEventIdPartitionV1FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.codec.SseKafkaEventIdV1FW;

public final class SseKafkaIdHelper
{
    private static final String8FW NULL_KEY = new String8FW("null");

    private final Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> progressRW =
            new Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                .wrap(new UnsafeBuffer(new byte[2048]), 0, 2048);

    private final SseKafkaEventIdFW.Builder eventIdRW =
            new SseKafkaEventIdFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

    private final MutableDirectBuffer progressOnlyRW = new UnsafeBuffer(new byte[1024], 0, 1024);
    private final MutableDirectBuffer keyAndProgressRW = new UnsafeBuffer(new byte[1024], 0, 1024);

    private final String8FW.Builder stringRW = new String8FW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
    private final OctetsFW octetsRO = new OctetsFW();

    private final SseKafkaEventIdFW eventIdRO = new SseKafkaEventIdFW();
    private final SseKafkaEventIdPartitionV1FW partitionV1RO = new SseKafkaEventIdPartitionV1FW();
    private final SseKafkaEventIdPartitionV1FW.Builder partitionV1RW = new SseKafkaEventIdPartitionV1FW.Builder();

    private final Base64.Encoder encoder64 = Base64.getUrlEncoder();
    private final Base64.Decoder decoder64 = Base64.getUrlDecoder();

    private final MutableDirectBuffer bufferRW = new UnsafeBuffer(0L, 0);
    private final DirectBuffer bufferRO = new UnsafeBuffer(0L, 0);
    private final byte[] base64RW = new byte[256];

    private final Int2ObjectCache<byte[]> byteArrays = new Int2ObjectCache<>(1, 16, i -> {});

    private final Array32FW<KafkaOffsetFW> historical =
            new Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                .wrap(new UnsafeBuffer(new byte[36]), 0, 36)
                .item(o -> o.partitionId(-1).partitionOffset(KafkaOffsetType.HISTORICAL.value()))
                .build();

    private final MutableInteger offset = new MutableInteger();

    public String8FW encodeKeyAndProgress(
        final OctetsFW key,
        final Array32FW<KafkaOffsetFW> progress,
        final KafkaHeaderFW etag)
    {
        final MutableDirectBuffer encodedBuf = keyAndProgressRW;
        int encodedOffset = 0;
        encodedBuf.putByte(encodedOffset++, (byte) '[');
        if (key != null)
        {
            final OctetsFW encodedKey = encodeKey(key);
            encodedBuf.putByte(encodedOffset++, (byte) '"');
            encodedBuf.putBytes(encodedOffset, encodedKey.value(), 0, encodedKey.sizeof());
            encodedOffset += encodedKey.sizeof();
            encodedBuf.putByte(encodedOffset++, (byte) '"');
        }
        else
        {
            encodedBuf.putBytes(encodedOffset, NULL_KEY.value(), 0, NULL_KEY.length());
            encodedOffset += NULL_KEY.length();
        }
        encodedBuf.putByte(encodedOffset++, (byte) ',');
        encodedBuf.putByte(encodedOffset++, (byte) '"');

        String8FW encodedProgress = encodeProgressOnly(progress, etag);
        encodedBuf.putBytes(encodedOffset, encodedProgress.value(), 0, encodedProgress.length());
        encodedOffset += encodedProgress.length();

        encodedBuf.putByte(encodedOffset++, (byte) '"');
        encodedBuf.putByte(encodedOffset++, (byte) ']');

        return stringRW.set(encodedBuf, 0, encodedOffset).build();
    }

    public String8FW encodeProgressOnly(
        final Array32FW<KafkaOffsetFW> progress,
        final KafkaHeaderFW etag)
    {
        eventIdRW.rewrap();
        SseKafkaEventIdFW eventId = eventIdRW
                .v1(v1 -> v1.partitionCount(progress.fieldCount()))
                .build();

        MutableDirectBuffer buffer = eventIdRW.buffer();
        offset.value = eventId.limit();
        progress.forEach(p ->
        {
            offset.value = partitionV1RW.wrap(buffer, offset.value, buffer.capacity())
                    .partitionId(p.partitionId())
                    .partitionOffset(p.partitionOffset())
                    .build()
                    .limit();
        });

        String8FW encoded = encode8(eventId);

        if (etag != null)
        {
            final DirectBuffer encodedValue = encoded.value();
            final DirectBuffer etagValue = etag.value().value();
            final int encodedValueLength = encodedValue.capacity();
            final int etagValueLength = etagValue.capacity();
            final int encodedLength = encodedValueLength + 1 + etagValueLength;

            final MutableDirectBuffer encodedBuf = progressOnlyRW;
            int encodedOffset = 0;
            encodedBuf.putBytes(encodedOffset, encodedValue, 0, encodedValueLength);
            encodedOffset += encodedValueLength;
            encodedBuf.putByte(encodedOffset++, (byte) '/');
            encodedBuf.putBytes(encodedOffset, etagValue, 0, etagValueLength);
            encodedOffset += etagValueLength;
            assert encodedOffset == encodedLength;

            encoded = stringRW.set(encodedBuf, 0, encodedLength).build();
        }

        return encoded;
    }

    public OctetsFW encodeKey(
        final Flyweight encodable)
    {
        offset.value = encodable.limit();
        final String8FW encoded = encode8(encodable);
        return encoded != null ? octetsRO.wrap(encoded.value(), 0, encoded.length()) : null;
    }

    private String8FW encode8(
        final Flyweight encodable)
    {
        String8FW encodedBuf = null;

        if (encodable != null)
        {
            final DirectBuffer buffer = encodable.buffer();
            final int index = encodable.offset();
            final int length = offset.value - index;
            encodedBuf = encode8(buffer, index, length);
        }

        return encodedBuf;
    }

    private String8FW encode8(
        final DirectBuffer buffer,
        final int index,
        final int length)
    {
        final byte[] encodableRaw = byteArrays.computeIfAbsent(length, byte[]::new);
        buffer.getBytes(index, encodableRaw);

        final byte[] encodedBase64 = base64RW;
        final int encodedBytes = encoder64.encode(encodableRaw, encodedBase64);
        MutableDirectBuffer encodeBuf = bufferRW;
        encodeBuf.wrap(encodedBase64, 0, encodedBytes);
        return stringRW.set(encodeBuf, 0, encodeBuf.capacity()).build();
    }

    public DirectBuffer findProgress(
        String8FW lastId)
    {
        // extract from progress64, progress64/etag, ["key64","progress64"], or ["key64","progress64/etag"]
        DirectBuffer progress64 = null;

        final DirectBuffer id = lastId != null ? lastId.value() : null;
        if (id != null)
        {
            int progressAt = 0;
            int progressEnd = id.capacity();
            int commaAt = indexOfByte(id, progressAt, progressEnd, (byte) ',');
            if (commaAt != -1)
            {
                int openQuoteAt = indexOfByte(id, commaAt, progressEnd, (byte) '"');
                if (openQuoteAt != -1)
                {
                    progressAt = openQuoteAt + 1;
                    int closeQuoteAt = indexOfByte(id, progressAt, progressEnd, (byte) '"');
                    if (closeQuoteAt != -1)
                    {
                        progressEnd = closeQuoteAt - 1;
                    }
                }
            }

            int slashAt = indexOfByte(id, progressAt, progressEnd, (byte) '/');
            if (slashAt != -1)
            {
                progressEnd = slashAt;
            }

            bufferRO.wrap(id, progressAt, progressEnd - progressAt);
            progress64 = bufferRO;
        }

        return progress64;
    }

    public Array32FW<KafkaOffsetFW> decode(
        final DirectBuffer progress64)
    {
        Array32FW<KafkaOffsetFW> progress = historical;

        SseKafkaEventIdFW decoded = null;

        DirectBuffer decodeBuf = progress64;
        decode:
        if (decodeBuf != null)
        {
            final byte[] encodedBase64 = byteArrays.computeIfAbsent(decodeBuf.capacity(), byte[]::new);
            decodeBuf.getBytes(0, encodedBase64, 0, decodeBuf.capacity());

            if ((encodedBase64.length & 0x03) != 0x00)
            {
                break decode;
            }

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
            decoded = eventIdRO.tryWrap(decodedBuf, 0, decodedBuf.capacity());
        }

        if (decoded != null)
        {
            final SseKafkaEventIdFW eventId = decoded;

            decode:
            switch (eventId.kind())
            {
            case SseKafkaEventIdFW.KIND_V1:
                final SseKafkaEventIdV1FW eventIdV1 = eventId.v1();
                int partitionCount = eventIdV1.partitionCount();
                DirectBuffer wrapBuffer = eventIdV1.buffer();
                int wrapOffset = eventIdV1.limit();

                final Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> progressV1RW = progressRW;
                progressV1RW.wrap(progressV1RW.buffer(), 0, progressV1RW.buffer().capacity());
                for (int i = 0; i < partitionCount; i++)
                {
                    SseKafkaEventIdPartitionV1FW partitionV1 =
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

    Array32FW<KafkaOffsetFW> historical()
    {
        return historical;
    }

    private static int indexOfByte(
        DirectBuffer buffer,
        int offset,
        int limit,
        byte value)
    {
        for (int cursor = offset; cursor < limit; cursor++)
        {
            if (buffer.getByte(cursor) == value)
            {
                return cursor;
            }
        }

        return -1;
    }
}
