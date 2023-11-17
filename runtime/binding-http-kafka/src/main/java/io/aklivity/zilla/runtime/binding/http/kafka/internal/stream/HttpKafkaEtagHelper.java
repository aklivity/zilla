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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.stream;

import java.util.Base64;
import java.util.function.BiConsumer;
import java.util.function.ToLongFunction;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.codec.HttpKafkaEtagFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.codec.HttpKafkaEtagPartitionV1FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.codec.HttpKafkaEtagV1FW;

public final class HttpKafkaEtagHelper
{
    private final Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> progressRW =
        new Array32FW.Builder<>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                .wrap(new UnsafeBuffer(new byte[2048]), 0, 2048);

    private final HttpKafkaEtagFW.Builder etagRW =
            new HttpKafkaEtagFW.Builder().wrap(new UnsafeBuffer(new byte[256]), 0, 256);

    private final String16FW.Builder stringRW = new String16FW.Builder().wrap(new UnsafeBuffer(new byte[256]), 0, 256);

    private final HttpKafkaEtagFW etagRO = new HttpKafkaEtagFW();
    private final HttpKafkaEtagPartitionV1FW partitionV1RO = new HttpKafkaEtagPartitionV1FW();
    private final HttpKafkaEtagPartitionV1FW.Builder partitionV1RW = new HttpKafkaEtagPartitionV1FW.Builder();

    private final Base64.Encoder encoder64 = Base64.getUrlEncoder();
    private final Base64.Decoder decoder64 = Base64.getUrlDecoder();

    private final MutableDirectBuffer bufferRW = new UnsafeBuffer(0L, 0);
    private final byte[] base64RW = new byte[256];

    private final Int2ObjectCache<byte[]> byteArrays = new Int2ObjectCache<>(1, 16, i -> {});

    private final MutableInteger offset = new MutableInteger();
    private final MutableInteger count = new MutableInteger();

    public String16FW encode(
        final Array32FW<KafkaOffsetFW> progress)
    {
        return encode(progress, KafkaOffsetFW::partitionOffset);
    }

    public String16FW encodeLatest(
        final Array32FW<KafkaOffsetFW> progress)
    {
        return encode(progress, KafkaOffsetFW::latestOffset);
    }

    private String16FW encode(
        final Array32FW<KafkaOffsetFW> progress,
        ToLongFunction<KafkaOffsetFW> resolve)
    {
        etagRW.rewrap();
        HttpKafkaEtagFW etag = etagRW
                .v1(v1 -> v1.partitionCount(progress.fieldCount()))
                .build();

        count.value = 0;

        MutableDirectBuffer buffer = etagRW.buffer();
        offset.value = etag.limit();
        progress.forEach(p ->
        {
            if (p.partitionId() >= 0)
            {
                count.value++;
                offset.value = partitionV1RW.wrap(buffer, offset.value, buffer.capacity())
                        .partitionId(p.partitionId())
                        .partitionOffset(resolve.applyAsLong(p))
                        .build()
                        .limit();
            }
        });

        etagRW.v1(v1 -> v1.partitionCount(count.value))
              .build();

        final HttpKafkaEtagFW encodable = etag;

        String16FW encodedBuf = null;

        if (encodable != null)
        {
            final int encodableBytes = offset.value - encodable.offset();
            final byte[] encodableRaw = byteArrays.computeIfAbsent(encodableBytes, byte[]::new);
            buffer.getBytes(encodable.offset(), encodableRaw);

            final byte[] encodedBase64 = base64RW;
            final int encodedBytes = encoder64.encode(encodableRaw, encodedBase64);
            MutableDirectBuffer encodeBuf = bufferRW;
            encodeBuf.wrap(encodedBase64, 0, encodedBytes);
            encodedBuf = stringRW.set(encodeBuf, 0, encodeBuf.capacity()).build();
        }

        return encodedBuf;
    }

    public Array32FW<KafkaOffsetFW> decodeLive(
        final String16FW decodable)
    {
        return decode(decodable, this::decodeLiveOffset);
    }

    public Array32FW<KafkaOffsetFW> decodeHistorical(
        final String16FW decodable)
    {
        return decode(decodable, this::decodeHistoricalOffset);
    }

    public Array32FW<KafkaOffsetFW> decodeLatest(
        final String16FW decodable)
    {
        return decode(decodable, this::decodeLatestOffset);
    }

    private Array32FW<KafkaOffsetFW> decode(
        final String16FW decodable,
        BiConsumer<HttpKafkaEtagPartitionV1FW, KafkaOffsetFW.Builder> decodeOffset)
    {
        Array32FW<KafkaOffsetFW> progress = null;

        HttpKafkaEtagFW decoded = null;

        DirectBuffer decodeBuf = decodable != null ? decodable.value() : null;
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
            decoded = etagRO.tryWrap(decodedBuf, 0, decodedBuf.capacity());
        }

        if (decoded != null)
        {
            final HttpKafkaEtagFW etag = decoded;

            decode:
            switch (etag.kind())
            {
            case HttpKafkaEtagFW.KIND_V1:
                final HttpKafkaEtagV1FW etagV1 = etag.v1();
                int partitionCount = etagV1.partitionCount();
                DirectBuffer wrapBuffer = etagV1.buffer();
                int wrapOffset = etagV1.limit();

                final Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> progressV1RW = progressRW;
                progressV1RW.wrap(progressV1RW.buffer(), 0, progressV1RW.buffer().capacity());
                for (int i = 0; i < partitionCount; i++)
                {
                    HttpKafkaEtagPartitionV1FW partitionV1 =
                            partitionV1RO.tryWrap(wrapBuffer, wrapOffset, wrapBuffer.capacity());
                    if (partitionV1 == null)
                    {
                        break decode;
                    }
                    wrapOffset = partitionV1.limit();

                    if (partitionV1.partitionId() >= 0)
                    {
                        progressV1RW.item(item -> decodeOffset.accept(partitionV1, item));
                    }
                }
                progress = progressV1RW.build();
                break;
            }
        }

        return progress;
    }

    private void decodeLiveOffset(
        HttpKafkaEtagPartitionV1FW partitionV1,
        KafkaOffsetFW.Builder builder)
    {
        builder.partitionId(partitionV1.partitionId())
               .partitionOffset(partitionV1.partitionOffset())
               .stableOffset(KafkaOffsetType.LIVE.value())
               .latestOffset(KafkaOffsetType.LIVE.value());
    }

    private void decodeHistoricalOffset(
        HttpKafkaEtagPartitionV1FW partitionV1,
        KafkaOffsetFW.Builder builder)
    {
        builder.partitionId(partitionV1.partitionId())
               .partitionOffset(partitionV1.partitionOffset())
               .stableOffset(KafkaOffsetType.HISTORICAL.value())
               .latestOffset(KafkaOffsetType.HISTORICAL.value());
    }

    private void decodeLatestOffset(
        HttpKafkaEtagPartitionV1FW partitionV1,
        KafkaOffsetFW.Builder builder)
    {
        builder.partitionId(partitionV1.partitionId())
               .partitionOffset(0L)
               .stableOffset(partitionV1.partitionOffset())
               .latestOffset(partitionV1.partitionOffset());
    }
}
