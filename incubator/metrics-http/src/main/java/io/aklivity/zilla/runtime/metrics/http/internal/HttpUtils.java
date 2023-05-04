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
package io.aklivity.zilla.runtime.metrics.http.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.metrics.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.HttpBeginExFW;

final class HttpUtils
{
    public static final int INVALID_CONTENT_LENGTH = -1;

    private HttpUtils()
    {
    }

    public static long initialId(
        long streamId)
    {
        // reduce both initial and reply stream ids to the same initial id
        return streamId & ~0b01L;
    }

    public static long direction(
        long streamId)
    {
        // get stream direction (1: received; 0: sent)
        return streamId & 0b01L;
    }

    public static HttpHeaderFW findContentLength(
        BeginFW begin)
    {
        final OctetsFW extension = begin.extension();
        final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);
        final Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();
        final String8FW httpContentLength = new String8FW("content-length");
        return headers.matchFirst(header -> httpContentLength.equals(header.name()));
    }

    public static long parseContentLength(
        HttpHeaderFW contentLength)
    {
        if (isContentLengthValid(contentLength))
        {
            DirectBuffer buffer = contentLength.value().value();
            assert buffer != null;
            return buffer.parseLongAscii(0, buffer.capacity());
        }
        else
        {
            return INVALID_CONTENT_LENGTH;
        }
    }

    private static boolean isContentLengthValid(
        HttpHeaderFW contentLength)
    {
        return contentLength != null && contentLength.value() != null && contentLength.value().length() != -1;
    }
}
