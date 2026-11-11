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

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.util.Random;

import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;

final class BenchMessages
{
    static final int SMALL_LENGTH = 400;
    static final int LARGE_LENGTH = 10_000;
    static final int STREAMING_FRAGMENT_LENGTH = 256;
    static final String AUTHORITY = "localhost:8080";
    static final String CONTENT_TYPE = "application/octet-stream";

    private BenchMessages()
    {
    }

    static int bodyLength(
        String size)
    {
        final int length;

        switch (size)
        {
        case "none":
            length = 0;
            break;
        case "small":
            length = SMALL_LENGTH;
            break;
        case "10k":
            length = LARGE_LENGTH;
            break;
        default:
            throw new IllegalArgumentException(size);
        }

        return length;
    }

    static byte[] body(
        int length)
    {
        final byte[] random = new byte[length];
        new Random(0L).nextBytes(random);

        final byte[] body = new byte[length];
        for (int index = 0; index < length; index++)
        {
            body[index] = (byte) ('a' + (random[index] & 0x0f));
        }

        return body;
    }

    static UnsafeBufferEx requestBeginEx(
        int httpTypeId,
        int headerCount,
        int bodyLength)
    {
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[2048]);

        final HttpBeginExFW.Builder builder = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name(":method").value(bodyLength != 0 ? "POST" : "GET"))
            .headersItem(h -> h.name(":scheme").value("http"))
            .headersItem(h -> h.name(":authority").value(AUTHORITY))
            .headersItem(h -> h.name(":path").value("/"));

        if (bodyLength != 0)
        {
            builder.headersItem(h -> h.name("content-type").value(CONTENT_TYPE))
                   .headersItem(h -> h.name("content-length").value(Integer.toString(bodyLength)));
        }

        for (int index = 0; index < headerCount; index++)
        {
            final int header = index;
            builder.headersItem(h -> h.name(headerName(header)).value(headerValue(header)));
        }

        return encoded(builder.build());
    }

    static Response response(
        int httpTypeId,
        int headerCount,
        int bodyLength,
        boolean streaming)
    {
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[2048]);

        final HttpBeginExFW.Builder builder = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name(":status").value("200"))
            .headersItem(h -> h.name("content-type").value(CONTENT_TYPE));

        if (bodyLength == 0 || !streaming)
        {
            builder.headersItem(h -> h.name("content-length").value(Integer.toString(bodyLength)));
        }

        for (int index = 0; index < headerCount; index++)
        {
            final int header = index;
            builder.headersItem(h -> h.name(headerName(header)).value(headerValue(header)));
        }

        final byte[] beginEx = new byte[builder.build().sizeof()];
        buffer.getBytes(0, beginEx);

        return new Response(
            beginEx,
            bodyLength != 0 ? new byte[][] { new byte[0] } : new byte[0][],
            bodyLength != 0 ? new byte[][] { body(bodyLength) } : new byte[0][],
            new byte[0],
            streaming ? STREAMING_FRAGMENT_LENGTH : 0);
    }

    static String headerName(
        int index)
    {
        return String.format("x-bench-%02d", index);
    }

    static String headerValue(
        int index)
    {
        return String.format("value-%02d-abcdefghijkl", index);
    }

    private static UnsafeBufferEx encoded(
        HttpBeginExFW beginEx)
    {
        final byte[] encoded = new byte[beginEx.sizeof()];
        beginEx.buffer().getBytes(beginEx.offset(), encoded);

        return new UnsafeBufferEx(encoded);
    }

    static byte[] ascii(
        String text)
    {
        return text.getBytes(US_ASCII);
    }
}
