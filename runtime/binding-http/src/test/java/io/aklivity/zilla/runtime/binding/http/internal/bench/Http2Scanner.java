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
package io.aklivity.zilla.runtime.binding.http.internal.bench;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Splits an HTTP/2 byte stream into frames and reports each complete frame, without allocating.
 */
final class Http2Scanner
{
    @FunctionalInterface
    interface FrameListener
    {
        void onFrame(
            int type,
            int flags,
            int streamId,
            int length,
            int firstWord);
    }

    @FunctionalInterface
    private interface Step
    {
        int apply(
            DirectBufferEx buffer,
            int offset,
            int limit);
    }

    private static final int PREFACE_LENGTH = 24;
    private static final int FRAME_HEADER_LENGTH = 9;

    private final FrameListener listener;
    private final Step scanPreface = this::scanPreface;
    private final Step scanFrameHeader = this::scanFrameHeader;
    private final Step scanFramePayload = this::scanFramePayload;

    private Step step;
    private int prefaceRemaining = PREFACE_LENGTH;
    private int headerLength;
    private int type;
    private int flags;
    private int streamId;
    private int length;
    private int payloadRemaining;
    private int firstWord;
    private int firstWordLength;

    Http2Scanner(
        FrameListener listener,
        boolean preface)
    {
        this.listener = listener;
        this.step = preface ? scanPreface : scanFrameHeader;
    }

    void scan(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        while (progress < limit)
        {
            progress = step.apply(buffer, progress, limit);
        }
    }

    private int scanPreface(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int skipped = Math.min(prefaceRemaining, limit - offset);
        prefaceRemaining -= skipped;

        if (prefaceRemaining == 0)
        {
            step = scanFrameHeader;
        }

        return offset + skipped;
    }

    private int scanFrameHeader(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        while (progress < limit && headerLength < FRAME_HEADER_LENGTH)
        {
            final int octet = buffer.getByte(progress++) & 0xff;
            switch (headerLength++)
            {
            case 0:
            case 1:
            case 2:
                length = headerLength == 1 ? octet : (length << 8) | octet;
                break;
            case 3:
                type = octet;
                break;
            case 4:
                flags = octet;
                break;
            case 5:
                streamId = octet & 0x7f;
                break;
            default:
                streamId = (streamId << 8) | octet;
                break;
            }
        }

        if (headerLength == FRAME_HEADER_LENGTH)
        {
            headerLength = 0;
            payloadRemaining = length;
            firstWord = 0;
            firstWordLength = 0;

            if (length == 0)
            {
                listener.onFrame(type, flags, streamId, length, firstWord);
            }
            else
            {
                step = scanFramePayload;
            }
        }

        return progress;
    }

    private int scanFramePayload(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int available = Math.min(payloadRemaining, limit - offset);

        for (int index = 0; firstWordLength < Integer.BYTES && index < available; index++)
        {
            firstWord = (firstWord << 8) | (buffer.getByte(offset + index) & 0xff);
            firstWordLength++;
        }

        payloadRemaining -= available;

        if (payloadRemaining == 0)
        {
            listener.onFrame(type, flags, streamId, length, firstWord);
            step = scanFrameHeader;
        }

        return offset + available;
    }
}
