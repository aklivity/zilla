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
package io.aklivity.zilla.runtime.binding.http.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

final class HttpSseFraming
{
    static final int CAPABILITY_MASK = 1 << Capability.FRAMING.ordinal();

    private static final String8FW HEADER_NAME_CONTENT_TYPE = new String8FW("content-type");
    private static final byte[] CONTENT_TYPE_EVENT_STREAM = "text/event-stream".getBytes(UTF_8);

    static boolean isEventStream(
        HttpBeginExFW beginEx)
    {
        final HttpHeaderFW contentType = beginEx.headers().matchFirst(h -> HEADER_NAME_CONTENT_TYPE.equals(h.name()));

        return contentType != null && startsWith(contentType.value().value(), CONTENT_TYPE_EVENT_STREAM);
    }

    private static boolean startsWith(
        DirectBufferEx value,
        byte[] prefix)
    {
        final int length = value != null ? value.capacity() : 0;

        boolean starts = length >= prefix.length;

        for (int i = 0; starts && i < prefix.length; i++)
        {
            starts = value.getByte(i) == prefix[i];
        }

        return starts;
    }

    private HttpSseFraming()
    {
    }
}
