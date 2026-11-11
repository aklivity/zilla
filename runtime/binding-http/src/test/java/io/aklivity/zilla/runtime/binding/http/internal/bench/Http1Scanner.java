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
 * Finds the end of each HTTP/1.1 message (request or response) in a byte stream, honoring
 * {@code content-length} and chunked transfer coding, without allocating.
 */
final class Http1Scanner
{
    private static final int LINE_CAPACITY = 128;
    private static final byte[] CONTENT_LENGTH = BenchMessages.ascii("content-length:");
    private static final byte[] TRANSFER_ENCODING = BenchMessages.ascii("transfer-encoding:");
    private static final byte[] CHUNKED = BenchMessages.ascii("chunked");

    @FunctionalInterface
    private interface Step
    {
        int apply(
            DirectBufferEx buffer,
            int offset,
            int limit);
    }

    private final byte[] line = new byte[LINE_CAPACITY];
    private final Step scanHeaders = this::scanHeaders;
    private final Step scanContent = this::scanContent;
    private final Step scanChunkSize = this::scanChunkSize;
    private final Step scanChunkContent = this::scanChunkContent;
    private final Step scanChunkEnd = this::scanChunkEnd;
    private final Step scanTrailers = this::scanTrailers;

    private Step step = scanHeaders;
    private int lineLength;
    private long contentLength;
    private boolean chunked;
    private long remaining;
    private int chunkEndRemaining;
    private int messages;

    /**
     * @return the number of messages completed since the last call
     */
    int scan(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        while (progress < limit)
        {
            progress = step.apply(buffer, progress, limit);
        }

        final int completed = messages;
        messages = 0;
        return completed;
    }

    private int scanHeaders(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        while (progress < limit && step == scanHeaders)
        {
            final int octet = buffer.getByte(progress++) & 0xff;
            if (octet == '\n')
            {
                onHeaderLine();
            }
            else if (octet != '\r' && lineLength < LINE_CAPACITY)
            {
                line[lineLength++] = (byte) (octet >= 'A' && octet <= 'Z' ? octet + 32 : octet);
            }
        }
        return progress;
    }

    private void onHeaderLine()
    {
        if (lineLength == 0)
        {
            if (chunked)
            {
                remaining = 0L;
                step = scanChunkSize;
            }
            else if (contentLength > 0L)
            {
                remaining = contentLength;
                step = scanContent;
            }
            else
            {
                completeMessage();
            }
        }
        else if (startsWith(CONTENT_LENGTH))
        {
            long value = 0L;
            for (int index = CONTENT_LENGTH.length; index < lineLength; index++)
            {
                final int digit = line[index] - '0';
                if (digit >= 0 && digit <= 9)
                {
                    value = value * 10L + digit;
                }
            }
            contentLength = value;
        }
        else if (startsWith(TRANSFER_ENCODING))
        {
            chunked = endsWith(CHUNKED);
        }

        lineLength = 0;
    }

    private int scanContent(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int length = (int) Math.min(remaining, limit - offset);
        remaining -= length;

        if (remaining == 0L)
        {
            completeMessage();
        }

        return offset + length;
    }

    private int scanChunkSize(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        while (progress < limit && step == scanChunkSize)
        {
            final int octet = buffer.getByte(progress++) & 0xff;
            if (octet == '\n')
            {
                if (remaining == 0L)
                {
                    lineLength = 0;
                    step = scanTrailers;
                }
                else
                {
                    step = scanChunkContent;
                }
            }
            else
            {
                final int digit = Character.digit(octet, 16);
                if (digit >= 0)
                {
                    remaining = remaining * 16L + digit;
                }
            }
        }
        return progress;
    }

    private int scanChunkContent(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int length = (int) Math.min(remaining, limit - offset);
        remaining -= length;

        if (remaining == 0L)
        {
            chunkEndRemaining = 2;
            step = scanChunkEnd;
        }

        return offset + length;
    }

    private int scanChunkEnd(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int length = Math.min(chunkEndRemaining, limit - offset);
        chunkEndRemaining -= length;

        if (chunkEndRemaining == 0)
        {
            step = scanChunkSize;
        }

        return offset + length;
    }

    private int scanTrailers(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        while (progress < limit && step == scanTrailers)
        {
            final int octet = buffer.getByte(progress++) & 0xff;
            if (octet == '\n')
            {
                if (lineLength == 0)
                {
                    completeMessage();
                }
                lineLength = 0;
            }
            else if (octet != '\r')
            {
                lineLength++;
            }
        }
        return progress;
    }

    private void completeMessage()
    {
        messages++;
        contentLength = 0L;
        chunked = false;
        lineLength = 0;
        step = scanHeaders;
    }

    private boolean startsWith(
        byte[] prefix)
    {
        boolean matches = lineLength >= prefix.length;
        for (int index = 0; matches && index < prefix.length; index++)
        {
            matches = line[index] == prefix[index];
        }
        return matches;
    }

    private boolean endsWith(
        byte[] suffix)
    {
        boolean matches = lineLength >= suffix.length;
        for (int index = 0; matches && index < suffix.length; index++)
        {
            matches = line[lineLength - suffix.length + index] == suffix[index];
        }
        return matches;
    }
}
